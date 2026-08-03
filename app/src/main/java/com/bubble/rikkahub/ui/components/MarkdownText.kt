package com.bubble.rikkahub.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Renders text with Markdown-like formatting.
 * Supports: **bold**, *italic*, `code`, ~~strikethrough~~, [links](url)
 *
 * If the text contains no markdown formatting, renders as plain Text
 * for better performance.
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val hasMarkdown = MARKDOWN_PATTERN.containsMatchIn(text)
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    if (!hasMarkdown) {
        // Plain text - no markdown rendering overhead
        Text(
            text = text,
            color = color,
            style = style,
            modifier = modifier
        )
    } else {
        Text(
            text = buildAnnotatedMarkdown(text, codeBg, linkColor),
            color = color,
            style = style,
            modifier = modifier,
            inlineContent = inlineContentMap
        )
    }
}

// Patterns for markdown detection
private val MARKDOWN_PATTERN = Regex(
    "\\*\\*|\\*|__|_|~~|`|\\[|\\]\\(|^#{1,6}\\s|^>\\s|^[*-]\\s|^\\d+\\.\\s",
    setOf(RegexOption.MULTILINE)
)

// Inline content map (for code blocks, images, etc.)
private val inlineContentMap = mapOf<String, InlineTextContent>()

/**
 * Simple markdown-to-AnnotatedString renderer.
 * Handles inline formatting: bold, italic, code, strikethrough, links.
 */
private fun buildAnnotatedMarkdown(text: String, codeBg: Color, linkColor: Color) = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        when {
            // **bold** or __bold__
            (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                val delim = if (text.startsWith("**", i)) "**" else "__"
                val end = text.indexOf(delim, i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // *italic* or _italic_ (but not **)
            ((text[i] == '*' && (i == 0 || text[i-1] != '*') && text.getOrNull(i+1) != '*') ||
             (text[i] == '_' && (i == 0 || text[i-1] != '_') && text.getOrNull(i+1) != '_')) -> {
                val delim = text[i].toString()
                val end = text.indexOf(delim, i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // ~~strikethrough~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = codeBg
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // [link](url)
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i)
                val openParen = if (closeBracket > i) text.indexOf('(', closeBracket) else -1
                val closeParen = if (openParen > closeBracket) text.indexOf(')', openParen) else -1
                if (closeBracket > i && openParen == closeBracket + 1 && closeParen > openParen) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val url = text.substring(openParen + 1, closeParen)
                    withStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append(linkText)
                    }
                    i = closeParen + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Newline
            text[i] == '\n' -> {
                append('\n')
                i++
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
