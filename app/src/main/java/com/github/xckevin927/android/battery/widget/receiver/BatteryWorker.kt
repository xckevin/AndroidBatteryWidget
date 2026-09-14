package com.github.xckevin927.android.battery.widget.receiver

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.xckevin927.android.battery.widget.service.WidgetUpdateService
import java.util.concurrent.TimeUnit

class BatteryWorker(private val appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        // The foreground monitor delivers events immediately. This work is a
        // recovery path, not a retry loop whose backoff grows after each refresh.
        WidgetUpdateService.refreshWidgets(appContext, "workmanager")
        return Result.success()
    }

    companion object {

        private const val TAG = "BatteryWorker"
        private const val UNIQUE_WORK_NAME = "BatteryWidgetRefresh"
        fun start(context: Context) {

            val updateWorkRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .build()


            val workManager = WorkManager.getInstance(context)
            // Retire the old perpetual retry chain, including its accumulated backoff.
            workManager.cancelUniqueWork(TAG)
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, updateWorkRequest
            )
        }
    }
}
