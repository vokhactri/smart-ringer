package dev.trivk.smartringer.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object AutomationRecovery {
    private const val WORK_NAME = "smart-ringer-recovery"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutomationRecoveryWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class AutomationRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        RingerScheduler(applicationContext).reconcile(TriggerReason.WORKER)
        Result.success()
    }.getOrElse { Result.retry() }
}

