package it.manlio.nova.core.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Fornito dall'app: dà al worker l'aggiornatore già assemblato. */
interface UpdaterProvider {
    val updater: Updater
}

/** Controllo periodico ogni 6 ore, solo con rete (SPEC §4.6, §15: un solo risveglio raggruppato). */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? UpdaterProvider ?: return Result.failure()
        provider.updater.check(automatic = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "nova-update-check"
        private const val HOURS = 6L

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<UpdateWorker>(HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
