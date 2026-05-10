package ru.radeflex.queuetest.worker

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ru.radeflex.queuetest.service.TaskService

@Component
class TaskWorkerPool(
    private val taskService: TaskService,
    @Value($$"${queue.pool-size}")
    private val poolSize: Int,
    private val queue: Channel<Int>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
)
{
    @PostConstruct
    fun start() {
        taskService.recover()
        repeat(poolSize) {
            scope.launch {
                for (taskId in queue) {
                    println("Processing task $taskId")
                    taskService.process(taskId)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        scope.cancel()
    }
}