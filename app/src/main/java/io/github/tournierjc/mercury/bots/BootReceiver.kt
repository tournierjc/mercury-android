package io.github.tournierjc.mercury.bots

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.tournierjc.mercury.StartupCrashReport

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
        // The 5-second broadcast window is shorter than the 10-second startForeground window.
        // Starting the service from a receiver was the exact recipe of the
        // ForegroundServiceDidNotStartInTime crash: the delivery thread parks in
        // startForegroundService() waiting for a binder answer while One UI (Android 16,
        // SM-A546B) holds the reply — the ANR timer fires and the whole process dies with it.
        // The service now starts itself on a private thread: the receiver returns immediately,
        // and a slow binder answer can never strand the main looper again.
        if (Build.VERSION.SDK_INT >= 34) {
            startDeferred(context)
            return
        }
        if (WatchService.start(context)) return
        AttentionSchedule.apply(context)
    }

    private fun startDeferred(context: Context) {
        val appContext = context.applicationContext
        Thread {
            val started = runCatching {
                appContext.startForegroundService(Intent(appContext, WatchService::class.java))
            }.isSuccess
            if (!started) runCatching { AttentionSchedule.apply(appContext) }
        }.also { thread ->
            thread.setUncaughtExceptionHandler { _, error ->
                runCatching { StartupCrashReport.writeExternal(appContext, error, "BootReceiver") }
            }
        }.start()
    }
}
