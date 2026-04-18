package com.selfdox.rtspserver.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Log entry for in-memory ring buffer.
 * DECISION: Per D12 spec — max 200 entries, stored in StreamRepository.
 */
data class LogEntry(
    val id: Long = nextId(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    companion object {
        private val idCounter = AtomicLong(0)
        private fun nextId(): Long = idCounter.incrementAndGet()
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
