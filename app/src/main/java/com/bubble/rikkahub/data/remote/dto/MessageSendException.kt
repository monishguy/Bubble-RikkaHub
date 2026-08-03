package com.bubble.rikkahub.data.remote.dto

import io.ktor.http.HttpStatusCode

/** Thrown when the server rejects a message send with a non-2xx HTTP status. */
class MessageSendException(
    val status: HttpStatusCode,
    message: String
) : Exception(message)
