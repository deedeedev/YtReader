package com.deedeedev.ytreader.ui.settings

import android.app.Activity
import android.content.Context
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.DefaultAppContainer
import com.deedeedev.ytreader.YtReaderApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DATABASE_NAME = "ytreader.db"

suspend fun exportPreferencesBackup(
    context: Context,
    appContainer: AppContainer,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(uriString)
        val json = appContainer.userPreferencesRepository.exportPreferencesJson()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(json)
        } ?: error(context.getString(R.string.settings_backup_error_open_destination))
    }
}

suspend fun importPreferencesBackup(
    context: Context,
    appContainer: AppContainer,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(uriString)
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error(context.getString(R.string.settings_backup_error_open_selected))

        check(appContainer.userPreferencesRepository.importPreferencesJson(json)) {
            context.getString(R.string.settings_backup_error_invalid_preferences)
        }
    }
}

suspend fun exportDataBackup(
    context: Context,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(uriString)
        val databaseFiles = databaseFiles(context)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                databaseFiles.forEach { file ->
                    if (!file.exists()) {
                        return@forEach
                    }
                    zipOutputStream.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        input.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()
                }
            }
        } ?: error(context.getString(R.string.settings_backup_error_open_destination))
    }
}

suspend fun importDataBackup(
    context: Context,
    appContainer: AppContainer,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = android.net.Uri.parse(uriString)
        val tempDir = File(context.cacheDir, "db-import").apply {
            deleteRecursively()
            mkdirs()
        }
        val extractedFiles = mutableMapOf<String, File>()

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/').trim()
                    if (name in DATA_BACKUP_FILES && !entry.isDirectory) {
                        val outputFile = File(tempDir, name)
                        outputFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                        extractedFiles[name] = outputFile
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        } ?: error(context.getString(R.string.settings_backup_error_open_backup))

        check(extractedFiles.containsKey(DATABASE_NAME)) {
            context.getString(R.string.settings_backup_error_missing_database)
        }

        appContainer.closeDatabase()

        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val databaseDir = databaseFile.parentFile
            ?: error(context.getString(R.string.settings_backup_error_database_directory))
        databaseFiles(context).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        extractedFiles.forEach { (name, sourceFile) ->
            val targetFile = File(databaseDir, name)
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    val application = context.applicationContext as YtReaderApplication
    application.container = DefaultAppContainer(context.applicationContext)
    (context as? Activity)?.recreate()
}

fun buildPreferencesBackupFileName(): String =
    "ytreader-preferences-${backupTimestamp()}.json"

fun buildDataBackupFileName(): String =
    "ytreader-data-${backupTimestamp()}.zip"

private fun backupTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}

private fun databaseFiles(context: Context): List<File> {
    val databaseFile = context.getDatabasePath(DATABASE_NAME)
    val parent = databaseFile.parentFile ?: return listOf(databaseFile)
    return listOf(
        databaseFile,
        File(parent, "$DATABASE_NAME-wal"),
        File(parent, "$DATABASE_NAME-shm")
    )
}

private val DATA_BACKUP_FILES = setOf(
    DATABASE_NAME,
    "$DATABASE_NAME-wal",
    "$DATABASE_NAME-shm"
)
