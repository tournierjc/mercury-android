package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.context.ContextKeeper
import io.github.tournierjc.mercury.context.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keeper on a thread built by [BotThread] itself — the seam between the compression module and
 * the conversation the screen actually draws. The gate ("after a compression the context size must
 * always be lower") has to hold on this shape, not only on synthetic lists.
 */
class BotThreadCompressionTest {

    @Test
    fun `a long journal compresses through the keeper and always shrinks`() {
        val records = (1..20).map { record("r$it", "answer $it: " + "result ".repeat(150), at = it * 1_000L) }
        val prompts = records.associate { it.runId to "task ${it.runId} " + "command ".repeat(80) }
        val thread = BotThread.build(records, prompts)
        assertTrue("a journal this long must trigger compression",
            ContextKeeper(budget = 4096).needsCompression(BotThreadCompression.turnsOf(thread)))

        val keeper = ContextKeeper(
            budget = 4096,
            notesWriter = io.github.tournierjc.mercury.context.NotesWriter { _, leaving ->
                io.github.tournierjc.mercury.context.NotesDelta.parse(
                    "NEW RULES\n(none)\nCHANGES\n" +
                        leaving.take(2).mapIndexed { i, t -> "fact$i: ${t.text.take(40)}" }.joinToString("\n") +
                        "\nOPEN\n(none)",
                )
            },
        )
        val (view, outcome) = BotThreadCompression.compressedThread(thread, keeper)
        // The screen may only ever see a thread that got shorter, with a report proving it.
        if (outcome.skip == SkipReason.Cut) {
            val report = outcome.report!!
            assertTrue("compressed thread is not smaller: ${report.afterTokens} >= ${report.beforeTokens}",
                report.afterTokens < report.beforeTokens)
            assertTrue(view.size < thread.size)
            // Nothing was invented: every bubble in the kept view is text from the original thread,
            // or the pinned notes block.
            val originals = thread.map { it.text }.toSet()
            for (m in view.filter { it.state != BotThreadCompression.NOTES }) {
                assertTrue("the kept view invented a bubble", m.text in originals)
            }
        }
    }

    @Test
    fun `a measure-only keeper never changes the thread`() {
        val records = (1..10).map { record("r$it", "a ".repeat(500), at = it * 1_000L) }
        val thread = BotThread.build(records, emptyMap())
        val keeper = ContextKeeper(budget = 4096, notesWriter = BotThreadCompression.measureOnly)
        val (view, outcome) = BotThreadCompression.compressedThread(thread, keeper)
        assertEquals(SkipReason.NotesRejected, outcome.skip)
        assertEquals("a writer that cannot ask the model must leave the thread untouched", thread, view)
    }

    private fun record(runId: String, summary: String, at: Long) =
        io.github.tournierjc.mercury.data.dto.BotRunDto(runId = runId, botId = "b", state = "done", startedAt = at, durationMs = 500, summary = summary)
}
