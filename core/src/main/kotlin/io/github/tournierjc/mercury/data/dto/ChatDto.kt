package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chat-domain payloads, mirroring the JSON Mercury 1.4.2 actually returns.
 *
 * Every field has a default: Mercury may add keys (ignored) but a missing key
 * must not crash a screen. Names match the wire exactly — these are the
 * contract, so they are covered by `ChatDtoTest`.
 */

@Serializable
data class ModelRef(
    val name: String = "",
    val model: String = "",
    val label: String = "",
    val autoLabel: String = "",
    val customLabel: String? = null,
    val group: String = "",
    val reasoning: String = "",
    val reasoningLevels: List<String> = emptyList(),
    val available: Boolean = false,
    val isCurrent: Boolean = false,
) {
    val displayLabel: String get() = customLabel?.takeIf { it.isNotBlank() } ?: label.ifBlank { model }
}

@Serializable
data class ModelsResponse(
    val current: ModelRef? = null,
    val providers: List<ModelRef> = emptyList(),
)

@Serializable
data class SwitchModelRequest(val provider: String, val model: String)

@Serializable
data class ModelPrefsRequest(
    val provider: String,
    val model: String,
    val label: String? = null,
    val reasoning: String? = null,
)

/** `GET /api/chat/settings`. `permissionMode` is derived server-side, not settable. */
@Serializable
data class ChatSettings(
    val bypassPermissions: Boolean = false,
    val restrictUser: Boolean = false,
    val workspace: String = "",
    val permissionMode: String = "ask-me",
) {
    val autoApproves: Boolean get() = permissionMode == "allow-all" || bypassPermissions
}

/** `PUT /api/chat/settings`: absent fields keep their current value. */
@Serializable
data class ChatSettingsUpdate(
    val bypassPermissions: Boolean? = null,
    val restrictUser: Boolean? = null,
    val workspace: String? = null,
)

@Serializable
data class ChatSettingsResponse(
    val success: Boolean = false,
    val bypassPermissions: Boolean = false,
    val restrictUser: Boolean = false,
    val workspace: String = "",
    val permissionMode: String = "ask-me",
) {
    fun toSettings(): ChatSettings = ChatSettings(bypassPermissions, restrictUser, workspace, permissionMode)
}

@Serializable
data class DeleteThreadRequest(@SerialName("confirmSessionId") val confirmSessionId: String)

@Serializable
data class UsageEntry(
    val provider: String = "",
    val model: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val channelType: String = "",
    val timestamp: Long = 0,
)

@Serializable
data class UsageDto(
    val dailyUsed: Long = 0,
    val dailyBudget: Long = 0,
    val lastResetDate: String = "",
    val remaining: Long = 0,
    val requestLog: List<UsageEntry> = emptyList(),
    val byProvider: Map<String, Long> = emptyMap(),
    val byChannel: Map<String, Long> = emptyMap(),
) {
    /** 0f..1f, safe when the budget is unlimited (0). */
    val ratio: Float
        get() = if (dailyBudget <= 0L) 0f else (dailyUsed.toFloat() / dailyBudget.toFloat()).coerceIn(0f, 1f)
}

@Serializable
data class PasswordChangeRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class UsernameChangeRequest(val currentPassword: String, val newUsername: String)

/** Envelope used by most action endpoints (`{ok:true,message:…}` or `{success:true}`). */
@Serializable
data class SimpleResponse(
    val ok: Boolean = false,
    val success: Boolean = false,
    val message: String = "",
    val error: String = "",
    val resolved: Boolean = false,
    val cancelled: Boolean = false,
    val cleared: Int = 0,
    val halted: Int = 0,
    val resumed: Int = 0,
    val heldJobs: Int = 0,
    val accepted: Boolean = false,
    val status: String = "",
) {
    val isOk: Boolean get() = ok || success || resolved || accepted || status == "accepted"
}

