package ru.radeflex.queuetest.entity

enum class TaskStatus {
    NEW, QUEUED, PROCESSING, DONE, FAILED, CANCELLED, RETRY
}