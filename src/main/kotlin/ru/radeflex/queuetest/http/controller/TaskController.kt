package ru.radeflex.queuetest.http.controller

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import ru.radeflex.queuetest.dto.TaskFilter
import ru.radeflex.queuetest.dto.TaskReadDto
import ru.radeflex.queuetest.service.TaskService

@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun create(file: MultipartFile):
            ResponseEntity<TaskReadDto> {
        return ResponseEntity.ok(taskService.create(file))
    }

    @GetMapping
    fun findAll(filter: TaskFilter, pageable: Pageable):
            ResponseEntity<Page<TaskReadDto>> {
        return ResponseEntity.ok(taskService.findAll(filter, pageable))
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Int): ResponseEntity<TaskReadDto> {
        return taskService.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity(HttpStatus.NOT_FOUND) }
    }

    @DeleteMapping("/{id}")
    suspend fun cancel(@PathVariable id: Int): ResponseEntity<Void> {
        if (!taskService.cancel(id)) {
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
        return ResponseEntity.ok().build()
    }
}