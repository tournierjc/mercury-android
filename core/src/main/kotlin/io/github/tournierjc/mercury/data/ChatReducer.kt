package io.github.tournierjc.mercury.data

/** Pure chat state reduced from [ChatEvent]s; kept Android-free so it is unit tested. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String = "",
    val steps: List<ToolStep> = emptyList(),
    val streaming: Boolean = false,
    val failed: Boolean = false,
) {
    enum class Role { User, Assistant }
}

data class ToolStep(val tool: String, val label: String, val summary: String? = null)

data class PermissionPrompt(val id: String, val prompt: String, val options: List<String>)

data class ChatState(
    val serverUrl: String = "",
    val provider: String = "",
    val model: String = "",
    val sessionId: String? = null,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val connected: Boolean = false,
    val pendingPermission: PermissionPrompt? = null,
    val error: String? = null,
) {
    val isWelcome: Boolean get() = messages.isEmpty()
}

object ChatReducer {

    fun addUserMessage(state: ChatState, text: String): ChatState {
        val message = ChatMessage(id = "user-${state.messages.size}", role = ChatMessage.Role.User, text = text)
        return state.copy(messages = state.messages + message, error = null)
    }

    fun clearPermission(state: ChatState): ChatState = state.copy(pendingPermission = null)

    fun setError(state: ChatState, message: String?): ChatState = state.copy(error = message)

    fun newChat(state: ChatState): ChatState = state.copy(
        sessionId = null,
        title = "",
        messages = emptyList(),
        streaming = false,
        pendingPermission = null,
        error = null,
    )

    fun onEvent(state: ChatState, event: ChatEvent): ChatState = when (event) {
        is ChatEvent.Connected -> state.copy(
            connected = true,
            provider = event.provider.ifBlank { state.provider },
            model = event.model.ifBlank { state.model },
        )

        is ChatEvent.TextDelta -> appendDelta(state, event.text)

        is ChatEvent.TextDone -> finishAssistant(state, event.fullText)

        ChatEvent.Thinking -> state.copy(streaming = true)

        is ChatEvent.StepStart -> updateAssistant(state) { message ->
            message.copy(steps = message.steps + ToolStep(event.tool, event.label))
        }

        is ChatEvent.StepDone -> updateAssistant(state) { message ->
            val index = message.steps.indexOfLast { it.tool == event.tool && it.summary == null }
            if (index >= 0) {
                message.copy(
                    steps = message.steps.toMutableList().also { it[index] = it[index].copy(summary = event.summary) },
                )
            } else {
                message.copy(steps = message.steps + ToolStep(event.tool, event.tool, event.summary))
            }
        }

        is ChatEvent.PermissionRequest -> state.copy(
            pendingPermission = PermissionPrompt(event.id, event.prompt, event.options),
        )

        is ChatEvent.ContinueRequest -> state.copy(
            pendingPermission = PermissionPrompt(event.id, event.question, listOf("yes", "no")),
        )

        is ChatEvent.SessionSwitched -> state.copy(
            sessionId = event.sessionId.ifBlank { state.sessionId },
            title = event.title.ifBlank { state.title },
        )

        is ChatEvent.Failure -> state.copy(
            streaming = false,
            error = event.message,
            messages = state.messages.map { if (it.streaming) it.copy(streaming = false) else it },
        )

        is ChatEvent.Ignored -> state
    }

    private fun appendDelta(state: ChatState, delta: String): ChatState {
        if (delta.isEmpty()) return state
        val messages = state.messages
        val last = messages.lastOrNull()
        return if (last != null && last.role == ChatMessage.Role.Assistant) {
            state.copy(
                streaming = true,
                messages = messages.dropLast(1) + last.copy(text = last.text + delta, streaming = true),
            )
        } else {
            state.copy(
                streaming = true,
                messages = messages + ChatMessage(
                    id = "assistant-${messages.size}",
                    role = ChatMessage.Role.Assistant,
                    text = delta,
                    streaming = true,
                ),
            )
        }
    }

    private fun finishAssistant(state: ChatState, fullText: String): ChatState {
        val messages = state.messages
        val last = messages.lastOrNull()
        return if (last != null && last.role == ChatMessage.Role.Assistant) {
            state.copy(
                streaming = false,
                messages = messages.dropLast(1) +
                    last.copy(text = fullText.ifBlank { last.text }, streaming = false),
            )
        } else {
            state.copy(
                streaming = false,
                messages = messages + ChatMessage(
                    id = "assistant-${messages.size}",
                    role = ChatMessage.Role.Assistant,
                    text = fullText,
                    streaming = false,
                ),
            )
        }
    }

    private fun updateAssistant(state: ChatState, transform: (ChatMessage) -> ChatMessage): ChatState {
        val messages = state.messages
        val last = messages.lastOrNull()
        return if (last != null && last.role == ChatMessage.Role.Assistant) {
            state.copy(messages = messages.dropLast(1) + transform(last))
        } else {
            state.copy(
                messages = messages + transform(
                    ChatMessage(
                        id = "assistant-${messages.size}",
                        role = ChatMessage.Role.Assistant,
                        streaming = true,
                    ),
                ),
            )
        }
    }
}
