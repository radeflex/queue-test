package ru.radeflex.queuetest.dto

import ru.radeflex.queuetest.entity.TaskStatus
import java.time.Instant

data class TaskFilter(
    val filePath: String? = null,
    val status: TaskStatus? = null,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
)
