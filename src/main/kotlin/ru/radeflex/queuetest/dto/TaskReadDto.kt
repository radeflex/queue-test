package ru.radeflex.queuetest.dto

import ru.radeflex.queuetest.entity.TaskStatus

data class TaskReadDto(
    val id: Int?,
    val filePath: String,
    val status: TaskStatus,
    val createdAt: String
) {
}