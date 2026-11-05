package io.github.tournierjc.mercury.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consistency contract for the context keeper — the tests that make "compression" mean
 * something. Every test asserts a property the app *relies* on, not a golden output: the invariant
 * that after a compression the context size must always be lower is [everyCutStrictlyShrinksTheView]
 * and [repeatedCutsKeepShrinkingTheViewToMonotonicity]; the invariants that make a shrinking lie
 * impossible (nothing lost, no rule lost, no rollback leaving debris) sit beside it.
 */
class ContextKeeperConsistencyTest {

    // ---------- the headline invariant ----------

    @Test
    fun `every cut strictly shrinks the view`() {
        val conversation = longConversation(turns = 30, chars = 900)
        val keeper = keeper(writingPerfectNotes())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.Cut, outcome.skip)
        val report = outcome.report!!
        assertTrue(
            "a cut must always leave a smaller view (${report.afterTokens} !< ${report.beforeTokens})",
            report.afterTokens < report.beforeTokens,
        )
        assertEquals("the report's before-size must be the size of what was handed in",
            viewTokens(conversation), report.beforeTokens)
    }

    @Test
    fun `repeated cuts keep shrinking the view to monotonicity`() {
        // Grow the conversation one turn at a time and compress every step: every cut must strictly
        // shrink what it replaced, and across cuts the view must not climb more than one turn's
        // worth of growth (a cut only commits to what *it* removed — between cuts the newest
        // exchange genuinely grows, which is the conversation, not the keeper accordioning).
        val conversation = mutableListOf(systemTurn(), userTurn("kickoff", 0))
        val keeper = keeper(writingPerfectNotes(), budget = 4096)
        var lastCutView: Int? = null
        repeat(40) { i ->
            conversation += botTurn("answer $i: " + "blah ".repeat(150), conversation.size)
            conversation += userTurn("next step $i", conversation.size)
            val outcome = keeper.maybeCompress(conversation.toList())
            if (outcome.skip == SkipReason.Cut) {
                val report = outcome.report!!
                assertTrue("cut ${keeper.cuts}: after ${report.afterTokens} >= before ${report.beforeTokens}",
                    report.afterTokens < report.beforeTokens)
                val shown = keeper.lastViewTokens
                val turnGrowth = report.beforeTokens - report.afterTokens
                if (lastCutView != null) {
                    assertTrue("the shown view climbed more than the material a cut removed: $shown vs $lastCutView",
                        shown <= lastCutView!! + turnGrowth)
                }
                lastCutView = shown
            }
        }
        assertTrue("40 long turns without a single cut means the keeper is dead code", keeper.cuts > 0)
        assertNotNull("the last cut must have a report for the screen", keeper.lastReport)
    }

    // ---------- nothing is lost, only moved out of view ----------

    @Test
    fun `every turn a cut removes is in the archive verbatim`() {
        val conversation = longConversation(turns = 24, chars = 800)
        val keeper = keeper(writingPerfectNotes())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.Cut, outcome.skip)
        val report = outcome.report!!
        val archived = keeper.archivedTurns()
        assertEquals(report.removedTurns, archived.size)
        // The kept view plus the archive is the whole conversation: no turn exists in one and not
        // the other, and the archived text is byte-identical to what was handed in.
        val viewTexts = keeper.viewFor(conversation).map { it.text }.toSet()
        val archivedTexts = archived.map { it.text }.toSet()
        for (turn in conversation) {
            assertTrue(
                "turn ${turn.number} (${turn.role}) vanished — neither kept nor archived",
                turn.text in viewTexts || turn.text in archivedTexts,
            )
        }
        assertTrue(archived.map { it.number } == archived.map { it.number }.sorted())
    }

    @Test
    fun `a refused cut archives nothing and leaves the notes untouched`() {
        val conversation = longConversation(turns = 20, chars = 900)
        val keeper = keeper(writerReturningNull())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.NotesRejected, outcome.skip)
        assertTrue(keeper.archivedTurns().isEmpty())
        assertTrue(keeper.currentNotes().isEmpty)
        assertEquals(0, keeper.cuts)
    }

    @Test
    fun `a cut rolled back for not shrinking leaves no archive debris`() {
        // The notes writer returns the layout but stacks a giant rule every time — the kept view
        // would grow past the original, so the gate must roll the whole cut back, archive included.
        val conversation = longConversation(turns = 16, chars = 400)
        val keeper = keeper(writerAddingHugeRules())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.NotesRejected, outcome.skip)
        assertTrue("a rolled-back cut left turns in the archive", keeper.archivedTurns().isEmpty())
        assertEquals(0, keeper.cuts)
        assertTrue("a rolled-back cut kept its notes", keeper.currentNotes().isEmpty)
    }

    @Test
    fun `a keeper with no writer never compresses`() {
        val conversation = longConversation(turns = 30, chars = 900)
        val keeper = ContextKeeper(budget = 4096)
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.NotesRejected, outcome.skip)
        assertTrue(keeper.archivedTurns().isEmpty())
    }

    // ---------- the safety floor: notes may not collapse ----------

    @Test
    fun `a cut that moves out a lot on near-empty notes is refused`() {
        // Thousands of chars leaving with a notes block under the floor is a broken keeper, not a
        // compression: the cut must be refused rather than shown as done.
        val conversation = longConversation(turns = 14, chars = 700)
        val keeper = keeper(writerReturningLayoutButAlmostEmpty())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.NotesRejected, outcome.skip)
        assertTrue(keeper.archivedTurns().isEmpty())
    }

    // ---------- recall proves the archive is usable, not just a graveyard ----------

    @Test
    fun `archived material is searchable with its turn number`() {
        // The codename rides in the *body* (the opening question must stay verbatim, so a planted
        // fact there would never need recalling): the first cut moves it out, recall finds it.
        val conversation = longConversation(turns = 24, chars = 800, codename = "cedar-77")
        val keeper = keeper(writingPerfectNotes())
        keeper.maybeCompress(conversation)
        assertTrue(keeper.archivedTurns().isNotEmpty())
        val hits = keeper.recall("cedar-77")
        assertTrue("the codename left the view but is not findable in the archive", hits.isNotEmpty())
        assertTrue(hits.first().startsWith("turn "))
    }

    // ---------- view bounds ----------

    @Test
    fun `the kept view never exceeds the effective budget for large conversations`() {
        val conversation = longConversation(turns = 40, chars = 700)
        val keeper = keeper(writingPerfectNotes(), budget = 4096)
        keeper.maybeCompress(conversation)
        val view = keeper.viewFor(conversation)
        // One turn may overshoot alone (it must stay for the conversation to make sense); with the
        // last turn removed, everything fits the effective budget, plus rounding slack.
        val withoutLast = view.dropLast(1).sumOf { ESTIMATE.estimate(it.text) }
        assertTrue("view overshoots the budget by more than one turn: $withoutLast",
            withoutLast <= 4096 - 2048 - 1024 + 512 + 64)
    }

    @Test
    fun `the opening turns always stay in the view`() {
        val conversation = longConversation(turns = 30, chars = 900)
        val keeper = keeper(writingPerfectNotes())
        keeper.maybeCompress(conversation)
        val view = keeper.viewFor(conversation)
        assertTrue(view.isNotEmpty())
        assertTrue("system opener first", view.first().role == ContextTurn.Role.System)
        assertTrue("first user message second",
            view[1].role == ContextTurn.Role.User && view[1].text.startsWith("kickoff"))
    }

    @Test
    fun `the newest exchange never splits`() {
        // The keeper must never keep an answer whose question it archived: the newest user message
        // and everything after it are the exchange and stay whole.
        val conversation = longConversation(turns = 24, chars = 800)
        val keeper = keeper(writingPerfectNotes())
        keeper.maybeCompress(conversation)
        val view = keeper.viewFor(conversation)
        val archived = keeper.archivedTurns()
        val lastUserTurn = conversation.last { it.isUser }
        assertTrue("the newest question was archived — its answer would hang on nothing",
            view.any { it.role == ContextTurn.Role.User && it.text == lastUserTurn.text })
        assertTrue("turns after the newest question were archived",
            archived.none { it.number >= lastUserTurn.number })
    }

    @Test
    fun `a fitting conversation is not compressed`() {
        val conversation = listOf(
            systemTurn(), userTurn("short", 1), botTurn("ok", 2), userTurn("thanks", 3),
        )
        val keeper = keeper(writerReturningNull())
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.Fits, outcome.skip)
        assertTrue(keeper.archivedTurns().isEmpty())
    }

    @Test
    fun `a conversation too short to cut is reported as too short, not fits`() {
        // One giant assistant turn after the opener: needs compression, but the newest exchange is
        // everything there is — there is nothing to move out.
        val conversation = listOf(systemTurn(), userTurn("go", 1), botTurn("x".repeat(30_000), 2))
        val keeper = keeper(writerReturningNull(), budget = 4096)
        val outcome = keeper.maybeCompress(conversation)
        assertEquals(SkipReason.TooShort, outcome.skip)
    }

    // ---------- helpers ----------

    private fun systemTurn() = ContextTurn(0, ContextTurn.Role.System, "You are the fleet's lead bot. Be brief.")
    private fun userTurn(text: String, n: Int) = ContextTurn(n, ContextTurn.Role.User, text)
    private fun botTurn(text: String, n: Int) = ContextTurn(n, ContextTurn.Role.Assistant, text)

    /**
     * A conversation that certainly overruns any small budget: long bot turns, an owner turn every
     * third, and the codename planted in the oldest body turn (the first cut is what must archive it).
     */
    private fun longConversation(turns: Int, chars: Int, codename: String = "cedar-77"): List<ContextTurn> {
        val out = mutableListOf(systemTurn(), userTurn("kickoff. stand by.", 0))
        var n = 1
        repeat(turns) { i ->
            val seed = if (i == 1) "the codename is $codename. " else ""
            out += botTurn(seed + "turn $i: " + "lorem ipsum dolor sit amet ".repeat(chars / 27 + 1), n++)
            if (i % 3 == 2) out += userTurn("step $i " + "command ".repeat(chars / 8 + 1), n++)
        }
        return out
    }

    private class ScriptedWriter(private val reply: (Notes, List<ContextTurn>) -> String?) : NotesWriter {
        var calls = 0
        override fun requestDelta(current: Notes, leaving: List<ContextTurn>): NotesDelta? {
            calls += 1
            val raw = reply(current, leaving) ?: return null
            return NotesDelta.parse(raw)
        }
    }

    private fun writingPerfectNotes(): NotesWriter = ScriptedWriter { _, leaving ->
        val names = leaving.take(3).mapIndexed { i, t -> "fact$i: ${t.text.take(40)}" }
        buildString {
            appendLine("NEW RULES")
            appendLine("(none)")
            appendLine("CHANGES")
            names.forEach { appendLine(it) }
            appendLine("OPEN")
            appendLine("waiting on the next step")
        }
    }

    private fun writerReturningNull(): NotesWriter = NotesWriter { _, _ -> null }

    /** Returns a valid layout, but each cut stacks a huge new rule — the view would grow: the gate must roll back. */
    private fun writerAddingHugeRules(): NotesWriter = ScriptedWriter { _, _ ->
        "NEW RULES\n" + "rule ".repeat(2500) + "\nCHANGES\n(none)\nOPEN\n(none)"
    }

    /** A valid layout that records essentially nothing, while a lot leaves — the collapse floor must catch it. */
    private fun writerReturningLayoutButAlmostEmpty(): NotesWriter = NotesWriter { _, _ ->
        NotesDelta.parse("NEW RULES\n(none)\nCHANGES\n(none)\nOPEN\n(none)")
    }

    private val ESTIMATE = TokenEstimate()

    private fun keeper(writer: NotesWriter, budget: Int = 4096): ContextKeeper =
        ContextKeeper(budget = budget, notesWriter = writer)

    private fun viewTokens(conversation: List<ContextTurn>): Int =
        conversation.sumOf { ESTIMATE.estimate(it.text) }
}

