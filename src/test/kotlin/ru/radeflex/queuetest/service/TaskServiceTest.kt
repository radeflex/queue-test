package ru.radeflex.queuetest.service

import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.dto.TaskFilter
import ru.radeflex.queuetest.dto.TaskReadDto
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import ru.radeflex.queuetest.mapper.TaskMapper
import ru.radeflex.queuetest.repository.TaskRepository
import java.util.*

class TaskServiceTest {
    @MockK lateinit var taskMapper: TaskMapper
    @MockK lateinit var taskRepository: TaskRepository
    @MockK lateinit var taskTransactionService: TaskTransactionService

    private lateinit var queue: Channel<Int>
    private lateinit var taskService: TaskService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        queue = Channel(capacity = 100)
        taskService = TaskService(taskMapper, taskRepository, taskTransactionService, queue)
    }

    @Nested
    inner class Create {
        @Test
        fun `create - queue принял задачу - возвращает dto без маркировки RETRY`() = runTest {
            val file = mockk<MultipartFile>()
            val task = Task(id = 1, status = TaskStatus.NEW)
            val readDto = mockk<TaskReadDto>()

            every { taskTransactionService.saveTask(file) } returns task
            every { taskTransactionService.markTask(task, TaskStatus.QUEUED) } just Runs // ← добавить
            every { taskMapper.map(task) } returns readDto

            val result = taskService.create(file)

            assertEquals(readDto, result)
            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.QUEUED) }
            verify(exactly = 0) { taskTransactionService.markTask(any(), TaskStatus.RETRY) }
        }

        @Test
        fun `create - queue полон - задача помечается RETRY`() = runTest {
            queue = Channel(capacity = Channel.RENDEZVOUS)
            taskService = TaskService(taskMapper, taskRepository, taskTransactionService, queue)

            val file = mockk<MultipartFile>()
            val task = Task(id = 1, status = TaskStatus.NEW)
            val readDto = mockk<TaskReadDto>()

            every { taskTransactionService.saveTask(file) } returns task
            every { taskTransactionService.markTask(task, TaskStatus.QUEUED) } just Runs // ← сначала QUEUED
            every { taskTransactionService.markTask(task, TaskStatus.RETRY) } just Runs  // ← потом откат
            every { taskMapper.map(task) } returns readDto

            val result = taskService.create(file)

            assertEquals(readDto, result)
            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.QUEUED) }
            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.RETRY) }
        }

        @Test
        fun `create - задача без id - queue не вызывается`() = runTest {
            val file = mockk<MultipartFile>()
            val task = Task(id = null, status = TaskStatus.NEW)
            val readDto = mockk<TaskReadDto>()

            every { taskTransactionService.saveTask(file) } returns task
            every { taskMapper.map(task) } returns readDto

            taskService.create(file)

            assertTrue(queue.isEmpty)
            verify(exactly = 0) { taskTransactionService.markTask(any(), any()) }
        }
    }

    @Nested
    inner class Process {
        @Test
        fun `process - claim вернул null - задача пропускается`() = runTest {
            every { taskTransactionService.claimTask(1) } returns null

            taskService.process(1)

            verify(exactly = 0) { taskTransactionService.markTask(any(), any()) }
        }

        @Test
        fun `process - успешное выполнение - статус DONE`() = runTest {
            val task = Task(id = 1, status = TaskStatus.PROCESSING)

            every { taskTransactionService.claimTask(1) } returns task
            every { taskTransactionService.markTask(task, TaskStatus.DONE) } just Runs

            taskService.process(1)

            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.DONE) }
        }
    }

    @Nested
    inner class Recover {
        @Test
        fun `recover - канал принял задачи - все помечаются QUEUED`() {
            val task1 = Task(id = 1, status = TaskStatus.NEW)
            val task2 = Task(id = 2, status = TaskStatus.RETRY)

            every { taskTransactionService.refreshTasks() } just Runs
            every { taskRepository.findByStatusIn(listOf(TaskStatus.NEW, TaskStatus.RETRY)) } returns mutableListOf(task1, task2)
            every { taskTransactionService.markTask(any(), TaskStatus.QUEUED) } just Runs

            taskService.recover()

            verify(exactly = 1) { taskTransactionService.markTask(task1, TaskStatus.QUEUED) }
            verify(exactly = 1) { taskTransactionService.markTask(task2, TaskStatus.QUEUED) }
            verify(exactly = 0) { taskTransactionService.markTask(any(), TaskStatus.RETRY) }
            assertFalse(queue.isEmpty)
        }

        @Test
        fun `recover - канал полон - задача с исчерпанными попытками помечается FAILED`() {
            queue = Channel(capacity = Channel.RENDEZVOUS)
            taskService = TaskService(taskMapper, taskRepository, taskTransactionService, queue)

            val task = Task(id = 1, status = TaskStatus.NEW, retries = 2, maxRetries = 3)

            every { taskTransactionService.refreshTasks() } just Runs
            every { taskRepository.findByStatusIn(listOf(TaskStatus.NEW, TaskStatus.RETRY)) } returns mutableListOf(task)
            every { taskTransactionService.markTask(task, TaskStatus.QUEUED) } just Runs // ← добавить
            every { taskTransactionService.markTask(task, TaskStatus.FAILED) } just Runs

            taskService.recover()

            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.QUEUED) }
            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.FAILED) }
        }

        @Test
        fun `recover - канал полон - задача с оставшимися попытками помечается RETRY`() {
            queue = Channel(capacity = Channel.RENDEZVOUS)
            taskService = TaskService(taskMapper, taskRepository, taskTransactionService, queue)

            val task = Task(id = 1, status = TaskStatus.NEW, retries = 0, maxRetries = 3)

            every { taskTransactionService.refreshTasks() } just Runs
            every { taskRepository.findByStatusIn(listOf(TaskStatus.NEW, TaskStatus.RETRY)) } returns mutableListOf(task)
            every { taskTransactionService.markTask(task, TaskStatus.QUEUED) } just Runs // ← добавить
            every { taskTransactionService.markTask(task, TaskStatus.RETRY) } just Runs

            taskService.recover()

            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.QUEUED) }
            verify(exactly = 1) { taskTransactionService.markTask(task, TaskStatus.RETRY) }
            verify(exactly = 0) { taskTransactionService.markTask(task, TaskStatus.FAILED) }
        }
    }

    @Nested
    inner class Cancel {
        @Test
        fun `cancel - задача найдена и отменена - возвращает true`() = runTest {
            every { taskTransactionService.cancel(1) } returns true

            assertTrue(taskService.cancel(1))
        }

        @Test
        fun `cancel - задача не найдена или уже в процессе - возвращает false`() = runTest {
            every { taskTransactionService.cancel(99) } returns false

            assertFalse(taskService.cancel(99))
        }
    }

    @Nested
    inner class FindById {
        @Test
        fun `findById - задача существует - возвращает dto`() {
            val task = Task(id = 1, status = TaskStatus.DONE)
            val readDto = mockk<TaskReadDto>()

            every { taskRepository.findById(1) } returns Optional.of(task)
            every { taskMapper.map(task) } returns readDto

            val result = taskService.findById(1)

            assertTrue(result.isPresent)
            assertEquals(readDto, result.get())
        }

        @Test
        fun `findById - задача не существует - возвращает empty`() {
            every { taskRepository.findById(99) } returns Optional.empty()

            val result = taskService.findById(99)

            assertTrue(result.isEmpty)
        }
    }

    @Nested
    inner class FindAll {

        @Test
        fun `findAll - возвращает страницу dto`() {
            val task = Task(id = 1, status = TaskStatus.DONE)
            val readDto = mockk<TaskReadDto>()
            val pageable = PageRequest.of(0, 10)
            val filter = TaskFilter()
            val page = PageImpl(listOf(task))

            every { taskRepository.findAll(any<Specification<Task>>(), pageable) } returns page
            every { taskMapper.map(task) } returns readDto

            val result = taskService.findAll(filter, pageable)

            assertEquals(1, result.totalElements)
            assertEquals(readDto, result.content[0])
        }
    }
}