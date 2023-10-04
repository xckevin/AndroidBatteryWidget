package com.github.xckevin927.android.battery.widget.receiver

import android.content.Context
import androidx.work.BackoffPolicy
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

        WidgetUpdateService.start(appContext)
        return Result.retry()
    }

    companion object {

        private const val TAG = "BatteryWorker"
        fun start(context: Context) {

            val uploadWorkRequest = PeriodicWorkRequestBuilder<BatteryWorker>(1, TimeUnit.MINUTES)
                    .addTag(TAG)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        1,
                        TimeUnit.MINUTES)
                    .build()


            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, uploadWorkRequest)
        }
    }
}