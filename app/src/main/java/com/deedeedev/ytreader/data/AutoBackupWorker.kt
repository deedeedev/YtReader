package com.deedeedev.ytreader.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.YtReaderApplication
import com.deedeedev.ytreader.ui.settings.exportDataBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AUTO_BACKUP_CHANNEL_ID = "auto_backup"
private const val AUTO_BACKUP_NOTIFICATION_ID = 5_000
private const val AUTO_BACKUP_FILE_PREFIX = "ytreader-auto-"
private const val AUTO_BACKUP_PREFS_PREFIX = "ytreader-auto-prefs-"
private const val MAX_AUTO_BACKUPS = 3

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as YtReaderApplication).container
        val prefs = container.userPreferencesRepository

        val enabled = prefs.getAutoBackupEnabled()
        if (!enabled) {
            return@withContext Result.success()
        }

        val directoryUriString = prefs.getAutoBackupDirectoryUri()
        if (directoryUriString.isNullOrBlank()) {
            return@withContext Result.success()
        }

        val treeUri = Uri.parse(directoryUriString)
        val contentResolver = applicationContext.contentResolver

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val dataFileName = "${AUTO_BACKUP_FILE_PREFIX}$timestamp.zip"

            val dataDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val dataDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, dataDocId)
            val newDataUri = DocumentsContract.createDocument(
                contentResolver,
                dataDocUri,
                "application/zip",
                dataFileName
            ) ?: run {
                postNotification(
                    applicationContext.getString(R.string.auto_backup_failed),
                    applicationContext.getString(R.string.auto_backup_error_create_file)
                )
                return@withContext Result.failure()
            }

            val includeSettings = prefs.getAutoBackupIncludeSettings()

            withContext(Dispatchers.IO) {
                exportAutoDataBackup(applicationContext, newDataUri.toString())
            }

            if (includeSettings) {
                val prefsFileName = "${AUTO_BACKUP_PREFS_PREFIX}$timestamp.json"
                val newPrefsUri = DocumentsContract.createDocument(
                    contentResolver,
                    dataDocUri,
                    "application/json",
                    prefsFileName
                )
                if (newPrefsUri != null) {
                    val json = prefs.exportPreferencesJson()
                    contentResolver.openOutputStream(newPrefsUri)?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    }
                }
            }

            cleanupOldBackups(contentResolver, treeUri, dataDocUri)

            postNotification(
                applicationContext.getString(R.string.auto_backup_success),
                applicationContext.getString(R.string.auto_backup_success_message, dataFileName)
            )

            Result.success()
        } catch (e: Exception) {
            postNotification(
                applicationContext.getString(R.string.auto_backup_failed),
                e.message ?: applicationContext.getString(R.string.operation_failed)
            )
            Result.failure()
        }
    }

    private suspend fun exportAutoDataBackup(context: Context, uriString: String) {
        exportDataBackup(context, uriString)
    }

    private fun cleanupOldBackups(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocUri: Uri
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parentDocUri)
        )

        val autoBackupFiles = mutableListOf<BackupFileEntry>()
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null,
            null,
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                if (name.startsWith(AUTO_BACKUP_FILE_PREFIX) && name.endsWith(".zip")) {
                    val docId = cursor.getString(idCol)
                    val modified = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                    autoBackupFiles.add(BackupFileEntry(docId, name, modified))
                }
            }
        }

        if (autoBackupFiles.size <= MAX_AUTO_BACKUPS) {
            return
        }

        val sorted = autoBackupFiles.sortedByDescending { it.lastModified }
        val toDelete = sorted.drop(MAX_AUTO_BACKUPS)
        for (entry in toDelete) {
            try {
                val deleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                DocumentsContract.deleteDocument(contentResolver, deleteUri)

                val prefsName = entry.fileName
                    .replace(AUTO_BACKUP_FILE_PREFIX, AUTO_BACKUP_PREFS_PREFIX)
                    .replace(".zip", ".json")
                val prefsDocId = findDocumentIdByName(contentResolver, treeUri, childrenUri, prefsName)
                if (prefsDocId != null) {
                    val prefsDeleteUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, prefsDocId)
                    DocumentsContract.deleteDocument(contentResolver, prefsDeleteUri)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun findDocumentIdByName(
        contentResolver: ContentResolver,
        treeUri: Uri,
        childrenUri: Uri,
        targetName: String
    ): String? {
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameCol) == targetName) {
                    return cursor.getString(idCol)
                }
            }
        }
        return null
    }

    private fun postNotification(title: String, message: String) {
        if (!canPostNotifications(applicationContext)) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, AUTO_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(AUTO_BACKUP_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class BackupFileEntry(
        val docId: String,
        val fileName: String,
        val lastModified: Long
    )
}

fun createAutoBackupNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        AUTO_BACKUP_CHANNEL_ID,
        context.getString(R.string.auto_backup_channel_name),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.auto_backup_channel_description)
    }
    manager.createNotificationChannel(channel)
}
