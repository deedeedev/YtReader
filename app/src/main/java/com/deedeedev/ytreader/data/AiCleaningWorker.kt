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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

private const val AI_CLEANING_CHANNEL_ID = "ai_cleaning_jobs"
private const val AI_CLEANING_UNIQUE_WORK_PREFIX = "ai_cleaning_"
private const val AI_CLEANING_NOTIFICATION_ID_BASE = 4_200
private const val KEY_SUBTITLE_ID = "subtitle_id"
private const val KEY_WORK_ID = "work_id"
private const val ACTION_CANCEL_AI_CLEANING = "com.deedeedev.ytreader.action.CANCEL_AI_CLEANING"
private const val AI_CLEANING_WORK_TAG = "ai_cleaning"

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
        val aiCleaningStateDao = container.aiCleaningStateDao
        val preferences = container.userPreferencesRepository
        val repository = container.aiCleaningRepository
        val subtitle = subtitleDao.getById(subtitleId)
            ?: return@withContext Result.failure()

        val aiState = aiCleaningStateDao.getBySubtitleId(subtitleId)
        val sourceText = aiState?.aiCleaningSourceText?.takeIf { it.isNotBlank() }
        if (sourceText == null) {
            aiCleaningStateDao.storeAiCleaningFailure(
                subtitleId = subtitleId,
                summary = applicationContext.getString(R.string.ai_cleaning_no_text_available),
                log = applicationContext.getString(
                    R.string.ai_cleaning_log_missing_snapshot,
                    subtitleId
                ),
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = applicationContext.getString(R.string.ai_cleaning_failed)
            )
            return@withContext Result.failure()
        }

        val endpoint = preferences.getAiEndpoint().trim()
        val apiKey = preferences.getAiApiKey().trim()
        val model = preferences.getAiModel().trim()
        val prompt = preferences.getAiPrompt()
        if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
            val failure = AiCleaningFailure(
                summary = applicationContext.getString(R.string.ai_cleaning_missing_settings),
                detailedLog = applicationContext.getString(R.string.ai_cleaning_log_missing_settings)
            )
            aiCleaningStateDao.storeAiCleaningFailure(
                subtitleId = subtitleId,
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
                message = applicationContext.getString(R.string.ai_cleaning_notification_in_progress)
            )
        )

        return@withContext try {
            val cleanedText = repository.cleanText(request)
            aiCleaningStateDao.storeAiCleaningResult(
                subtitleId = subtitleId,
                result = cleanedText,
                updatedAt = System.currentTimeMillis()
            )
            postCompletionNotification(
                context = applicationContext,
                subtitleId = subtitleId,
                title = subtitle.title,
                message = applicationContext.getString(R.string.ai_cleaning_notification_complete)
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                aiCleaningStateDao.cancelAiCleaning(
                    subtitleId = subtitleId,
                    updatedAt = System.currentTimeMillis()
                )
            }
            throw cancelled
        } catch (throwable: Throwable) {
            val failure = repository.toFailure(request, throwable)
            aiCleaningStateDao.storeAiCleaningFailure(
                subtitleId = subtitleId,
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
            .addTag(AI_CLEANING_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(subtitleId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun uniqueWorkName(subtitleId: Long): String = "$AI_CLEANING_UNIQUE_WORK_PREFIX$subtitleId"

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(AI_CLEANING_WORK_TAG)
    }
}

fun createAiCleaningNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        AI_CLEANING_CHANNEL_ID,
        context.getString(R.string.ai_cleaning_channel_name),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.ai_cleaning_channel_description)
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
        .addAction(
            0,
            context.getString(R.string.cancel),
            createCancelAiCleaningPendingIntent(context, subtitleId, workId)
        )
        .build()
}

private fun postCompletionNotification(
    context: Context,
    subtitleId: Long,
    title: String,
    message: String
) {
    if (!canPostCompletionNotifications(context)) {
        return
    }

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, AI_CLEANING_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setContentIntent(createReaderPendingIntent(context, subtitleId))
        .build()
    try {
        manager.notify(notificationIdFor(subtitleId), notification)
    } catch (_: SecurityException) {
        // Notification permission may have been revoked between check and post.
    }
}

private fun canPostCompletionNotifications(context: Context): Boolean =
    canPostNotifications(context)

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

        val request = parseAiCleaningCancellationRequest(
            subtitleId = intent.getLongExtra(KEY_SUBTITLE_ID, -1L),
            workSpecId = intent.getStringExtra(KEY_WORK_ID)
        ) ?: return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        runBlocking(Dispatchers.IO) {
            try {
                cancelAiCleaningWorkAndState(appContext, request)
            } catch (_: Throwable) {
                // Defensive: receiver must never crash on cancel action.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal data class AiCleaningCancellationRequest(
    val subtitleId: Long,
    val workId: UUID
)

internal fun parseAiCleaningCancellationRequest(
    subtitleId: Long,
    workSpecId: String?
): AiCleaningCancellationRequest? {
    if (subtitleId <= 0L || workSpecId.isNullOrBlank()) {
        return null
    }
    val workId = runCatching { UUID.fromString(workSpecId) }.getOrNull() ?: return null
    return AiCleaningCancellationRequest(subtitleId = subtitleId, workId = workId)
}

internal suspend fun cancelAiCleaningWorkAndState(
    appContext: Context,
    request: AiCleaningCancellationRequest,
    nowProvider: () -> Long = { System.currentTimeMillis() },
    cancelWork: (Context, UUID) -> Unit = { context, workId ->
        WorkManager.getInstance(context).cancelWorkById(workId)
    },
    cancelSubtitleState: suspend (Context, Long, Long) -> Unit = { context, subtitleId, updatedAt ->
        val container = (context as YtReaderApplication).container
        container.aiCleaningStateDao.cancelAiCleaning(subtitleId = subtitleId, updatedAt = updatedAt)
    },
    cancelNotification: (Context, Int) -> Unit = { context, notificationId ->
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }
) {
    cancelWork(appContext, request.workId)
    cancelSubtitleState(appContext, request.subtitleId, nowProvider())
    cancelNotification(appContext, notificationIdFor(request.subtitleId))
}

private fun notificationIdFor(subtitleId: Long): Int {
    val normalized = (subtitleId % Int.MAX_VALUE).toInt().coerceAtLeast(0)
    return AI_CLEANING_NOTIFICATION_ID_BASE + normalized
}
