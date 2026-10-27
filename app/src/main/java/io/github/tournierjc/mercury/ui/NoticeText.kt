package io.github.tournierjc.mercury.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.tournierjc.mercury.R
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
    NoticeKey.SESSION_EXPIRED -> R.string.notice_session_expired
    NoticeKey.REQUEST_REFUSED -> R.string.notice_request_refused
}

/** The sentence, then the server's own words when it sent any. */
@Composable
fun noticeText(notice: Notice): String {
    val sentence = stringResource(noticeResId(notice.key), *notice.args.toTypedArray())
    return notice.detail?.let { "$sentence — $it" } ?: sentence
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
