package com.bubble.rikkahub.util

import java.util.regex.Pattern

object MessageSplitter {

    /**
     * Splits a message into individual bubble texts based on configured delimiters.
     *
     * Pattern: startDelim + captured content + endDelim (lazy match, dot-all mode)
     *
     * Examples:
     *   start="#" end="*"  content="#Hello!*#How are you?*#Goodbye!*"
     *   Result: ["Hello!", "How are you?", "Goodbye!"]
     *
     * If no delimiter pairs are found, returns the original content as a single item.
     * Delimiters are regex-escaped using Pattern.quote().
     */
    fun split(content: String, startDelim: String, endDelim: String): List<String> {
        val quotedStart = Pattern.quote(startDelim)
        val quotedEnd = Pattern.quote(endDelim)
        val regex = Regex("$quotedStart(.*?)$quotedEnd", RegexOption.DOT_MATCHES_ALL)

        val results = regex.findAll(content).map { it.groupValues[1] }.toList()

        return if (results.isEmpty()) {
            listOf(content)
        } else {
            results
        }
    }
}
