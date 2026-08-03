package com.bubble.rikkahub.util

import com.bubble.rikkahub.data.remote.dto.SseFrame

object SseLineParser {

    /**
     * Parses a list of lines from an SSE stream into an SseFrame.
     * An empty frame (all blank lines) returns null.
     *
     * SSE frames are delimited by double-newline. Each line is "field: value".
     * Supports: event, data, id, retry fields.
     */
    fun parse(lines: List<String>): SseFrame? {
        if (lines.all { it.isBlank() }) return null

        var event: String? = null
        var data: String? = null

        for (line in lines) {
            when {
                line.startsWith("event:", ignoreCase = true) ->
                    event = line.substringAfter(":").trim()
                line.startsWith("data:", ignoreCase = true) -> {
                    val value = line.substringAfter(":").trim()
                    data = if (data == null) value else data + "\n" + value
                }
            }
        }

        if (event == null && data == null) return null
        return SseFrame(event = event, data = data)
    }
}