/** The notes layout and its safety rules — the ctxkeeper contract, pinned. */
class NotesTest {

    @Test
    fun `parse accepts the exact layout and round-trips`() {
        val text = """
            RULES
            always answer in French
            STATE
            codename: cedar-77
            budget = 500
            OPEN
            waiting on deploy
        """.trimIndent()
        val notes = Notes.parse(text)!!
        assertEquals(listOf("always answer in French"), notes.rules)
        assertEquals(listOf("codename" to "cedar-77", "budget" to "500"), notes.state)
        assertEquals(listOf("waiting on deploy"), notes.open)
        // render -> parse keeps the content
        assertEquals(notes, Notes.parse(notes.render()))
    }

    @Test
    fun `a reply with no RULES heading is rejected`() {
        assertNull(Notes.parse("Sure! Here is the answer..."))
        assertNull(NotesDelta.parse("Sure! Here is the answer..."))
    }

    @Test
    fun `a delta with no CHANGES heading is rejected`() {
        assertNull(NotesDelta.parse("NEW RULES\nsome rule\nOPEN\nsomething"))
    }

    @Test
    fun `a missing OPEN heading keeps the old open section`() {
        val delta = NotesDelta.parse("NEW RULES\n(none)\nCHANGES\nk: v")!!
        assertNull(delta.open)
    }

