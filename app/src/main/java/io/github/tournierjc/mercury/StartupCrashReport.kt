package io.github.tournierjc.mercury

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Last-resort startup crash log. A release build dies before any screen can
 * show anything, so the exception is written where the owner can find it with
 * a file manager — no computer needed:
 *
 *   Download/Mercury/startup-crash.txt   (the obvious place)
 *   Android/data/<pkg>/files/logs/…      (the sandbox copy)
 *
 * The trace is written to both before re-throwing. Download can fail on newer
 * Androids (scoped storage), which is why the sandbox copy exists: the owner
 * opens the phone's file manager, browses to it, and shares it back to the bot.
 */
object StartupCrashReport {

    private const val FILE_NAME = "startup-crash.txt"
    private const val FOREGROUND_FILE_NAME = "watch-crash.txt"
    private var previous: Thread.UncaughtExceptionHandler? = null

    /**
     * A death inside an activity callback also kills the process through the default
     * handler, but Android sometimes surfaces it via `ActivityManager` instead — and
     * `Instrumentation` hooks survive where a bare re-throw loses the cause. Wrapping
     * the callbacks writes the same file from the inside.
     */
    fun installActivityHooks(app: Application) {
        runCatching {
            val field = Class.forName("android.app.ActivityThread")
                .getDeclaredField("mInstrumentation")
                .apply { isAccessible = true }
            val main = Class.forName("android.app.ActivityThread")
                .getMethod("currentActivityThread").invoke(null)
            val base = field.get(main) as? android.app.Instrumentation ?: return
            if (base is ReportingInstrumentation) return
            field.set(main, ReportingInstrumentation(base, app))
        }
    }

    private class ReportingInstrumentation(
        base: android.app.Instrumentation,
        private val app: Application,
    ) : android.app.Instrumentation() {
        init {
            // Mirror the delegate's wiring — `onCreate` already ran on it; the rest must route.
            runCatching {
                val copy = { name: String ->
                    javaClass.superclass.getDeclaredField(name).apply { isAccessible = true }
                }
                copy("mThread").set(this, copy("mThread").get(base))
                copy("mComponentName").set(this, copy("mComponentName").get(base))
                copy("mResolvedComponentName").set(this, copy("mResolvedComponentName").get(base))
                copy("mAppContext").set(this, copy("mAppContext").get(base))
                copy("mTargets").set(this, copy("mTargets").get(base))
                copy("mActivityMutex").set(this, copy("mActivityMutex").get(base))
                copy("mActivityController").set(this, copy("mActivityController").get(base))
            }
        }

        private fun <T> guarded(block: () -> T): T =
            try {
                block()
            } catch (e: Throwable) {
                write(app, e)
                throw e
            }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) =
            guarded { super.callActivityOnCreate(activity, icicle) }

        override fun callActivityOnCreate(activity: Activity, icicle: Bundle?, persistentState: android.os.PersistableBundle?) =
            guarded { super.callActivityOnCreate(activity, icicle, persistentState) }

        override fun callActivityOnResume(activity: Activity) = guarded { super.callActivityOnResume(activity) }

        override fun callActivityOnStart(activity: Activity) = guarded { super.callActivityOnStart(activity) }
    }

    fun install(context: Context) {
        if (previous != null) return
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(context, error, thread.name)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, error: Throwable, threadName: String = "main") {
        writeInternal(context, error, threadName, FILE_NAME)
    }

    /** The watch's own crash file: a service bug must not be buried in the startup log. */
    fun writeExternal(context: Context, error: Throwable, threadName: String) {
        writeInternal(context, error, threadName, FOREGROUND_FILE_NAME)
    }

    private fun writeInternal(context: Context, error: Throwable, threadName: String, fileName: String) {
        runCatching {
            val sw = StringWriter()
            PrintWriter(sw).use { pw ->
                pw.println("=== Mercury crash ${System.currentTimeMillis()} " +
                    "(thread $threadName, Android ${Build.VERSION.RELEASE}, " +
                    "${Build.MANUFACTURER} ${Build.MODEL}, v${io.github.tournierjc.mercury.BuildConfig.VERSION_NAME}) ===")
                error.printStackTrace(pw)
                pw.println()
            }
            val report = sw.toString()
            // Sandbox copy: always works.
            runCatching {
                val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
                File(dir, fileName).appendText(report)
            }
            // Download copy: the place a human actually looks.
            runCatching {
                val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(dl, "Mercury").apply { mkdirs() }
                File(dir, fileName).appendText(report)
            }
        }
    }
}
