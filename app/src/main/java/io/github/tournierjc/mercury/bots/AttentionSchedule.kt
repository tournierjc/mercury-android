package io.github.tournierjc.mercury.bots

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Pose ou retire la vérification de fond. Une seule tâche périodique existe : la
 * relancer avec un nouvel intervalle la remplace au lieu d'en empiler une seconde.
 */
object AttentionSchedule {

    fun apply(context: Context) {
        val prefs = AttentionPrefs(context)
        val work = WorkManager.getInstance(context)
        if (!prefs.enabled) {
            work.cancelUniqueWork(AttentionWorker.NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AttentionWorker>(
            prefs.intervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        work.enqueueUniquePeriodicWork(
            AttentionWorker.NAME,
            // UPDATE: a changed interval must not wait for the old schedule to expire.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
