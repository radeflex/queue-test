package ru.radeflex.queuetest.service

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import ru.radeflex.queuetest.mapper.TaskMapper
import ru.radeflex.queuetest.repository.TaskRepository
import java.time.Duration
import java.util.*

class TaskTransactionServiceTest {

    @MockK lateinit var idleTimeout: Duration
    @MockK lateinit var taskRepository: TaskRepository
    @MockK lateinit var taskMapper: TaskMapper

    private lateinit var service: TaskTransactionService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TaskTransactionService(idleTimeout, taskRepository, taskMapper)
    }

    @Nested
    inner class SaveTask {
        @Test
        fun `saveTask - маппит file и сохраняет задачу`() {
            val file = mockk<MultipartFile>()
            val task = Task(id = 1, status = TaskStatus.QUEUED)

            every { taskMapper.map(file) } returns task
            every { taskRepository.save(task) } returns task

            val result = service.saveTask(file)

            assertEquals(task, result)
            verify(exactly = 1) { taskRepository.save(task) }
        }
    }

    @Nested
    inner class MarkTask {
        @Test
        fun `markTask - обновляет статус и сохраняет`() {
            val task = Task(id = 1, status = TaskStatus.QUEUED)

            every { taskRepository.save(task) } returns task

            service.markTask(task, TaskStatus.DONE)

            assertEquals(TaskStatus.DONE, task.status)
            verify(exactly = 1) { taskRepository.save(task) }
        }

        @Test
        fun `markTask - вызывается с разными статусами`() {
            val task = Task(id = 1, status = TaskStatus.QUEUED)

            every { taskRepository.save(task) } returns task

            TaskStatus.entries.forEach { status ->
                service.markTask(task, status)
                assertEquals(status, task.status)
            }

            verify(exactly = TaskStatus.entries.size) { taskRepository.save(task) }
        }
    }

    @Nested
    inner class ClaimTask {
        @Test
        fun `claimTask - claim вернул 1 - возвращает задачу`() {
            val task = Task(id = 1, status = TaskStatus.PROCESSING)

            every { taskRepository.claim(1) } returns 1
            every { taskRepository.findById(1) } returns Optional.of(task)

            val result = service.claimTask(1)

            assertNotNull(result)
            assertEquals(task, result)
        }

        @Test
        fun `claimTask - claim вернул 0 - задача уже захвачена - возвращает null`() {
            every { taskRepository.claim(1) } returns 0

            val result = service.claimTask(1)

            assertNull(result)
            verify(exactly = 0) { taskRepository.findById(any()) }
        }
    }

    @Nested
    inner class Cancel {
        @Test
        fun `cancel - задача отменена - возвращает true`() {
            every { taskRepository.cancel(1) } returns 1

            assertTrue(service.cancel(1))
        }

        @Test
        fun `cancel - задача не найдена или уже в процессе - возвращает false`() {
            every { taskRepository.cancel(99) } returns 0

            assertFalse(service.cancel(99))
        }
    }
}