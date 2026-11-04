package io.github.tournierjc.mercury.context

/**
 * One message of a conversation, in the shape the keeper works with.
 *
 * The keeper never parses the wire itself: the chat screen hands it the messages the reducer
 * produced, so a turn here is already "a bubble with its text". [number] is its position in the
 * conversation — the number the archive and every [CutReport] speak in.
 */
data class ContextTurn(
    val number: Int,
    val role: Role,
    val text: String,
) {
    enum class Role { System, User, Assistant }

    val isUser: Boolean get() = role == Role.User
}

/**
 * Why compression did not run, or why a cut was refused. Each is its own test in
 * `ContextKeeperConsistencyTest` — a silent no-op is how a "context manager" ships that quietly
 * stops managing anything.
 */
enum class SkipReason {
    /** The view already fits the budget: nothing to do. */
    Fits,

    /** Not enough turns for a cut to have anything to move out (the head and the newest exchange stay). */
    TooShort,

    /** The model's notes reply did not use the layout — the cut was refused, not faked. */
    NotesRejected,

    /** The cut passed the gate and ran. */
    Cut,
}

/** The outcome of one [ContextKeeper.maybeCompress] — the caller tells the user what actually happened. */
data class KeepOutcome(
    val skip: SkipReason,
    val report: CutReport? = null,
)

/**
 * The context keeper: ctxkeeper's policy, in the app, for the bot conversation.
 *
 * The screen keeps holding the whole conversation (the journal is the durable record). Before the
 * thread is drawn or sent, the keeper answers one question: can this view still be shown/used
 * inside the budget? When it cannot, the keeper cuts — and a cut is only real when four things all
 * happened, which is what this class enforces and the consistency tests assert:
 *
 * 1. every turn the cut moved out went to the [TurnArchive] **verbatim** (nothing is lost, only
 *    moved out of view);
 * 2. the notes were refreshed *before* the turns left (the model itself writes what changes, in
 *    the `NEW RULES / CHANGES / OPEN` layout — [NotesDeltaApplier] refuses a reply that lies);
 * 3. RULES can never disappear (applier rule, not a hope);
 * 4. the view the cut leaves is **strictly smaller** than the view it replaced — measured, not
 *    assumed. A cut that cannot prove it is rolled back and reported as [SkipReason.NotesRejected].
 *
 * The kept view is always: the opening messages (system prompt + first user message — ctxkeeper
 * keeps them verbatim because the task lives there), the pinned notes, then the newest turns.
 *
 * Pure except for [notesWriter], the one callback that talks to the model: the JVM tests hand it a
 * scripted writer, so the whole policy runs on arm64 without a server.
 */
