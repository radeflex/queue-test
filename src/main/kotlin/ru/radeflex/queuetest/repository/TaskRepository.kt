package ru.radeflex.queuetest.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import java.time.Instant

interface TaskRepository : JpaRepository<Task, Int>, JpaSpecificationExecutor<Task> {
    @Modifying
    @Query("""
        UPDATE Task 
        SET status = CASE
            WHEN retries < maxRetries THEN 'RETRY'
            ELSE 'FAILED'
        END
        WHERE status = 'PROCESSING' OR status = 'QUEUED'
        AND :threshold > createdAt
""")
    fun refreshTasks(threshold: Instant)
    fun findByStatusIn(col: Collection<TaskStatus>): List<Task>

    @Modifying
    @Query("""
        UPDATE Task 
        SET status = 'PROCESSING'
        WHERE id = :id AND status = 'QUEUED'
""")
    fun claim(id: Int): Int

    @Modifying
    @Query("""
        UPDATE Task
        SET status = 'CANCELLED'
        WHERE id = :id AND status != 'PROCESSING'
    """)
    fun cancel(id: Int): Int
}