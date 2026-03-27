package com.deedeedev.ytreader.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {
    private const val UNIQUE_WORK_NAME = "auto_backup"
    private const val AUTO_BACKUP_WORK_TAG = "auto_backup"
    private const val BACKUP_INTERVAL_HOURS = 24L

    fun schedule(context: Context, targetTime: String) {
        val delay = calculateInitialDelay(targetTime)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            BACKUP_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(AUTO_BACKUP_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun calculateInitialDelay(targetTime: String): Duration {
        val parts = targetTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 2
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val target = LocalTime.of(hour, minute)
        val now = java.time.ZonedDateTime.now()
        var next = now.with(target)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next)
    }
}
