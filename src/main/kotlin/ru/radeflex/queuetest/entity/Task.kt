package ru.radeflex.queuetest.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
class Task(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    var filePath: String = "",

    @Enumerated(EnumType.STRING)
    var status: TaskStatus = TaskStatus.NEW,

    var createdAt: Instant = Instant.now(),
    var maxRetries: Int = 5,
    var retries: Int = 0,
)