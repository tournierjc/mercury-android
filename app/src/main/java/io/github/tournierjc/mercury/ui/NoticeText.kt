package io.github.tournierjc.mercury.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.tournierjc.mercury.R
import android.content.Context
import io.github.tournierjc.mercury.data.AttachmentProblem
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.NoticeSeverity
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * The only place a [Notice] becomes words. A key with no string here is a compile error; a key whose
 * FR and EN strings disagree on arguments is a test failure (`NoticeStringsTest`).
 */
@StringRes
fun noticeResId(key: NoticeKey): Int = when (key) {
    NoticeKey.CONNECT_FAILED -> R.string.notice_connect_failed
    NoticeKey.SEND_FAILED -> R.string.notice_send_failed
    NoticeKey.STREAM_FAILED -> R.string.notice_stream_failed
    NoticeKey.REPLY_REFUSED -> R.string.notice_reply_refused
    NoticeKey.HISTORY_UNAVAILABLE -> R.string.notice_history_unavailable
    NoticeKey.DELETE_FAILED -> R.string.notice_delete_failed
    NoticeKey.ATTACHMENTS_LIMIT -> R.string.notice_attachments_limit
    NoticeKey.DISPATCH_QUEUED -> R.string.notice_dispatch_queued
    NoticeKey.DISPATCH_REFUSED -> R.string.notice_dispatch_refused
    NoticeKey.NAME_REQUIRED -> R.string.notice_name_required
    NoticeKey.RESTART_REFUSED -> R.string.notice_restart_refused
    NoticeKey.ATTACHMENT_TOO_LARGE -> R.string.notice_attachment_too_large
    NoticeKey.SERVER_WARNING -> R.string.notice_server_warning
    NoticeKey.CREDENTIALS_REFUSED -> R.string.notice_credentials_refused
    NoticeKey.SESSION_EXPIRED -> R.string.notice_session_expired
    NoticeKey.REQUEST_REFUSED -> R.string.notice_request_refused
    NoticeKey.CREW_PHOTO_SAVED -> R.string.notice_crew_photo_saved
    NoticeKey.CREW_PHOTO_REMOVED -> R.string.notice_crew_photo_removed
    NoticeKey.PERSONA_SAVED -> R.string.notice_persona_saved
    NoticeKey.PROMOTED_LEAD -> R.string.notice_promoted_lead
    NoticeKey.CREW_AUTOBUILD_REQUESTED -> R.string.notice_crew_autobuild_requested
    NoticeKey.CREW_MEMBER_ADDED -> R.string.notice_crew_member_added
    NoticeKey.BUNDLE_IMPORTED -> R.string.notice_bundle_imported
    NoticeKey.CREW_MEMBER_ADOPTED -> R.string.notice_crew_member_adopted
    NoticeKey.CREW_MEMBER_DETACHED -> R.string.notice_crew_member_detached
    NoticeKey.BOT_STARTED -> R.string.notice_bot_started
    NoticeKey.BOT_STOPPED -> R.string.notice_bot_stopped
    NoticeKey.RUN_REQUESTED -> R.string.notice_run_requested
    NoticeKey.TEAMMATE_CREATED -> R.string.notice_teammate_created
    NoticeKey.OUTPUT_DELETED -> R.string.notice_output_deleted
    NoticeKey.JOB_RESTARTED -> R.string.notice_job_restarted
    NoticeKey.INCIDENTS_CLEARED -> R.string.notice_incidents_cleared
    NoticeKey.TEAMMATE_MODEL_SAVED -> R.string.notice_teammate_model_saved
    NoticeKey.TEAMMATE_MODEL_CLEARED -> R.string.notice_teammate_model_cleared
    NoticeKey.TEAMMATE_DESCRIPTION_SAVED -> R.string.notice_teammate_description_saved
    NoticeKey.TEAMMATE_BUDGET_SAVED -> R.string.notice_teammate_budget_saved
    NoticeKey.MEMORY_ADDED -> R.string.notice_memory_added
    NoticeKey.MEMORY_FORGOTTEN -> R.string.notice_memory_forgotten
    NoticeKey.MEMORY_UPDATED -> R.string.notice_memory_updated
    NoticeKey.MODEL_ACTIVE -> R.string.notice_model_active
    NoticeKey.PROVIDER_SAVED -> R.string.notice_provider_saved
    NoticeKey.LABEL_SAVED -> R.string.notice_label_saved
    NoticeKey.PASSWORD_CHANGED -> R.string.notice_password_changed
    NoticeKey.USERNAME_CHANGED -> R.string.notice_username_changed
    NoticeKey.SESSION_REVOKED -> R.string.notice_session_revoked
    NoticeKey.BELIEF_CONFIRMED -> R.string.notice_belief_confirmed
    NoticeKey.BELIEF_FORGOTTEN -> R.string.notice_belief_forgotten
    NoticeKey.BELIEF_CORRECTED -> R.string.notice_belief_corrected
    NoticeKey.MEMORY_RESTORED -> R.string.notice_memory_restored
    NoticeKey.BYPASS_APPROVED -> R.string.notice_bypass_approved
    NoticeKey.BYPASS_ASKED -> R.string.notice_bypass_asked
    NoticeKey.BOARD_CREATED -> R.string.notice_board_created
    NoticeKey.BOARD_DELETED -> R.string.notice_board_deleted
    NoticeKey.BOARD_ACTIVATED -> R.string.notice_board_activated
    NoticeKey.BOARD_DEACTIVATED -> R.string.notice_board_deactivated
    NoticeKey.BOARD_DECISION_REQUESTED -> R.string.notice_board_decision_requested
    NoticeKey.CARD_ADDED -> R.string.notice_card_added
    NoticeKey.CARD_DELETED -> R.string.notice_card_deleted
    NoticeKey.CARD_STARTED -> R.string.notice_card_started
    NoticeKey.CARD_HALTED -> R.string.notice_card_halted
    NoticeKey.CASCADE_REQUESTED -> R.string.notice_cascade_requested
    NoticeKey.CARDS_ALL_STARTED -> R.string.notice_cards_all_started
    NoticeKey.CARDS_ALL_HALTED -> R.string.notice_cards_all_halted
    NoticeKey.SMART_RUN_REQUESTED -> R.string.notice_smart_run_requested
    NoticeKey.DONE_CARDS_CLEARED -> R.string.notice_done_cards_cleared
    NoticeKey.GENERATION_REQUESTED -> R.string.notice_generation_requested
    NoticeKey.INSTRUCTIONS_SAVED -> R.string.notice_instructions_saved
    NoticeKey.DIRECTORY_SAVED -> R.string.notice_directory_saved
    NoticeKey.KNOWLEDGE_SAVED -> R.string.notice_knowledge_saved
    NoticeKey.VARIABLE_SAVED -> R.string.notice_variable_saved
    NoticeKey.FEEDBACK_SENT -> R.string.notice_feedback_sent
    NoticeKey.COMMENT_ADDED -> R.string.notice_comment_added
    NoticeKey.COMMENT_DELETED -> R.string.notice_comment_deleted
    NoticeKey.BOARD_ATTACHMENT_ADDED -> R.string.notice_board_attachment_added
    NoticeKey.BOARD_ATTACHMENT_REMOVED -> R.string.notice_board_attachment_removed
    NoticeKey.CARD_LABEL_ADDED -> R.string.notice_card_label_added
    NoticeKey.CARD_LABEL_REMOVED -> R.string.notice_card_label_removed
    NoticeKey.DEPENDENCY_ADDED -> R.string.notice_dependency_added
    NoticeKey.DEPENDENCY_REMOVED -> R.string.notice_dependency_removed
    NoticeKey.CARD_PARENT_SET -> R.string.notice_card_parent_set
    NoticeKey.BOARD_WRITE_FAILED -> R.string.notice_board_write_failed
    NoticeKey.NO_APP_FOR_TYPE -> R.string.notice_no_app_for_type
    NoticeKey.TASK_CANCELLED -> R.string.notice_task_cancelled
    NoticeKey.FINISHED_TASKS_CLEARED -> R.string.notice_finished_tasks_cleared
    NoticeKey.AGENTS_HALTED -> R.string.notice_agents_halted
    NoticeKey.AGENTS_STOPPED -> R.string.notice_agents_stopped
    NoticeKey.SCHEDULE_DELETED -> R.string.notice_schedule_deleted
    NoticeKey.SKILL_ENABLED -> R.string.notice_skill_enabled
    NoticeKey.SKILL_DISABLED -> R.string.notice_skill_disabled
    NoticeKey.SKILL_INSTALLED_FROM_URL -> R.string.notice_skill_installed_from_url
    NoticeKey.SKILL_INSTALLED_FROM_REGISTRY -> R.string.notice_skill_installed_from_registry
    NoticeKey.SKILL_DELETED -> R.string.notice_skill_deleted
    NoticeKey.BOTS_ALL_STARTED -> R.string.notice_bots_all_started
    NoticeKey.BOTS_ALL_STOPPED -> R.string.notice_bots_all_stopped
    NoticeKey.TEAMMATE_ENABLED -> R.string.notice_teammate_enabled
    NoticeKey.TEAMMATE_DISABLED -> R.string.notice_teammate_disabled
    NoticeKey.TEAMMATE_DELETED_NAMED -> R.string.notice_teammate_deleted_named
    NoticeKey.DAEMON_RESTART_REQUESTED -> R.string.notice_daemon_restart_requested
    NoticeKey.SHUTDOWN_ACCEPTED -> R.string.notice_shutdown_accepted
    NoticeKey.SHUTDOWN_REFUSED -> R.string.notice_shutdown_refused
    NoticeKey.CREW_IMPORT_NOTHING -> R.string.notice_crew_import_nothing
    NoticeKey.CREW_IMPORTED -> R.string.notice_crew_imported
    NoticeKey.TIER_APPLIED -> R.string.notice_tier_applied
    NoticeKey.FILE_STAGED -> R.string.notice_file_staged
    NoticeKey.FILES_STAGED -> R.string.notice_files_staged
    NoticeKey.FILE_UNSTAGED -> R.string.notice_file_unstaged
    NoticeKey.FILES_UNSTAGED -> R.string.notice_files_unstaged
    NoticeKey.COMMIT_CREATED -> R.string.notice_commit_created
    NoticeKey.PUSHED -> R.string.notice_pushed
    NoticeKey.PULLED -> R.string.notice_pulled
    NoticeKey.BRANCH_CHECKED_OUT -> R.string.notice_branch_checked_out

    NoticeKey.TERMINAL_REQUEST_FAILED -> R.string.notice_terminal_request_failed
    NoticeKey.TERMINAL_EXIT_CODE -> R.string.notice_terminal_exit_code
    NoticeKey.TERMINAL_OK -> R.string.notice_terminal_ok
    NoticeKey.CODE_MODE_SET -> R.string.notice_code_mode_set
    NoticeKey.WORKSPACE_SET -> R.string.notice_workspace_set
    NoticeKey.CREW_IMPORT_FAILED -> R.string.notice_crew_import_failed



    NoticeKey.PREVIEW_TRUNCATED -> R.string.notice_preview_truncated


}

