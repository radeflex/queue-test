package ru.radeflex.queuetest.specs

import org.springframework.data.jpa.domain.Specification
import ru.radeflex.queuetest.entity.Task
import ru.radeflex.queuetest.entity.TaskStatus
import java.time.Instant

object TaskSpecs {
    fun status(status: TaskStatus?) =
        Specification<Task> { root, _, cb ->
            status?.let {
                cb.equal(root.get<TaskStatus>("status"), it)
            }
        }
    fun filePath(filePath: String?) =
        Specification<Task> { root, _, cb ->
            filePath?.let {
                cb.like(root.get("filePath"), "%$it%")
            }
        }
    fun createdFrom(from: Instant?) =
        Specification<Task> { root, _, cb ->
            from?.let {
                cb.greaterThanOrEqualTo(root.get("createdAt"), it)
            }
        }
    fun createdTo(to: Instant?) =
        Specification<Task> { root, _, cb ->
            to?.let {
                cb.lessThanOrEqualTo(root.get("createdAt"), it)
            }
        }
}

