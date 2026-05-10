package ru.radeflex.queuetest.config

import kotlinx.coroutines.channels.Channel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfig(
    @Value($$"${queue.capacity}")
    private val capacity: Int
) {
    @Bean
    fun queue(): Channel<Int> {
        return Channel(capacity)
    }
}