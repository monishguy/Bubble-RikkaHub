package com.bubble.rikkahub

import com.bubble.rikkahub.util.MessageSplitter
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSplitterTest {

    @Test
    fun `splits on default delimiters`() {
        val input = "#Hello!*#How are you?*#Goodbye!*"
        val result = MessageSplitter.split(input, "#", "*")
        assertEquals(listOf("Hello!", "How are you?", "Goodbye!"), result)
    }

    @Test
    fun `returns single item when no delimiters found`() {
        val input = "Hello, this is a normal message without delimiters."
        val result = MessageSplitter.split(input, "#", "*")
        assertEquals(listOf(input), result)
    }

    @Test
    fun `handles multiline bubble content`() {
        val input = "#Line 1\nLine 2*#Line 3\nLine 4*"
        val result = MessageSplitter.split(input, "#", "*")
        assertEquals(listOf("Line 1\nLine 2", "Line 3\nLine 4"), result)
    }

    @Test
    fun `handles custom delimiters`() {
        val input = "{{Hello!}}<<How are you?>>"
        val result = MessageSplitter.split(input, "{{", "}}")
        assertEquals(listOf("Hello!"), result)
    }

    @Test
    fun `handles empty content between delimiters`() {
        val input = "#first*#*#third*"
        val result = MessageSplitter.split(input, "#", "*")
        assertEquals(listOf("first", "", "third"), result)
    }

    @Test
    fun `handles special regex characters in delimiters`() {
        val input = ".+Hello!.+"
        val result = MessageSplitter.split(input, ".+", ".+")
        assertEquals(listOf("Hello!"), result)
    }

    @Test
    fun `handles empty input`() {
        val result = MessageSplitter.split("", "#", "*")
        assertEquals(listOf(""), result)
    }
}