/** The sentence, then the server's own words when it sent any. */
@Composable
fun noticeText(notice: Notice): String = noticeText(LocalContext.current, notice)

/**
 * The same words outside a composable — a picker callback, a notification body. One resolver, so
 * the wording cannot drift between the screen and the service that speaks for it.
 */
fun noticeText(context: Context, notice: Notice): String {
    val sentence = context.getString(noticeResId(notice.key), *notice.args.toTypedArray())
    return notice.detail?.let { "$sentence — $it" } ?: sentence
}

/** The notice for a refused batch of files, or null when the batch is fine. */
fun attachmentNotice(problem: AttachmentProblem?): Notice? = when (problem) {
    null -> null
    AttachmentProblem.TooMany -> Notice(NoticeKey.ATTACHMENTS_LIMIT)
    is AttachmentProblem.TooLarge -> Notice(
        NoticeKey.ATTACHMENT_TOO_LARGE,
        args = listOf(problem.name, problem.limitMb.toString()),
    )
}

/** A banner for a notice: the words from the key, the colour from the severity. */
@Composable
fun NoticeBanner(notice: Notice, onDismiss: () -> Unit) {
    InlineBanner(
        message = noticeText(notice),
        isError = notice.severity == NoticeSeverity.Error,
        onDismiss = onDismiss,
        tint = noticeTint(notice.severity),
    )
}

/** Severity decides the colour; nothing else in a notice does. */
@Composable
fun noticeTint(severity: NoticeSeverity): Color {
    val colors = LocalMercuryColors.current
    return when (severity) {
        NoticeSeverity.Error -> colors.danger
        NoticeSeverity.Warning -> colors.accent
        NoticeSeverity.Info -> colors.textSecondary
    }
}
