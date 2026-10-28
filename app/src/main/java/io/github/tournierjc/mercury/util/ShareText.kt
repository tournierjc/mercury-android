package io.github.tournierjc.mercury.util

import io.github.tournierjc.mercury.R

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Hands a document the daemon produced to whatever the phone can share with.
 *
 * The bot bundle arrives as JSON text (`GET /api/bots/:id/bundle`), so `EXTRA_TEXT` is
 * enough: no FileProvider, no write to app storage, and the receiving app saves the very
 * bytes the server built. A share target that refuses the payload is not an error worth
 * interrupting the screen for.
 */
object ShareText {

    fun bundle(context: Context, fileName: String, json: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, json)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_bundle_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
