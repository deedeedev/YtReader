package com.deedeedev.ytreader.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deedeedev.ytreader.MainActivity
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.YtReaderApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

private const val AI_CLEANING_CHANNEL_ID = "ai_cleaning_jobs"
private const val AI_CLEANING_UNIQUE_WORK_PREFIX = "ai_cleaning_"
private const val AI_CLEANING_NOTIFICATION_ID_BASE = 4_200
private const val KEY_SUBTITLE_ID = "subtitle_id"
private const val KEY_WORK_ID = "work_id"
private const val ACTION_CANCEL_AI_CLEANING = "com.deedeedev.ytreader.action.CANCEL_AI_CLEANING"

class AiCleaningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val subtitleId = inputData.getLong(KEY_SUBTITLE_ID, -1L)
        if (subtitleId <= 0L) {
            return@withContext Result.failure()
        }

        val container = (applicationContext as YtReaderApplication).container
        val subtitleDao = container.subtitleDao
        val preferences = container.userPreferencesRepository
        val repository = container.aiCleaningRepository
        val subtitle = subtitleDao.getById(subtitleId)
            ?: return@withContext Result.failure()

        val sourceText = subtitle.aiCleaningSourceText?.takeIf { it.isNotBlank() }
        if (sourceText == null) {
            subtitleDao.storeAiCleaningFailure(
                id = subtitleId,
                summary = "No text was available for AI cleaning.",
                log = "Subtitle $subtitleId does not have a stored AI cleaning source snapshot.",
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = "AI cleaning failed."
            )
            return@withContext Result.failure()
        }

        val endpoint = preferences.getAiEndpoint().trim()
        val apiKey = preferences.getAiApiKey().trim()
        val model = preferences.getAiModel().trim()
        val prompt = preferences.getAiPrompt()
        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            val failure = AiCleaningFailure(
                summary = "Set AI endpoint, API key, and model in Settings.",
                detailedLog = "AI cleaning could not start because endpoint, API key, or model was blank."
            )
            subtitleDao.storeAiCleaningFailure(
                id = subtitleId,
                summary = failure.summary,
                log = failure.detailedLog,
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = failure.summary
            )
            return@withContext Result.failure()
        }

        val request = AiCleaningRequest(
            endpointBaseUrl = endpoint,
            apiKey = apiKey,
            model = model,
            userInstructions = prompt,
            subtitleText = sourceText
        )

        setForeground(
            createForegroundInfo(
                subtitleId = subtitleId,
                workId = id,
                title = subtitle.title,
                message = "Cleaning in progress"
            )
        )

        return@withContext try {
            val cleanedText = repository.cleanText(request)
            subtitleDao.storeAiCleaningResult(
                id = subtitleId,
                cleanedText = cleanedText,
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = "AI cleaning complete."
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                subtitleDao.cancelAiCleaning(
                    id = subtitleId,
                    updatedAt = System.currentTimeMillis()
                )
            }
            throw cancelled
        } catch (throwable: Throwable) {
            val failure = repository.toFailure(request, throwable)
            subtitleDao.storeAiCleaningFailure(
                id = subtitleId,
                summary = failure.summary,
                log = failure.detailedLog,
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = failure.summary
            )
            Result.failure()
        }
    }

    private fun createForegroundInfo(
        subtitleId: Long,
        workId: UUID,
        title: String,
        message: String
    ): ForegroundInfo {
        val notification = buildAiCleaningForegroundNotification(
            context = applicationContext,
            subtitleId = subtitleId,
            workId = workId,
            title = title,
            message = message
        )
        return ForegroundInfo(notificationIdFor(subtitleId), notification)
    }
}

object AiCleaningWorkScheduler {
    fun enqueue(context: Context, subtitleId: Long) {
        val request = OneTimeWorkRequestBuilder<AiCleaningWorker>()
            .setInputData(workDataOf(KEY_SUBTITLE_ID to subtitleId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(subtitleId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun uniqueWorkName(subtitleId: Long): String = "$AI_CLEANING_UNIQUE_WORK_PREFIX$subtitleId"
}

fun createAiCleaningNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        AI_CLEANING_CHANNEL_ID,
        "AI Cleaning",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Status updates for background AI Cleaning jobs"
    }
    manager.createNotificationChannel(channel)
}

internal fun buildAiCleaningForegroundNotification(
    context: Context,
    subtitleId: Long,
    workId: UUID,
    title: String,
    message: String
): Notification {
    return NotificationCompat.Builder(context, AI_CLEANING_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setContentIntent(createReaderPendingIntent(context, subtitleId))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(0, 0, true)
        .addAction(0, "Cancel", createCancelAiCleaningPendingIntent(context, subtitleId, workId))
        .build()
}

private fun postCompletionNotification(
    context: Context,
    subtitleId: Long,
    title: String,
    message: String
) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, AI_CLEANING_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setContentIntent(createReaderPendingIntent(context, subtitleId))
        .build()
    manager.notify(notificationIdFor(subtitleId), notification)
}

private fun createReaderPendingIntent(context: Context, subtitleId: Long): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_READER
        putExtra(MainActivity.EXTRA_SUBTITLE_ID, subtitleId)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        subtitleId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun createCancelAiCleaningPendingIntent(
    context: Context,
    subtitleId: Long,
    workId: UUID
): PendingIntent {
    val intent = Intent(context, AiCleaningCancelReceiver::class.java).apply {
        action = ACTION_CANCEL_AI_CLEANING
        putExtra(KEY_SUBTITLE_ID, subtitleId)
        putExtra(KEY_WORK_ID, workId.toString())
    }
    return PendingIntent.getBroadcast(
        context,
        subtitleId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class AiCleaningCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_AI_CLEANING) return

        val subtitleId = intent.getLongExtra(KEY_SUBTITLE_ID, -1L)
        val workSpecId = intent.getStringExtra(KEY_WORK_ID)
        if (subtitleId <= 0L || workSpecId.isNullOrBlank()) return

        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelWorkById(UUID.fromString(workSpecId))

        val container = (appContext as YtReaderApplication).container
        kotlinx.coroutines.runBlocking {
            container.subtitleDao.cancelAiCleaning(
                id = subtitleId,
                updatedAt = System.currentTimeMillis()
            )
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationIdFor(subtitleId))
    }
}

private fun notificationIdFor(subtitleId: Long): Int {
    val normalized = (subtitleId % 10_000).toInt().coerceAtLeast(0)
    return AI_CLEANING_NOTIFICATION_ID_BASE + normalized
}
