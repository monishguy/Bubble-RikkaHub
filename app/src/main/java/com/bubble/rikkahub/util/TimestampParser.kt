package com.bubble.rikkahub.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Parses RikkaHub's `createdAt` strings ("2026-08-03T00:55:46.043660" or "...Z") to epoch millis. */
object TimestampParser {

    fun parse(createdAt: String?): Long {
        if (createdAt.isNullOrBlank()) return 0
        return try {
            if (createdAt.endsWith("Z", ignoreCase = true)) {
                Instant.parse(createdAt).toEpochMilli()
            } else {
                LocalDateTime.parse(createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (_: Exception) {
            0
        }
    }
}
