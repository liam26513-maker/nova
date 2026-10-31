package it.manlio.nova.core.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        val attempt = inputData.getInt(ATTEMPT, -1)
        // Attesa del ritorno a una versione precedente: si ricontrolla finché la versione non arriva.
        if (attempt >= 0 && attempt < WATCH_ATTEMPTS && !foundApp(provider.updater.state.value)) {
            enqueueWatch(applicationContext, attempt + 1)
        }
        return Result.success()
    }

    private fun foundApp(state: UpdateState) =
        state is UpdateState.AppAvailable ||
            state is UpdateState.Downloading ||
            state == UpdateState.Installing ||
            state == UpdateState.AwaitingConfirmation

    companion object {
        private const val NAME = "nova-update-check"
        private const val HOURS = 6L
        private const val WATCH = "nova-rollback-watch"
        private const val WATCH_SECONDS = 30L
        private const val WATCH_ATTEMPTS = 30
        private const val ATTEMPT = "attempt"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<UpdateWorker>(HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Dopo aver chiesto a GitHub una versione precedente: ricontrolla ogni 30 secondi (per al massimo
         * 15 minuti) e la installa appena è pubblicata, così dopo la build non si aspetta più di 30 secondi.
         */
        fun watchRollback(context: Context) = enqueueWatch(context, 0)

        private fun enqueueWatch(
            context: Context,
            attempt: Int,
        ) {
            val request =
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setInitialDelay(WATCH_SECONDS, TimeUnit.SECONDS)
                    .setInputData(workDataOf(ATTEMPT to attempt))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            // Dal worker stesso si accoda dopo di sé: REPLACE fermerebbe il controllo in corso.
            val policy = if (attempt == 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WATCH, policy, request)
        }
    }
}
