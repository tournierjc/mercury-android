package io.github.tournierjc.mercury.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadDto(
    val id: String = "",
    val shortId: String = "",
    val alias: String = "",
    val title: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ThreadsResponse(val threads: List<ThreadDto> = emptyList())

@Serializable
data class MessageDto(
    val role: String = "assistant",
    val kind: String? = null,
    val content: String = "",
    val timestamp: String = "",
)

@Serializable
data class ThreadMessagesResponse(val messages: List<MessageDto> = emptyList())

@Serializable
data class SendRequest(val content: String, val sessionId: String? = null)

@Serializable
data class SendResponse(val sessionId: String? = null, val error: String? = null)

@Serializable
data class HealthResponse(val pid: Int? = null, val version: String = "", val booting: Boolean = false)

@Serializable
data class PermissionActionRequest(val action: String)

@Serializable
data class ApiError(@SerialName("error") val message: String = "")
