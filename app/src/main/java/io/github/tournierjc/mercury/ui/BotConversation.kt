package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.bots.LiveTurn
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.vm.BotsUiState

/**
 * The bot conversation as the chat screen draws it.
 *
 * A pure function on purpose: the screen rebuilds this list on every live tick, inside a `remember`
 * block where nothing can be asserted. It lived there — and it worked from *part* of the state, so
 * the tool calls and the thinking the view model had just fetched were passed to nothing. Keeping
 * the assembly here means the list a bubble comes from can be built and checked in a JVM test.
 */
object BotConversation {

    /**
     * @param activity what the daemon says the open turn is doing, from the live feed.
     * @param queued/failed/noAnswer the sentences the screen shows when a bubble has no text of its
     *   own: read from resources by the caller, because a `stringResource` call is not allowed
     *   inside the `remember` block that used to own this code.
     */
    fun messages(
        state: BotsUiState,
        botId: String?,
        live: LiveTurn?,
        activity: String,
        queued: String,
        failed: String,
        noAnswer: String,
    ): List<ChatMessage> {
        if (botId == null) return emptyList()
        // Everything the state holds, not an arbitrary subset: `traces` and `reasoning` are the two
        // that were missing, and their absence is invisible — the thread simply had fewer bubbles
        // worth reading.
        return BotThread.build(
            records = state.records,
            prompts = state.prompts,
            outputs = state.answers,
            liveJobId = live?.jobId,
            liveTail = live?.text.orEmpty(),
            liveRunning = live?.running == true,
            liveActivity = activity,
            traces = state.traces,
            reasoning = state.reasoning,
            reasoningTruncated = state.reasoningTruncated,
            // Only this bot's echo: pendings outlive a refresh, and one addressed to another
            // teammate must not appear here.
            pending = state.pending.filter { it.botId.isBlank() || it.botId == botId },
            liveReasoning = live?.reasoning.orEmpty(),
        ).map { message ->
            ChatMessage(
                id = message.key,
                role = if (message.fromBot) ChatMessage.Role.Assistant else ChatMessage.Role.User,
                text = message.text.ifBlank {
                    when {
                        message.streaming -> "…"
                        message.state == "queued" -> queued
                        message.state == "failed" -> failed
                        else -> noAnswer
                    }
                },
                streaming = message.streaming,
                // The bubble dates itself from the turn's own clock (the journal's `startedAt` +
                // `durationMs`): without this line a bot's answer drew no hour at all, while the
                // main thread drew one.
                sentAt = message.at,
                steps = message.steps,
                // Stored steps are history and are drawn as a block, not as the row of chips a turn
                // in progress shows.
                toolTrace = message.steps.isNotEmpty(),
                truncated = message.truncated,
                reasoning = message.reasoning,
                reasoningTruncated = message.reasoningTruncated,
                activity = message.activity,
            )
        }
    }
}
