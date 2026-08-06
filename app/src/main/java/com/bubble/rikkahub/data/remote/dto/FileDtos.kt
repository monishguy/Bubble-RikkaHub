package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.Serializable

/** A local file ready to upload (bytes read from a content URI). */
data class UploadFileData(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

/** One uploaded file as returned by POST /api/files/upload. */
@Serializable
data class UploadedFileDto(
    val id: Long = 0,
    /** file:// URI on the server (message parts reference this; resolve for display). */
    val url: String = "",
    val fileName: String = "",
    val mime: String = "",
    val size: Long = 0
)

@Serializable
data class UploadFilesResponseDto(
    val files: List<UploadedFileDto> = emptyList()
)
