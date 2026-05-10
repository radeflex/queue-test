package ru.radeflex.queuetest.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import ru.radeflex.queuetest.mapper.TaskMapper
import ru.radeflex.queuetest.repository.TaskRepository
import java.time.Duration
import java.time.Instant

@Service
class TaskTransactionService(
    @Value($$"${queue.idle-timeout}")
    private val idleTimeout: Duration,
    private val taskRepository: TaskRepository,
    private val taskMapper: TaskMapper,
) {
    @Transactional
    fun refreshTasks() {
        val thres = Instant.now().minus(idleTimeout)
        taskRepository.refreshTasks(thres)
    }

    @Transactional
    fun saveTask(file: MultipartFile): Task {
        return taskRepository.save(taskMapper.map(file))
    }

    @Transactional
    fun markTask(task: Task, status: TaskStatus) {
        task.status = status
        taskRepository.save(task)
    }

    @Transactional
    fun claimTask(taskId: Int): Task? {
        val updated = taskRepository.claim(taskId)
        if (updated == 0) return null
        return taskRepository.findById(taskId).get()
    }

    @Transactional
    fun cancel(taskId: Int): Boolean {
        return taskRepository.cancel(taskId) > 0
    }
}