    @Test
    fun `rules can never be dropped by a delta`() {
        val current = Notes(rules = listOf("answer in French", "never push to main"), state = listOf("a" to "1"))
        val delta = NotesDelta.parse("NEW RULES\n(none)\nCHANGES\na: 2\nOPEN\n(none)")!!
        val outcome = NotesDeltaApplier.apply(current, delta) as NotesDeltaApplier.Outcome.Applied
        assertEquals(listOf("answer in French", "never push to main"), outcome.notes.rules)
        assertEquals(listOf("a" to "2"), outcome.notes.state)
    }

    @Test
    fun `a reworded new rule does not stack beside its original`() {
        val current = Notes(rules = listOf("always run the tests before shipping anything"))
        val delta = NotesDelta.parse("NEW RULES\nalways run the tests before shipping\nCHANGES\n(none)")!!
        val applied = NotesDeltaApplier.apply(current, delta) as NotesDeltaApplier.Outcome.Applied
        assertEquals(1, applied.notes.rules.size)
    }

    @Test
    fun `an extended rule is added not swallowed as a restatement`() {
        val current = Notes(rules = listOf("run the tests"))
        val delta = NotesDelta.parse(
            "NEW RULES\nrun the tests and the linter and the integration suite before any release\nCHANGES\n(none)",
        )!!
        val applied = NotesDeltaApplier.apply(current, delta) as NotesDeltaApplier.Outcome.Applied
        assertEquals(2, applied.notes.rules.size)
    }