class ContextKeeper(
    private val budget: Int = DEFAULT_BUDGET,
    /** Tokens kept free beside the budget for the reply that will be written. */
    private val replyReserve: Int = DEFAULT_REPLY_RESERVE,
    /** Slack for a low estimate: the trigger fires *before* the view truly reaches the budget. */
    private val headroom: Int = DEFAULT_HEADROOM,
    private val notesWriter: NotesWriter? = null,
    private val tokens: TokenEstimate = TokenEstimate(),
) {
    /** What the model was last shown for this conversation, as an estimate. */
    var lastViewTokens: Int = 0
        private set

    /** How many cuts ran and succeeded — the status line the screen can show. */
    var cuts: Int = 0
        private set

    /** The last cut that ran, for the screen's "why is my thread short?" affordance. */
    var lastReport: CutReport? = null
        private set

    private var notes: Notes = Notes.EMPTY
    private var archivedThrough: Int = -1
    private val archive = TurnArchive()

    fun archivedTurns(): List<ContextTurn> = archive.all()

    /** Search what the cuts moved out of view (the `recall` the model would get). */
    fun recall(pattern: String, limit: Int = 20): List<String> = archive.search(pattern, limit)

    /** The notes as the view shows them; empty until the first cut. */
    fun currentNotes(): Notes = notes

    /**
     * Fold the server's own token count for the view we last showed into the ratio. The screen calls
     * this when a finished turn reports `tokensIn` and the daemon echoed the same view back.
     */
    fun observeUsage(promptTokens: Long) {
        // Reconstruct the chars of the last shown view approximately: the estimate is the best we have.
        tokens.observe(lastViewChars, promptTokens)
    }

    private var lastViewChars: Int = 0

    /**
     * The view the model should see for [conversation]: the opening turns, the pinned notes when
     * there are any, and the newest turns — bounded so the whole view stays inside the budget.
     *
     * Never calls the model; if the conversation does not fit, this is still a *safe* (truncated)
     * view. The real cut — the one that archives and rewrites notes — is [maybeCompress], which the
     * caller runs when it can accept the cost.
     */
    fun viewFor(conversation: List<ContextTurn>): List<ContextTurn> {
        if (conversation.isEmpty()) return emptyList()
        val head = openingOf(conversation)
        val notesTurn = notesTurn()
        val body = conversation.drop(head.size)
        // The newest turns, newest first, until the view fits; at least one stays.
        val kept = ArrayList<ContextTurn>()
        var cost = viewCost(head, notesTurn())
        for (turn in body.asReversed()) {
            val extra = tokens.estimate(turn.text)
            if (kept.isNotEmpty() && cost + extra > effectiveBudget) break
            kept += turn
            cost += extra
        }
        kept.reverse()
        val view = head + listOfNotNull(notesTurn) + kept
        lastViewChars = head.sumOf { it.text.length } + kept.sumOf { it.text.length } +
            (notesTurn?.text?.length ?: 0)
        lastViewTokens = view.sumOf { tokens.estimate(it.text) }
        return view
    }

    /**
     * Cut the conversation down if it overruns the budget.
     *
     * [notesWriter] (the constructor's, or [maybeCompress]'s override) is asked, with the turns that
     * are about to leave, for a changes-only notes delta; the delta is applied with the safety rules;
     * the turns are archived; and the resulting view is **measured against the original** — a cut
     * that does not strictly shrink the view is rolled back. The caller shows [KeepOutcome] honestly:
     * `Fits` means nothing was needed, `NotesRejected` means the cut was refused and the conversation
     * still needs the user's attention (a long turn with an uncooperative model).
     */
    fun maybeCompress(conversation: List<ContextTurn>, writer: NotesWriter? = notesWriter): KeepOutcome {
        val effective = writer ?: notesWriter
        if (conversation.isEmpty() || !needsCompression(conversation)) {
            // Still show the view: the estimate of what the model would read is worth keeping fresh.
            viewFor(conversation)
            return KeepOutcome(SkipReason.Fits)
        }
        val head = openingOf(conversation)
        val body = conversation.drop(head.size)

        // The cut point: everything in `body` except the newest exchange moves out. The newest user
        // message and everything after it is the exchange the model is working on — ctxkeeper keeps
        // the newest exchange verbatim because that is where the task currently lives.
        val cutAt = newestExchangeStart(body)
        if (cutAt <= 0) return KeepOutcome(SkipReason.TooShort)
        val leaving = body.subList(0, cutAt)
        if (leaving.isEmpty()) return KeepOutcome(SkipReason.TooShort)

        val beforeTokens = viewTokens(conversation)

        // 1. Ask for the notes *before* anything leaves — the model writes what the turns mean while
        //    it can still read them. A refused layout aborts the whole cut, and a keeper with no
        //    way to ask the model never compresses: the thread grows and the screen says so.
        if (effective == null) return KeepOutcome(SkipReason.NotesRejected)
        val delta = effective.requestDelta(notes, leaving)
            ?: return KeepOutcome(SkipReason.NotesRejected)
        val applied = when (val o = NotesDeltaApplier.apply(notes, delta)) {
            is NotesDeltaApplier.Outcome.Applied -> o.notes
            is NotesDeltaApplier.Outcome.Rejected -> return KeepOutcome(SkipReason.NotesRejected)
        }

        // 2. Archive verbatim first: the cut may still be rolled back, and an archive that lost a
        //    turn is worse than no archive at all (ctxkeeper's Archive.add is idempotent by number
        //    for exactly this reason).
        val firstNumber = archivedThrough + 1
        val renumbered = leaving.mapIndexed { i, t -> t.copy(number = firstNumber + i) }
        archive.archive(renumbered)
        val oldNotes = notes
        val oldArchivedThrough = archivedThrough
        notes = applied
        archivedThrough = firstNumber + renumbered.size - 1

        // 3. Measure. The kept view: head, pinned notes, the newest exchange. Strictly smaller, or
        //    the cut did not happen.
        val headCharsBefore = head.sumOf { it.text.length }
        val notesCharsBefore = oldNotes.let { if (it.isEmpty) 0 else it.render().length }
        val leavingChars = leaving.sumOf { it.text.length }
        val newViewChars = headCharsBefore + notes.render().length +
            body.subList(cutAt, body.size).sumOf { it.text.length } + ARCHIVE_NOTICE_CHARS
        val afterTokens = estimateView(head, notes, body.subList(cutAt, body.size))

        if (afterTokens >= beforeTokens) {
            // Roll everything back — no partial cut, no half-archived conversation.
            notes = oldNotes
            archivedThrough = oldArchivedThrough
            rollbackArchive(renumbered)
            return KeepOutcome(SkipReason.NotesRejected)
        }
        // The gate's other half: every turn that left is in the archive, verbatim. This cannot
        // fail after step 2 except by a bug — but the app must never *trust* that, so it checks.
        val missing = renumbered.any { archive.turnAt(it.number)?.text != it.text }
        if (missing) {
            notes = oldNotes
            archivedThrough = oldArchivedThrough
            rollbackArchive(renumbered)
            return KeepOutcome(SkipReason.NotesRejected)
        }
        // 4. Only now: the notes must carry the dropped material's floor. Not "is the summary good"
        //    (unknowable), but "is it not absurdly smaller than what it replaces": a notes block
        //    that collapses while 20k chars leave is a broken keeper, and the user hears about it.
        if (leavingChars > NOTES_FLOOR_TRIGGER_CHARS && notes.render().length < NOTES_FLOOR_MIN_CHARS) {
            notes = oldNotes
            archivedThrough = oldArchivedThrough
            rollbackArchive(renumbered)
            return KeepOutcome(SkipReason.NotesRejected)
        }

        cuts += 1
        val report = CutReport(
            beforeTokens = beforeTokens,
            afterTokens = afterTokens,
            droppedChars = leavingChars + notesCharsBefore,
            removedTurns = renumbered.size,
            archivedThrough = archivedThrough,
        )
        lastReport = report
        viewFor(conversation)
        return KeepOutcome(SkipReason.Cut, report)
    }

    /**
     * True when the whole conversation would not fit the budget: the screen's signal to call
     * [maybeCompress] (or show "this thread is long" and offer it).
     */
    fun needsCompression(conversation: List<ContextTurn>): Boolean =
        conversation.isNotEmpty() && viewTokens(conversation) > effectiveBudget

    private val effectiveBudget: Int get() = (budget - replyReserve - headroom).coerceAtLeast(MIN_EFFECTIVE_BUDGET)

    private fun viewTokens(conversation: List<ContextTurn>): Int = conversation.sumOf { tokens.estimate(it.text) }

    /** What the fixed part of a view costs: the opening turns plus the pinned notes, when there are notes. */
    private fun viewCost(head: List<ContextTurn>, notesTurn: ContextTurn?): Int =
        tokens.estimateAll((head + listOfNotNull(notesTurn)).map { it.text })

    private fun estimateView(
        head: List<ContextTurn>,
        notes: Notes,
        kept: List<ContextTurn>,
    ): Int {
        val notesText = if (notes.isEmpty) null else notes.pinned(archive.size)
        val parts = head.map { it.text } + kept.map { it.text } + listOfNotNull(notesText)
        // estimateAll on both sides of the gate: the kept view's real cost (message overhead included,
        // since it is the cost the model actually reads), and the same function on the before side, so
        // the strictly-smaller decision never hangs on the ±8 chars a turn's metadata is worth.
        return tokens.estimateAll(parts)
    }

    /** The opening messages: the system turns and the first user message — kept verbatim, always. */
    private fun openingOf(conversation: List<ContextTurn>): List<ContextTurn> {
        var i = 0
        while (i < conversation.size && conversation[i].role == ContextTurn.Role.System) i++
        if (i < conversation.size && conversation[i].isUser) i++
        return conversation.subList(0, i)
    }

    /**
     * Index in [body] where the newest exchange starts.
     *
     * The cut boundary must never split an exchange: the newest *user* message and everything after
     * it (the bot's whole answer, tool turns included) always stays whole — an answer whose question
     * was archived is how a compressed thread starts reading as nonsense. With no user message left
     * in the body (a long tool run after the last question), only the last turn is the exchange and
     * everything before it is cuttable.
     */
    private fun newestExchangeStart(body: List<ContextTurn>): Int {
        for (i in body.indices.reversed()) {
            if (body[i].isUser) return i
        }
        return (body.size - 1).coerceAtLeast(0)
    }

    private fun notesTurn(): ContextTurn? =
        if (notes.isEmpty) null else ContextTurn(ARCHIVED_NOTES_NUMBER, ContextTurn.Role.System, notes.pinned(archive.size))

    private fun rollbackArchive(turns: List<ContextTurn>) {
        // The archive is append-only for everyone else; a rolled-back cut removes exactly the tail
        // it had just appended, so removal is the archive's internal door, not a public one.
        archive.removeTail(turns.size)
    }

    companion object {
        /** The default view the app asks for: ctxkeeper's `--budget` for a served chat model. */
        const val DEFAULT_BUDGET = 8192

        /** Room for the answer (ctxkeeper's `reply_reserve`). */
        const val DEFAULT_REPLY_RESERVE = 2048
        const val DEFAULT_HEADROOM = 1024
        private const val MIN_EFFECTIVE_BUDGET = 512

        /** Slack in the kept view for the "N earlier messages are in the archive" notice. */
        private const val ARCHIVE_NOTICE_CHARS = 80

        /**
         * When a cut moves this many characters out and the new notes are under [NOTES_FLOOR_MIN_CHARS],
         * the notes collapsed — refuse the cut rather than show a thread that forgot its task.
         */
        private const val NOTES_FLOOR_TRIGGER_CHARS = 4000
        private const val NOTES_FLOOR_MIN_CHARS = 120

        /** The slot number the pinned notes carry (never collides with conversation numbers, which start at 0). */
        private const val ARCHIVED_NOTES_NUMBER = -1
    }
}

/** The one callback that talks to the model: write the changes-only delta for the turns leaving. */
fun interface NotesWriter {
    /**
     * Ask the model for a [NotesDelta] covering [leaving]; null when the reply does not use the
     * layout or the writer failed — the caller treats both as [SkipReason.NotesRejected] and rolls
     * the cut back.
     */
    fun requestDelta(current: Notes, leaving: List<ContextTurn>): NotesDelta?
}
