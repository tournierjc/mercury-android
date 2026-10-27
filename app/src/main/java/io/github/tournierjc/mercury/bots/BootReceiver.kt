package io.github.tournierjc.mercury.bots

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the watch after a reboot, and says so honestly when it cannot.
 *
 * Android 14+ does not let a `dataSync` foreground service be started from the background, and
 * a boot broadcast is the background — so the attempt is allowed to fail. When it does, the
 * periodic check ([AttentionSchedule]) is re-armed, which is exactly what the owner had before
 * the watch existed: a notification within fifteen minutes rather than within seconds.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val attention = AttentionPrefs(context)
        if (!attention.persistent) return
        if (WatchService.start(context)) return
        AttentionSchedule.apply(context)
    }
}
