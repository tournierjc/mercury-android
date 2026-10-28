package io.github.tournierjc.mercury.data

/** How loud a [Notice] is. The UI picks the colour from this, never from the wording. */
enum class NoticeSeverity { Info, Warning, Error }

/**
 * A message that has not been turned into words yet.
 *
 * A ViewModel reports a failure with a [key] and its arguments; the sentence is resolved at the
 * edge, in a composable, so the app can say the same thing in the language the owner reads. The
 * only free text a notice may carry is [detail] — what the server itself answered. Nothing here
 * belongs to a screen: no colour, no layout, no punctuation.
 */
data class Notice(
    val key: NoticeKey,
    val args: List<String> = emptyList(),
    val detail: String? = null,
    val severity: NoticeSeverity = NoticeSeverity.Error,
) {
    /** The server's own words, appended after the house sentence. Blank or null is dropped. */
    fun withDetail(text: String?): Notice =
        copy(detail = text?.trim()?.takeIf { it.isNotEmpty() })

    fun asInfo(): Notice = copy(severity = NoticeSeverity.Info)

    fun asWarning(): Notice = copy(severity = NoticeSeverity.Warning)
}

/**
 * Every message the shell can raise. Each key has a `notice_<name>` string in `values/` and
 * `values-en/`, with the same positional arguments — a unit test in `:app` fails otherwise.
 */
enum class NoticeKey {
    /** The daemon could not be reached at the address in use. */
    CONNECT_FAILED,

    /** A turn of the conversation did not leave: the server refused or the link dropped. */
    SEND_FAILED,

    /** The stream of an answer broke mid-turn; `detail` carries what the daemon said. */
    STREAM_FAILED,

    /** The bot was asked to answer and declined. */
    REPLY_REFUSED,

    /** The conversation history could not be read back. */
    HISTORY_UNAVAILABLE,

    /** A conversation could not be deleted. */
    DELETE_FAILED,

    /** More files were attached than the composer accepts. */
    ATTACHMENTS_LIMIT,

    /** One attachment is over the cap of its kind: `%1$s` its name, `%2$d` the cap in megabytes. */
    ATTACHMENT_TOO_LARGE,

    /** The daemon warned about the turn (a loop, a truncated tool) without stopping it. */
    SERVER_WARNING,

    /** A turn was accepted for a teammate and queued behind the current one. */
    DISPATCH_QUEUED,

    /** A teammate declined the turn: saturated queue, paused bot, permissions. */
    DISPATCH_REFUSED,

    /** A teammate cannot be created (or renamed) without a name. */
    NAME_REQUIRED,

    /** A finished run was asked to run again and the daemon declined. */
    RESTART_REFUSED,

    /** The session token is no longer accepted: sign in again. */
    SESSION_EXPIRED,

    /** The request failed for a reason only the server can name; `detail` carries it. */
    REQUEST_REFUSED,

    // ---- what a finished action says, in the app's vocabulary: a bot is an « équipier »
    CREW_PHOTO_SAVED,
    CREW_PHOTO_REMOVED,
    PERSONA_SAVED,
    PROMOTED_LEAD,
    CREW_AUTOBUILD_REQUESTED,
    CREW_MEMBER_ADDED,
    BUNDLE_IMPORTED,
    CREW_MEMBER_ADOPTED,
    CREW_MEMBER_DETACHED,
    BOT_STARTED,
    BOT_STOPPED,
    RUN_REQUESTED,
    TEAMMATE_DELETED,
    TEAMMATE_CREATED,
    OUTPUT_DELETED,
    JOB_RESTARTED,
    INCIDENTS_CLEARED,
    /** The teammate was enabled or disabled. */
    TEAMMATE_ENABLED_TOGGLED,
    /** One field of a teammate's manifest was written. One key per field, like the rest. */
    TEAMMATE_MODEL_SAVED,
    TEAMMATE_MODEL_CLEARED,
    TEAMMATE_DESCRIPTION_SAVED,
    TEAMMATE_BUDGET_SAVED,
    MEMORY_ADDED,
    MEMORY_FORGOTTEN,
    MEMORY_UPDATED,
    MODEL_ACTIVE,
    PROVIDER_SAVED,
    LABEL_SAVED,
    PASSWORD_CHANGED,
    USERNAME_CHANGED,
    SESSION_REVOKED,
    BELIEF_CONFIRMED,
    BELIEF_FORGOTTEN,
    BELIEF_CORRECTED,
    MEMORY_RESTORED,
    BYPASS_APPROVED,
    BYPASS_ASKED,

}

/**
 * The shape of a failure as the network layer raises it. One place rather than one `readable()`
 * per ViewModel: the six copies drifted, and each of them spelled its French in place.
 */
fun Throwable.toNotice(): Notice = when (this) {
    is AuthException -> Notice(NoticeKey.SESSION_EXPIRED)
    else -> Notice(NoticeKey.REQUEST_REFUSED).withDetail(message)
}
