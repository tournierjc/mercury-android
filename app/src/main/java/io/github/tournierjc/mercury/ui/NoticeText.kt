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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
    NoticeKey.TEAMMATE_RENAMED -> R.string.notice_teammate_renamed
    NoticeKey.TEAMMATE_NAME_REFUSED -> R.string.notice_teammate_name_refused
    NoticeKey.TEAMMATE_BUDGET_SAVED -> R.string.notice_teammate_budget_saved
    NoticeKey.MEMORY_ADDED -> R.string.notice_memory_added
    NoticeKey.MEMORY_FORGOTTEN -> R.string.notice_memory_forgotten
    NoticeKey.MEMORY_UPDATED -> R.string.notice_memory_updated
    NoticeKey.GOAL_ADDED -> R.string.notice_goal_added
    NoticeKey.GOAL_FORGOTTEN -> R.string.notice_goal_forgotten
    NoticeKey.PROJECT_ADDED -> R.string.notice_project_added
    NoticeKey.PROJECT_FORGOTTEN -> R.string.notice_project_forgotten
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
    NoticeKey.MUSIC_ACTION_FAILED -> R.string.notice_music_action_failed
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
    NoticeKey.GIT_MODIFIED -> R.string.notice_git_modified
    NoticeKey.GIT_ADDED -> R.string.notice_git_added
    NoticeKey.GIT_DELETED -> R.string.notice_git_deleted
    NoticeKey.GIT_RENAMED -> R.string.notice_git_renamed
    NoticeKey.GIT_COPIED -> R.string.notice_git_copied
    NoticeKey.GIT_UNMERGED -> R.string.notice_git_unmerged
    NoticeKey.GIT_UNTRACKED -> R.string.notice_git_untracked
    NoticeKey.GIT_IGNORED -> R.string.notice_git_ignored
    NoticeKey.GIT_STAGED -> R.string.notice_git_staged
    NoticeKey.GIT_UNSTAGED -> R.string.notice_git_unstaged
    NoticeKey.GIT_OTHER -> R.string.notice_git_other
    NoticeKey.GIT_CLEAN -> R.string.notice_git_clean
    NoticeKey.GIT_UNAVAILABLE -> R.string.notice_git_unavailable
    NoticeKey.GIT_SUMMARY_UNSTAGED -> R.string.notice_git_summary_unstaged
    NoticeKey.GIT_SUMMARY_STAGED -> R.string.notice_git_summary_staged
    NoticeKey.GIT_SUMMARY_BOTH -> R.string.notice_git_summary_both
    NoticeKey.ATTENTION_NEEDS_YOU -> R.string.notice_attention_needs_you
    NoticeKey.ATTENTION_FINISHED -> R.string.notice_attention_finished
    NoticeKey.ATTENTION_FAILED -> R.string.notice_attention_failed
    NoticeKey.ATTENTION_NEEDS_YOU_BODY -> R.string.notice_attention_needs_you_body
    NoticeKey.ATTENTION_FINISHED_BODY -> R.string.notice_attention_finished_body
    NoticeKey.ATTENTION_FAILED_BODY -> R.string.notice_attention_failed_body
    NoticeKey.CREW_NONE -> R.string.notice_crew_none
    NoticeKey.CREW_ONE -> R.string.notice_crew_one
    NoticeKey.CREW_MANY -> R.string.notice_crew_many
    NoticeKey.CREW_WAITING_NONE -> R.string.notice_crew_waiting_none
    NoticeKey.CREW_WAITING_ONE -> R.string.notice_crew_waiting_one
    NoticeKey.CREW_WAITING_MANY -> R.string.notice_crew_waiting_many
    NoticeKey.AGE_NOW -> R.string.notice_age_now
    NoticeKey.AGE_MINUTES -> R.string.notice_age_minutes
    NoticeKey.AGE_HOURS -> R.string.notice_age_hours
    NoticeKey.AGE_DAYS -> R.string.notice_age_days
    NoticeKey.OUTPUT_FINAL -> R.string.notice_output_final
    NoticeKey.OUTPUT_DRAFT -> R.string.notice_output_draft
    NoticeKey.RUN_SUCCEEDED -> R.string.notice_run_succeeded
    NoticeKey.RUN_FAILED_STATE -> R.string.notice_run_failed_state
    NoticeKey.RUN_CANCELLED_STATE -> R.string.notice_run_cancelled_state
    NoticeKey.RUN_RUNNING_STATE -> R.string.notice_run_running_state
    NoticeKey.RUN_QUEUED_STATE -> R.string.notice_run_queued_state
    NoticeKey.UNKNOWN_CODE -> R.string.notice_unknown_code
    NoticeKey.SIZE_BYTES -> R.string.notice_size_bytes
    NoticeKey.SIZE_KB -> R.string.notice_size_kb
    NoticeKey.SIZE_MB -> R.string.notice_size_mb

    NoticeKey.REASON_NOT_FOUND -> R.string.notice_reason_not_found
    NoticeKey.REASON_PROVIDER_RATE_LIMIT -> R.string.notice_reason_provider_rate_limit
    NoticeKey.REASON_PROVIDER_ERROR -> R.string.notice_reason_provider_error
    NoticeKey.REASON_TIMEOUT -> R.string.notice_reason_timeout
    NoticeKey.REASON_CANCELLED -> R.string.notice_reason_cancelled
    NoticeKey.REASON_BUDGET_EXHAUSTED -> R.string.notice_reason_budget_exhausted
    NoticeKey.TOOL_FALLBACK -> R.string.notice_tool_fallback
    NoticeKey.ATTEMPTS_ONE -> R.string.notice_attempts_one
    NoticeKey.ATTEMPTS_MANY -> R.string.notice_attempts_many
    NoticeKey.OUTPUT_IMAGE_TOO_HEAVY -> R.string.notice_output_image_too_heavy
    NoticeKey.OUTPUT_PREVIEW_UNAVAILABLE_FORMAT -> R.string.notice_output_preview_unavailable_format
    NoticeKey.OUTPUT_PREVIEW_UNAVAILABLE -> R.string.notice_output_preview_unavailable
    NoticeKey.GIT_AHEAD -> R.string.notice_git_ahead
    NoticeKey.GIT_BEHIND -> R.string.notice_git_behind
    NoticeKey.GIT_UP_TO_DATE -> R.string.notice_git_up_to_date
    NoticeKey.BOT_STATE_PAUSED -> R.string.notice_bot_state_paused
    NoticeKey.BOT_STATE_DISABLED -> R.string.notice_bot_state_disabled
    NoticeKey.BOT_STATE_RESTING -> R.string.notice_bot_state_resting
    NoticeKey.SPOTIFY_COMPUTER -> R.string.notice_spotify_computer
    NoticeKey.SPOTIFY_PHONE -> R.string.notice_spotify_phone
    NoticeKey.SPOTIFY_SPEAKER -> R.string.notice_spotify_speaker
    NoticeKey.SPOTIFY_TV -> R.string.notice_spotify_tv
    NoticeKey.SPOTIFY_TABLET -> R.string.notice_spotify_tablet
    NoticeKey.SPOTIFY_CONSOLE -> R.string.notice_spotify_console
    NoticeKey.SPOTIFY_CAST -> R.string.notice_spotify_cast
    NoticeKey.SPOTIFY_CAR -> R.string.notice_spotify_car
    NoticeKey.SPOTIFY_DEVICE -> R.string.notice_spotify_device
    NoticeKey.SPOTIFY_UNKNOWN_TITLE -> R.string.notice_spotify_unknown_title
    NoticeKey.SPOTIFY_PREMIUM -> R.string.notice_spotify_premium
    NoticeKey.SPOTIFY_FREE -> R.string.notice_spotify_free

    NoticeKey.SPOTIFY_OTHER -> R.string.notice_spotify_other
    NoticeKey.TOKENS_IN -> R.string.notice_tokens_in
    NoticeKey.TOKENS_OUT -> R.string.notice_tokens_out
    NoticeKey.RUN_TOOL_CALLS_ONE -> R.string.notice_run_tool_calls_one
    NoticeKey.RUN_TOOL_CALLS_MANY -> R.string.notice_run_tool_calls_many
    NoticeKey.RUN_ERROR_LINE -> R.string.notice_run_error_line
    NoticeKey.OPENUI_EMPTY -> R.string.notice_openui_empty
    NoticeKey.OPENUI_TOO_LONG -> R.string.notice_openui_too_long
    NoticeKey.OPENUI_TREE -> R.string.notice_openui_tree
    NoticeKey.OPENUI_DUPLICATE_ID -> R.string.notice_openui_duplicate_id
    NoticeKey.OPENUI_UNREFERENCED -> R.string.notice_openui_unreferenced
    NoticeKey.OPENUI_ROWS -> R.string.notice_openui_rows
    NoticeKey.OPENUI_STRING_LIST -> R.string.notice_openui_string_list
    NoticeKey.OPENUI_NUMBER_LIST -> R.string.notice_openui_number_list
    NoticeKey.OPENUI_NUMBER -> R.string.notice_openui_number
    NoticeKey.OPENUI_POSITIVE -> R.string.notice_openui_positive
    NoticeKey.OPENUI_BOOLEAN -> R.string.notice_openui_boolean
    NoticeKey.OPENUI_CODE_LONG -> R.string.notice_openui_code_long
    NoticeKey.OPENUI_STRING -> R.string.notice_openui_string
    NoticeKey.OPENUI_STRING_LONG -> R.string.notice_openui_string_long
    NoticeKey.OPENUI_LABELS_VALUES -> R.string.notice_openui_labels_values
    NoticeKey.OPENUI_NEGATIVE_SHARE -> R.string.notice_openui_negative_share
    NoticeKey.OPENUI_ZERO_SHARES -> R.string.notice_openui_zero_shares
    NoticeKey.OPENUI_LINE_EXPECT -> R.string.notice_openui_line_expect
    NoticeKey.OPENUI_LINE_BAD_ID -> R.string.notice_openui_line_bad_id
    NoticeKey.OPENUI_LINE_UNKNOWN_COMPONENT -> R.string.notice_openui_line_unknown_component
    NoticeKey.OPENUI_LINE_ID_LIST -> R.string.notice_openui_line_id_list
    NoticeKey.OPENUI_LINE_PROPS_OBJECT -> R.string.notice_openui_line_props_object
    NoticeKey.OPENUI_TOO_MANY_COMPONENTS -> R.string.notice_openui_too_many_components
    NoticeKey.OPENUI_NO_ROOT -> R.string.notice_openui_no_root
    NoticeKey.OPENUI_DANGLING_CHILD -> R.string.notice_openui_dangling_child
    NoticeKey.OPENUI_CYCLE -> R.string.notice_openui_cycle
    NoticeKey.OPENUI_LINE_FORBIDDEN_PROP -> R.string.notice_openui_line_forbidden_prop
    NoticeKey.OPENUI_LINE_NOT_A_PROP -> R.string.notice_openui_line_not_a_prop
    NoticeKey.OPENUI_LINE_BAD_KIND -> R.string.notice_openui_line_bad_kind
    NoticeKey.OPENUI_LINE_UNKNOWN_TONE -> R.string.notice_openui_line_unknown_tone
    NoticeKey.OPENUI_LINE_UNKNOWN_VARIANT -> R.string.notice_openui_line_unknown_variant
    NoticeKey.OPENUI_LINE_TOO_MANY_SHARES -> R.string.notice_openui_line_too_many_shares
    NoticeKey.PERMISSION_MODE_QUESTION -> R.string.notice_permission_mode_question











    NoticeKey.TERMINAL_REQUEST_FAILED -> R.string.notice_terminal_request_failed
    NoticeKey.TERMINAL_EXIT_CODE -> R.string.notice_terminal_exit_code
    NoticeKey.TERMINAL_OK -> R.string.notice_terminal_ok
    NoticeKey.CODE_MODE_SET -> R.string.notice_code_mode_set
    NoticeKey.CODE_MODE_FAILED -> R.string.notice_code_mode_failed
    NoticeKey.WORKSPACE_SET -> R.string.notice_workspace_set
    NoticeKey.INSTANCE_SAVED -> R.string.notice_instance_saved
    NoticeKey.INSTANCE_RESTART_REQUIRED -> R.string.notice_instance_restart_required
    NoticeKey.CREW_IMPORT_FAILED -> R.string.notice_crew_import_failed





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

/**
 * The acknowledgement of a dispatch, and nothing more.
 *
 * It used to be a bare sentence in the accent colour carrying an internal job id — "Mis en file
 * (job 01e1077e) — le fil principal continue" — which is a log line shown to the person who just
 * typed, above his own keyboard. The thread already answers: the message appears at once, its state
 * says "en file…", and the phase line follows. What is left to acknowledge is that the send *left*,
 * so: one quiet chip, no identifiers, gone in four seconds.
 *
 * A refusal is not this: it keeps the explicit coloured line, because an owner must read it.
 */
@Composable
fun DispatchAck(notice: Notice, onDone: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(notice) {
        delay(4_000)
        onDone()
    }
    val colors = LocalMercuryColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = noticeText(notice),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}