    @Test
    fun `a state line keeps its place and a new name appends and removed deletes`() {
        val current = Notes(state = listOf("a" to "1", "b" to "2", "c" to "3"))
        val delta = NotesDelta.parse("NEW RULES\n(none)\nCHANGES\nb: 9\nd: 4\nc: removed")!!
        val applied = NotesDeltaApplier.apply(current, delta) as NotesDeltaApplier.Outcome.Applied
        assertEquals(listOf("a" to "1", "b" to "9", "d" to "4"), applied.notes.state)
    }

    @Test
    fun `a working that computes is kept and a working that lies is refused`() {
        val ok = NotesDeltaApplier.resolveWorking("120 + 49 = 169")
        assertEquals("120 + 49 = 169", ok)
        val lie = NotesDeltaApplier.resolveWorking("120 + 49 = 168")
        assertNull("a false working must be refused, not stored", lie)
        // values without a +- chain are never wrongly refused
        assertEquals("the deploy failed on the staging box",
            NotesDeltaApplier.resolveWorking("the deploy failed on the staging box"))
        assertEquals("v2.1.0-beta", NotesDeltaApplier.resolveWorking("v2.1.0-beta"))
    }

    @Test
    fun `pinned notes tell the model how much is in the archive`() {
        val notes = Notes(rules = listOf("r"))
        val pinned = notes.pinned(7)
        assertTrue(pinned.startsWith("[[NOTES kept by the context keeper: 7 earlier messages are in the archive]]"))
        assertTrue(pinned.contains("r"))
    }
}

/** The ratio the estimates ride on — learned, bounded, never collapsing. */
class TokenEstimateTest {

    @Test
    fun `observed usage moves the ratio toward the server`() {
        val e = TokenEstimate()
        val before = e.estimate("a".repeat(3600)) // ratio 3.6 -> ~1000 tokens
        // The server says 3 chars/token for this kind of prompt: tokens are more expensive than the
        // guess assumed, so the estimate for the same text must go UP (cut earlier), never down.
        repeat(6) { e.observe(chars = 3600, promptTokens = 1200) }
        val after = e.estimate("a".repeat(3600))
        assertTrue("ratio did not move toward the server's count: $before -> $after", after > before)
        assertTrue(e.ratio in 1.5..10.0)
    }

    @Test
    fun `a nonsense measurement cannot poison the ratio`() {
        val e = TokenEstimate()
        e.observe(chars = 0, promptTokens = 999)
        e.observe(chars = 1000, promptTokens = 0)
        e.observe(chars = 1000, promptTokens = -5)
        assertEquals(TokenEstimate.DEFAULT_RATIO, e.ratio, 1e-9)
    }

    @Test
    fun `estimate is never negative and zero for empty`() {
        val e = TokenEstimate()
        assertEquals(0, e.estimate(""))
        assertTrue(e.estimate("hello") > 0)
    }
}
