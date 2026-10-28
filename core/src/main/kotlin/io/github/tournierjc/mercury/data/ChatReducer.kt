package io.github.tournierjc.mercury.data

/** Pure chat state reduced from [ChatEvent]s; kept Android-free so it is unit tested. */
data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String = "",
    val steps: List<ToolStep> = emptyList(),
    val streaming: Boolean = false,
    val failed: Boolean = false,
    /**
     * The steps came from a stored tool summary, not from a turn in progress: they are history and
     * are drawn as a block, where live steps are a row of chips.
     */
    val toolTrace: Boolean = false,
    /**
     * The text is a summary the daemon cut short (see BotThread), not the answer: the screen offers
     * to load the rest instead of pretending this is all there was.
     */
    val truncated: Boolean = false,
) {
    enum class Role { User, Assistant }
}

data class ToolStep(val tool: String, val label: String, val summary: String? = null)

enum class InteractionKind {
    /** Approve a tool call (`yes` / `always` / `no`). */
    Permission,

    /** "Keep going?" after a loop guard. */
    Continue,

    /** The agent needs a free-form answer among [Interaction.options]. */
    Choice,

    /** Switch to auto-approval or stay interactive. */
    Mode,
}

/**
 * Something the agent is blocked on. All four kinds are answered through the same
 * route (`POST /api/chat/permission/:id` with an `action`), so the app models them
 * uniformly and only varies the wording.
 */
data class Interaction(
    val id: String,
    val kind: InteractionKind,
    /** The server's own wording — an agent asking for a tool, or a choice it wrote itself. */
    val text: String = "",
    val options: List<String> = emptyList(),
    /** Set when the sentence is ours: the screen then speaks the owner's language. */
    val notice: Notice? = null,
)

data class ChatState(
    val serverUrl: String = "",
    val provider: String = "",
    val model: String = "",
    val sessionId: String? = null,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val connected: Boolean = false,
    val interactions: List<Interaction> = emptyList(),
    /** A warning from the daemon: shown, does not stop the turn. */
    val warning: Notice? = null,
    /** A failure the screen shows as a banner; the words are resolved where it is drawn. */
    val error: Notice? = null,
) {
    val isWelcome: Boolean get() = messages.isEmpty()

    /** Oldest unanswered interaction; the UI shows one at a time. */
    val interaction: Interaction? get() = interactions.firstOrNull()

    val modelLabel: String
        get() = when {
            model.isNotBlank() && provider.isNotBlank() -> "$provider · $model"
            model.isNotBlank() -> model
            else -> provider
        }
}

object ChatReducer {

    fun addUserMessage(state: ChatState, text: String): ChatState {
        val message = ChatMessage(id = "user-${state.messages.size}", role = ChatMessage.Role.User, text = text)
        return state.copy(messages = state.messages + message, error = null)
    }

    /** Drops the interaction the user just answered. */
    fun clearPermission(state: ChatState, id: String? = null): ChatState = state.copy(
        interactions = if (id == null) state.interactions.drop(1) else state.interactions.filterNot { it.id == id },
    )

    fun setError(state: ChatState, notice: Notice?): ChatState = state.copy(error = notice)

    fun dismissWarning(state: ChatState): ChatState = state.copy(warning = null)

    fun newChat(state: ChatState): ChatState = state.copy(
        sessionId = null,
        title = "",
        messages = emptyList(),
        streaming = false,
        interactions = emptyList(),
        warning = null,
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

        is ChatEvent.PermissionRequest -> addInteraction(
            state,
            Interaction(event.id, InteractionKind.Permission, event.prompt, event.options),
        )

        is ChatEvent.ContinueRequest -> addInteraction(
            state,
            Interaction(event.id, InteractionKind.Continue, event.question, event.options),
        )

        is ChatEvent.ChoicePrompt -> addInteraction(
            state,
            Interaction(event.id, InteractionKind.Choice, event.question, event.options),
        )

        is ChatEvent.PermissionModeRequest -> addInteraction(
            state,
            Interaction(
                event.id,
                InteractionKind.Mode,
                text = "",
                options = event.options,
                notice = Notice(NoticeKey.PERMISSION_MODE_QUESTION),
            ),
        )

        is ChatEvent.SessionSwitched -> state.copy(
            sessionId = event.sessionId.ifBlank { state.sessionId },
            title = event.title.ifBlank { state.title },
        )

        is ChatEvent.ProviderInfo -> state.copy(
            provider = event.name.ifBlank { state.provider },
            model = event.model.ifBlank { state.model },
        )

        is ChatEvent.Warning -> state.copy(
            warning = event.message.takeIf { it.isNotBlank() }
                ?.let { Notice(NoticeKey.SERVER_WARNING).withDetail(it).asWarning() },
        )

        is ChatEvent.Failure -> state.copy(
            streaming = false,
            error = Notice(NoticeKey.STREAM_FAILED).withDetail(event.message),
            messages = state.messages.map { if (it.streaming) it.copy(streaming = false) else it },
        )

        is ChatEvent.Ignored -> state
    }

    private fun addInteraction(state: ChatState, interaction: Interaction): ChatState =
        if (state.interactions.any { it.id == interaction.id }) {
            state
        } else {
            state.copy(interactions = state.interactions + interaction)
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
