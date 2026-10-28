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

    /** One attachment is over the cap of its kind: `%1$s` its name, `%2$s` the cap in megabytes.
     *  Arguments are strings, so no notice may ask for a number (see `NoticeStringsTest`). */
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

    /** The sign-in itself was refused: wrong password, unknown user. */
    CREDENTIALS_REFUSED,

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
    TEAMMATE_CREATED,
    OUTPUT_DELETED,
    JOB_RESTARTED,
    INCIDENTS_CLEARED,
    /** One field of a teammate's manifest was written. One key per field, like the rest. */
    TEAMMATE_MODEL_SAVED,
    TEAMMATE_MODEL_CLEARED,
    TEAMMATE_DESCRIPTION_SAVED,
    TEAMMATE_RENAMED,
    TEAMMATE_NAME_REFUSED,
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

    // ---- boards: what a finished card or board action says
    BOARD_CREATED,
    BOARD_DELETED,
    BOARD_ACTIVATED,
    BOARD_DEACTIVATED,
    BOARD_DECISION_REQUESTED,
    CARD_ADDED,
    CARD_DELETED,
    CARD_STARTED,
    CARD_HALTED,
    CASCADE_REQUESTED,
    CARDS_ALL_STARTED,
    CARDS_ALL_HALTED,
    SMART_RUN_REQUESTED,
    DONE_CARDS_CLEARED,
    GENERATION_REQUESTED,
    INSTRUCTIONS_SAVED,
    DIRECTORY_SAVED,
    KNOWLEDGE_SAVED,
    VARIABLE_SAVED,
    FEEDBACK_SENT,
    COMMENT_ADDED,
    COMMENT_DELETED,
    BOARD_ATTACHMENT_ADDED,
    BOARD_ATTACHMENT_REMOVED,
    CARD_LABEL_ADDED,
    CARD_LABEL_REMOVED,
    DEPENDENCY_ADDED,
    DEPENDENCY_REMOVED,
    CARD_PARENT_SET,
    BOARD_WRITE_FAILED,
    NO_APP_FOR_TYPE,

    // ---- console: actions on the daemon, the fleet, git and the terminal
    TASK_CANCELLED,
    FINISHED_TASKS_CLEARED,
    AGENTS_HALTED,
    AGENTS_STOPPED,
    SCHEDULE_DELETED,
    SKILL_ENABLED,
    SKILL_DISABLED,
    SKILL_INSTALLED_FROM_URL,
    SKILL_INSTALLED_FROM_REGISTRY,
    SKILL_DELETED,
    BOTS_ALL_STARTED,
    BOTS_ALL_STOPPED,
    TEAMMATE_ENABLED,
    TEAMMATE_DISABLED,
    TEAMMATE_DELETED_NAMED,
    DAEMON_RESTART_REQUESTED,
    SHUTDOWN_ACCEPTED,
    SHUTDOWN_REFUSED,
    CREW_IMPORT_NOTHING,
    CREW_IMPORTED,
    TIER_APPLIED,
    FILE_STAGED,
    FILES_STAGED,
    FILE_UNSTAGED,
    FILES_UNSTAGED,
    COMMIT_CREATED,
    PUSHED,
    PULLED,
    BRANCH_CHECKED_OUT,

    // ---- git porcelain codes, as the console card shows them
    GIT_MODIFIED,
    GIT_ADDED,
    GIT_DELETED,
    GIT_RENAMED,
    GIT_COPIED,
    GIT_UNMERGED,
    GIT_UNTRACKED,
    GIT_IGNORED,
    GIT_STAGED,
    GIT_UNSTAGED,
    GIT_OTHER,
    GIT_CLEAN,
    GIT_UNAVAILABLE,
    GIT_SUMMARY_UNSTAGED,
    GIT_SUMMARY_STAGED,
    GIT_SUMMARY_BOTH,

    // ---- texts the notifications and the fleet inbox need
    ATTENTION_NEEDS_YOU,
    ATTENTION_FINISHED,
    ATTENTION_FAILED,
    ATTENTION_NEEDS_YOU_BODY,
    ATTENTION_FINISHED_BODY,
    ATTENTION_FAILED_BODY,
    CREW_NONE,
    CREW_ONE,
    CREW_MANY,
    CREW_WAITING_NONE,
    CREW_WAITING_ONE,
    CREW_WAITING_MANY,
    AGE_NOW,
    AGE_MINUTES,
    AGE_HOURS,
    AGE_DAYS,
    OUTPUT_FINAL,
    OUTPUT_DRAFT,
    RUN_SUCCEEDED,
    RUN_FAILED_STATE,
    RUN_CANCELLED_STATE,
    RUN_RUNNING_STATE,
    RUN_QUEUED_STATE,
    UNKNOWN_CODE,
    SIZE_BYTES,
    SIZE_KB,
    SIZE_MB,
    REASON_NOT_FOUND,
    REASON_PROVIDER_RATE_LIMIT,
    REASON_PROVIDER_ERROR,
    REASON_TIMEOUT,
    REASON_CANCELLED,
    REASON_BUDGET_EXHAUSTED,
    TOOL_FALLBACK,
    ATTEMPTS_ONE,
    ATTEMPTS_MANY,
    OUTPUT_IMAGE_TOO_HEAVY,
    OUTPUT_PREVIEW_UNAVAILABLE_FORMAT,
    OUTPUT_PREVIEW_UNAVAILABLE,
    GIT_AHEAD,
    GIT_BEHIND,
    GIT_UP_TO_DATE,
    BOT_STATE_PAUSED,
    BOT_STATE_DISABLED,
    BOT_STATE_RESTING,
    SPOTIFY_COMPUTER,
    SPOTIFY_PHONE,
    SPOTIFY_SPEAKER,
    SPOTIFY_TV,
    SPOTIFY_TABLET,
    SPOTIFY_CONSOLE,
    SPOTIFY_CAST,
    SPOTIFY_CAR,
    SPOTIFY_DEVICE,
    SPOTIFY_UNKNOWN_TITLE,
    SPOTIFY_PREMIUM,
    SPOTIFY_FREE,
    SPOTIFY_OTHER,
    TOKENS_IN,
    TOKENS_OUT,
    RUN_TOOL_CALLS_ONE,
    RUN_TOOL_CALLS_MANY,
    RUN_ERROR_LINE,
    OPENUI_EMPTY,
    OPENUI_TOO_LONG,
    OPENUI_TREE,
    OPENUI_DUPLICATE_ID,
    OPENUI_UNREFERENCED,
    OPENUI_ROWS,
    OPENUI_STRING_LIST,
    OPENUI_NUMBER_LIST,
    OPENUI_NUMBER,
    OPENUI_POSITIVE,
    OPENUI_BOOLEAN,
    OPENUI_CODE_LONG,
    OPENUI_STRING,
    OPENUI_STRING_LONG,
    OPENUI_LABELS_VALUES,
    OPENUI_NEGATIVE_SHARE,
    OPENUI_ZERO_SHARES,
    OPENUI_LINE_EXPECT,
    OPENUI_LINE_BAD_ID,
    OPENUI_LINE_UNKNOWN_COMPONENT,
    OPENUI_LINE_ID_LIST,
    OPENUI_LINE_PROPS_OBJECT,
    OPENUI_TOO_MANY_COMPONENTS,
    OPENUI_NO_ROOT,
    OPENUI_DANGLING_CHILD,
    OPENUI_CYCLE,
    OPENUI_LINE_FORBIDDEN_PROP,
    OPENUI_LINE_NOT_A_PROP,
    OPENUI_LINE_BAD_KIND,
    OPENUI_LINE_UNKNOWN_TONE,
    OPENUI_LINE_UNKNOWN_VARIANT,
    OPENUI_LINE_TOO_MANY_SHARES,
    PERMISSION_MODE_QUESTION,
    TERMINAL_REQUEST_FAILED,
    TERMINAL_EXIT_CODE,
    TERMINAL_OK,
    CODE_MODE_SET,
    WORKSPACE_SET,
    CREW_IMPORT_FAILED,

}

/**
 * The shape of a failure as the network layer raises it. One place rather than one `readable()`
 * per ViewModel: the six copies drifted, and each of them spelled its French in place.
 */
fun Throwable.toNotice(): Notice = when (this) {
    // Ordered: the refusal happens *while* signing in, so it must not say "sign in again".
    is CredentialsRefusedException -> Notice(NoticeKey.CREDENTIALS_REFUSED)
    is AuthException -> Notice(NoticeKey.SESSION_EXPIRED)
    else -> Notice(NoticeKey.REQUEST_REFUSED).withDetail(message)
}
