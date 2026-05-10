package ru.radeflex.queuetest.service

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.dto.TaskFilter
import ru.radeflex.queuetest.dto.TaskReadDto
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import ru.radeflex.queuetest.mapper.TaskMapper
import ru.radeflex.queuetest.repository.TaskRepository
import ru.radeflex.queuetest.specs.TaskSpecs
import java.util.*

@Service
class TaskService(
    private val taskMapper: TaskMapper,
    private val taskRepository: TaskRepository,
    private val taskTransactionService: TaskTransactionService,
    private val queue: Channel<Int>
) {
    suspend fun create(file: MultipartFile): TaskReadDto {
        val savedTask = taskTransactionService.saveTask(file) // NEW

        savedTask.id?.let { taskId ->
            taskTransactionService.markTask(savedTask, TaskStatus.QUEUED)
            val result = queue.trySend(taskId)
            if (result.isFailure) {
                // не вошла в очередь - RETRY (подбирается в scheduled)
                taskTransactionService.markTask(savedTask, TaskStatus.RETRY)
            }
        }
        return taskMapper.map(savedTask)
    }

    fun findAll(filter: TaskFilter, pageable: Pageable): Page<TaskReadDto> {
        return taskRepository.findAll(build(filter), pageable)
            .map { taskMapper.map(it) }
    }

    fun findById(id: Int): Optional<TaskReadDto> {
        return taskRepository.findById(id).map {
            taskMapper.map(it) }
    }

    private fun build(filter: TaskFilter): Specification<Task> {
        return Specification
            .where(TaskSpecs.status(filter.status))
            .and(TaskSpecs.filePath(filter.filePath))
            .and(TaskSpecs.createdFrom(filter.createdFrom))
            .and(TaskSpecs.createdTo(filter.createdTo))
    }

    suspend fun process(taskId: Int) {
        val task = taskTransactionService.claimTask(taskId) ?: return
        try {
            delay(10000)
            taskTransactionService.markTask(task, TaskStatus.DONE)
        } catch (e: Exception) {
            taskTransactionService.markTask(task, TaskStatus.FAILED)
        }
    }

    /*
    * Scheduled recovering
    * Назначение: собирает все RETRY (висящие) таски с БД
    * и пытается добавить их в очередь.
    * */
    @Scheduled(fixedDelay = 10000)
    fun recover() {
        // подхватываем висящие таски, согласно idleTimeout
        taskTransactionService.refreshTasks()
        val tasks = taskRepository.findByStatusIn(listOf(TaskStatus.NEW, TaskStatus.RETRY))
        tasks.forEach { task ->
            task.id?.let { taskId ->
                // публикация для воркера
                taskTransactionService.markTask(task, TaskStatus.QUEUED)
                val result = queue.trySend(taskId)
                if (result.isFailure) {
                    task.retries = task.retries.inc()
                    if (task.retries >= task.maxRetries)
                        taskTransactionService.markTask(task, TaskStatus.FAILED)
                    else taskTransactionService.markTask(task, TaskStatus.RETRY)
                }
            }
        }
    }

    suspend fun cancel(id: Int): Boolean {
        return taskTransactionService.cancel(id);
    }
}