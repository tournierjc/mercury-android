package io.github.tournierjc.mercury.data

import java.time.Instant

/** Pure chat state reduced from [ChatEvent]s; kept Android-free so it is unit tested. */
data class ChatMessage(
    val id: String,
    val role: Role,
    /**
     * Whether this message reached the daemon — the only send-failure signal the phone has.
     *
     * A stored exchange read back from a thread was, by definition, accepted: the history path
     * states [Delivery.Sent], which is why that is also the default — a message only ever exists
     * as unsent while its own send is in flight. [ChatReducer.addUserMessage] opens a turn as
     * [Delivery.Sending]; [ChatReducer.markUserSent] and [ChatReducer.markUserFailed] close it on
     * the answer of `POST /api/chat`. Replies carry it too but draw nothing with it: their bubble
     * speaks through [streaming] and [failed].
     */
    val delivery: Delivery = Delivery.Sent,
    val text: String = "",
    val steps: List<ToolStep> = emptyList(),
    val streaming: Boolean = false,
    val failed: Boolean = false,
    /**
     * The moment the daemon dated this message, from the server's clock (SPECS/115).
     *
     * `null` is a real state, not a default: a message built from the live stream — or an
     * optimistic turn the journal does not know yet — has no date, and its bubble must then
     * draw no hour rather than invent a local one.
     */
    val at: Instant? = null,
) {
    enum class Role { User, Assistant }

    /** Lifecycle of one turn written on the phone, from tap to the daemon's answer. */
    enum class Delivery { Sending, Sent, Failed }
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
    /** What the screen draws; the value inside each option is what gets posted. */
    val options: List<ChoiceOption> = emptyList(),
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
        val message = ChatMessage(
            id = "user-${state.messages.size}",
            role = ChatMessage.Role.User,
            delivery = ChatMessage.Delivery.Sending,
            text = text,
        )
        return state.copy(messages = state.messages + message, error = null)
    }

    /** The daemon took turn [id]: its bubble has nothing left to say about the send. */
    fun markUserSent(state: ChatState, id: String): ChatState =
        setUserDelivery(state, id, ChatMessage.Delivery.Sent)

    /** Turn [id] never reached the daemon: its bubble must say so, in the owner's language. */
    fun markUserFailed(state: ChatState, id: String): ChatState =
        setUserDelivery(state, id, ChatMessage.Delivery.Failed)

    /**
     * Puts [delivery] on the owner's turn [id].
     *
     * An unknown id, or the id of a reply, leaves the state untouched: a late answer from the
     * network can never paint somebody else's bubble nor invent a message it never sent.
     */
    fun setUserDelivery(state: ChatState, id: String, delivery: ChatMessage.Delivery): ChatState {
        val index = state.messages.indexOfLast { it.id == id && it.role == ChatMessage.Role.User }
        if (index < 0) return state
        val message = state.messages[index]
        if (message.delivery == delivery) return state
        return state.copy(
            messages = state.messages.toMutableList().also { it[index] = message.copy(delivery = delivery) },
        )
    }

    /** Drops the interaction the user just answered. */
    fun clearPermission(state: ChatState, id: String? = null): ChatState = state.copy(
        interactions = if (id == null) state.interactions.drop(1) else state.interactions.filterNot { it.id == id },
    )

    fun setError(state: ChatState, notice: Notice?): ChatState = state.copy(error = notice)

    fun dismissWarning(state: ChatState): ChatState = state.copy(warning = null)

    /**
     * Puts the server's date on the reply [id] once the stored thread knows it.
     *
     * The live stream dates nothing (SPECS/115 §3.4): `text_done` carries no instant, so the hour
     * under a reply arrives late, from the thread the daemon wrote. Nothing is guessed here — a
     * null date, an unknown id, a bubble still being written or one already dated all leave the
     * state untouched, so a late answer can never overwrite a date with a poorer one.
     */
    fun stampAssistantAt(state: ChatState, id: String, at: Instant?): ChatState {
        if (at == null) return state
        val index = state.messages.indexOfLast { it.id == id && it.role == ChatMessage.Role.Assistant }
        if (index < 0) return state
        val message = state.messages[index]
        if (message.streaming || message.at != null) return state
        return state.copy(
            messages = state.messages.toMutableList().also { it[index] = message.copy(at = at) },
        )
    }

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

        // A choice with no usable option still gets its card (DEC-9, SPECS/115 §16): the daemon is
        // blocked on this prompt until its own timeout, so dropping the interaction left the screen
        // mute for minutes. The card is drawn without a button and says `perm_no_options` where the
        // footnote used to sit — the app still invents no value to post, it just stops hiding the
        // wait.
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
