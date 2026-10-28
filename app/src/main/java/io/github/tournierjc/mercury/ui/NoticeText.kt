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
    NoticeKey.TEAMMATE_DELETED -> R.string.notice_teammate_deleted
    NoticeKey.TEAMMATE_CREATED -> R.string.notice_teammate_created
    NoticeKey.OUTPUT_DELETED -> R.string.notice_output_deleted
    NoticeKey.JOB_RESTARTED -> R.string.notice_job_restarted
    NoticeKey.INCIDENTS_CLEARED -> R.string.notice_incidents_cleared
    NoticeKey.TEAMMATE_ENABLED_TOGGLED -> R.string.notice_teammate_enabled_toggled
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
