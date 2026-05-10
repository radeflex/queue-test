package ru.radeflex.queuetest.mapper

import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.dto.TaskReadDto
import ru.radeflex.queuetest.entity.Task
import java.util.*

@Component
class TaskMapper {
    fun map(file: MultipartFile): Task {
        val task = Task(null, file.originalFilename ?: UUID.randomUUID().toString())
        return task
    }

    fun map(task: Task): TaskReadDto {
        return TaskReadDto(
            task.id,
            task.filePath,
            task.status,
            task.createdAt.toString())
    }
}