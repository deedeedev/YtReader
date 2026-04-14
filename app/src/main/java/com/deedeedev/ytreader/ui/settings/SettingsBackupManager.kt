package com.deedeedev.ytreader.ui.settings

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.deedeedev.ytreader.AppContainer
import com.deedeedev.ytreader.DefaultAppContainer
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.YtReaderApplication
import com.deedeedev.ytreader.data.AiCleaningWorkScheduler
import com.deedeedev.ytreader.data.VideoThumbnailStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DATABASE_NAME = "ytreader.db"
private const val DATA_BACKUP_MANIFEST_NAME = "manifest.json"
private const val DATA_BACKUP_FORMAT_VERSION = 2
private const val APP_DATABASE_VERSION = 24
private const val SQLITE_INTEGRITY_OK = "ok"
private const val THUMBNAIL_BACKUP_DIRECTORY = "video_thumbnails"

private val gson = Gson()

data class DataBackupPreview(
    val createdAtEpochMillis: Long?,
    val appVersionName: String?,
    val schemaVersion: Int,
    val roomIdentityHash: String?,
    val subtitleCount: Int,
    val collectionCount: Int,
    val bookmarkCount: Int,
    val highlightNoteCount: Int,
    val hasManifest: Boolean,
    val isCompatible: Boolean,
    val incompatibilityReason: BackupCompatibilityIssue?
)

internal data class DataBackupManifest(
    @SerializedName("formatVersion")
    val formatVersion: Int = DATA_BACKUP_FORMAT_VERSION,
    @SerializedName("createdAtEpochMillis")
    val createdAtEpochMillis: Long,
    @SerializedName("appVersionName")
    val appVersionName: String?,
    @SerializedName("schemaVersion")
    val schemaVersion: Int,
    @SerializedName("roomIdentityHash")
    val roomIdentityHash: String?,
    @SerializedName("subtitleCount")
    val subtitleCount: Int,
    @SerializedName("collectionCount")
    val collectionCount: Int,
    @SerializedName("bookmarkCount")
    val bookmarkCount: Int,
    @SerializedName("highlightNoteCount")
    val highlightNoteCount: Int,
    @SerializedName("thumbnailFileCount")
    val thumbnailFileCount: Int = 0
)

enum class BackupCompatibilityIssue {
    SCHEMA_VERSION_MISMATCH,
    ROOM_IDENTITY_MISMATCH
}

private data class ExtractedDataBackup(
    val directory: File,
    val files: Map<String, File>,
    val thumbnailFiles: Map<String, File>,
    val manifest: DataBackupManifest?
)

private data class DatabaseInspection(
    val schemaVersion: Int,
    val roomIdentityHash: String?,
    val subtitleCount: Int,
    val collectionCount: Int,
    val bookmarkCount: Int,
    val highlightNoteCount: Int
)

private data class InstalledDatabaseMetadata(
    val schemaVersion: Int,
    val roomIdentityHash: String?
)

suspend fun exportPreferencesBackup(
    context: Context,
    appContainer: AppContainer,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
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
        val uri = Uri.parse(uriString)
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: error(context.getString(R.string.settings_backup_error_open_selected))

        check(appContainer.userPreferencesRepository.importPreferencesJson(json)) {
            context.getString(R.string.settings_backup_error_invalid_preferences)
        }
    }
}

suspend fun readDataBackupPreview(
    context: Context,
    uriString: String
): DataBackupPreview {
    return withContext(Dispatchers.IO) {
        val extracted = extractDataBackup(context, Uri.parse(uriString), "db-preview")
        try {
            val inspection = inspectBackupDatabase(extracted)
            val installedMetadata = readInstalledDatabaseMetadata(context)
            val compatibilityIssue = determineBackupCompatibility(
                backupSchemaVersion = inspection.schemaVersion,
                backupIdentityHash = inspection.roomIdentityHash,
                installedSchemaVersion = installedMetadata.schemaVersion,
                installedIdentityHash = installedMetadata.roomIdentityHash
            )

            DataBackupPreview(
                createdAtEpochMillis = extracted.manifest?.createdAtEpochMillis,
                appVersionName = extracted.manifest?.appVersionName,
                schemaVersion = inspection.schemaVersion,
                roomIdentityHash = inspection.roomIdentityHash,
                subtitleCount = extracted.manifest?.subtitleCount ?: inspection.subtitleCount,
                collectionCount = extracted.manifest?.collectionCount ?: inspection.collectionCount,
                bookmarkCount = extracted.manifest?.bookmarkCount ?: inspection.bookmarkCount,
                highlightNoteCount = extracted.manifest?.highlightNoteCount ?: inspection.highlightNoteCount,
                hasManifest = extracted.manifest != null,
                isCompatible = compatibilityIssue == null,
                incompatibilityReason = compatibilityIssue
            )
        } finally {
            extracted.directory.deleteRecursively()
        }
    }
}

suspend fun exportDataBackup(
    context: Context,
    uriString: String
) {
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        checkpointCurrentDatabase(context)
        val currentMetadata = inspectDatabaseFiles(databaseFiles(context).associateBy { it.name })
        val manifest = DataBackupManifest(
            createdAtEpochMillis = System.currentTimeMillis(),
            appVersionName = appVersionName(context),
            schemaVersion = currentMetadata.schemaVersion,
            roomIdentityHash = currentMetadata.roomIdentityHash,
            subtitleCount = currentMetadata.subtitleCount,
            collectionCount = currentMetadata.collectionCount,
            bookmarkCount = currentMetadata.bookmarkCount,
            highlightNoteCount = currentMetadata.highlightNoteCount,
            thumbnailFileCount = thumbnailFiles(context).size
        )

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                zipOutputStream.putNextEntry(ZipEntry(DATA_BACKUP_MANIFEST_NAME))
                zipOutputStream.write(gson.toJson(manifest).toByteArray())
                zipOutputStream.closeEntry()

                databaseFiles(context).forEach { file ->
                    if (!file.exists()) {
                        return@forEach
                    }
                    zipOutputStream.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        input.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()
                }

                thumbnailFiles(context).forEach { file ->
                    if (!file.exists()) {
                        return@forEach
                    }
                    zipOutputStream.putNextEntry(
                        ZipEntry("$THUMBNAIL_BACKUP_DIRECTORY/${file.name}")
                    )
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
    uriString: String,
    forceImport: Boolean = false
) {
    var databaseWasClosed = false
    try {
        withContext(Dispatchers.IO) {
            val extracted = extractDataBackup(context, Uri.parse(uriString), "db-import")
            val backupInspection = inspectBackupDatabase(extracted)
            val installedMetadata = readInstalledDatabaseMetadata(context)
            val compatibilityIssue = if (forceImport) {
                null
            } else {
                determineBackupCompatibility(
                    backupSchemaVersion = backupInspection.schemaVersion,
                    backupIdentityHash = backupInspection.roomIdentityHash,
                    installedSchemaVersion = installedMetadata.schemaVersion,
                    installedIdentityHash = installedMetadata.roomIdentityHash
                )
            }
            check(compatibilityIssue == null) {
                compatibilityIssueMessage(context, compatibilityIssue)
            }

            AiCleaningWorkScheduler.cancelAll(context.applicationContext)
            appContainer.closeDatabase()
            databaseWasClosed = true

            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val databaseDir = databaseFile.parentFile
                ?: error(context.getString(R.string.settings_backup_error_database_directory))
            val rollbackDir = File(context.cacheDir, "db-rollback").apply {
                deleteRecursively()
                mkdirs()
            }

            try {
                backupExistingDatabaseFiles(databaseDir, rollbackDir)
                backupExistingThumbnailFiles(context, rollbackDir)
                replaceDatabaseFiles(databaseDir, extracted.files)
                replaceThumbnailFiles(context, extracted.thumbnailFiles)
                sanitizeImportedDatabase(databaseDir)
                validateInstalledDatabaseFiles(databaseDir)
            } catch (error: Exception) {
                restoreDatabaseFiles(databaseDir, rollbackDir)
                restoreThumbnailFiles(context, rollbackDir)
                throw error
            } finally {
                rollbackDir.deleteRecursively()
                extracted.directory.deleteRecursively()
            }
        }
    } catch (error: Exception) {
        if (databaseWasClosed) {
            rebuildApplicationContainer(context, forceImport)
            (context as? Activity)?.recreate()
        }
        throw error
    }

    rebuildApplicationContainer(context, forceImport)
    (context as? Activity)?.recreate()
}

fun buildPreferencesBackupFileName(): String =
    "ytreader-preferences-${backupTimestamp()}.json"

fun buildDataBackupFileName(): String =
    "ytreader-data-${backupTimestamp()}.zip"

internal fun determineBackupCompatibility(
    backupSchemaVersion: Int,
    backupIdentityHash: String?,
    installedSchemaVersion: Int,
    installedIdentityHash: String?
): BackupCompatibilityIssue? {
    if (backupSchemaVersion != installedSchemaVersion) {
        return BackupCompatibilityIssue.SCHEMA_VERSION_MISMATCH
    }
    if (!backupIdentityHash.isNullOrBlank() &&
        !installedIdentityHash.isNullOrBlank() &&
        backupIdentityHash != installedIdentityHash
    ) {
        return BackupCompatibilityIssue.ROOM_IDENTITY_MISMATCH
    }
    return null
}

internal fun parseDataBackupManifest(json: String): DataBackupManifest? {
    return try {
        gson.fromJson(json, DataBackupManifest::class.java)
    } catch (_: Exception) {
        null
    }
}

private fun extractDataBackup(
    context: Context,
    uri: Uri,
    tempDirectoryName: String
): ExtractedDataBackup {
    val tempDir = File(context.cacheDir, tempDirectoryName).apply {
        deleteRecursively()
        mkdirs()
    }
    val extractedFiles = mutableMapOf<String, File>()
    val extractedThumbnailFiles = mutableMapOf<String, File>()
    var manifest: DataBackupManifest? = null

    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/').trim()
                when {
                    name == DATA_BACKUP_MANIFEST_NAME && !entry.isDirectory -> {
                        val manifestBytes = ByteArrayOutputStream()
                        zipInputStream.copyTo(manifestBytes)
                        manifest = parseDataBackupManifest(manifestBytes.toString(Charsets.UTF_8.name()))
                    }

                    name in DATA_BACKUP_FILES && !entry.isDirectory -> {
                        val outputFile = File(tempDir, name)
                        outputFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                        extractedFiles[name] = outputFile
                    }

                    entry.name.startsWith("$THUMBNAIL_BACKUP_DIRECTORY/") && !entry.isDirectory -> {
                        val outputFile = File(tempDir, "thumbnail-$name")
                        outputFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                        extractedThumbnailFiles[name] = outputFile
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    } ?: error(context.getString(R.string.settings_backup_error_open_backup))

    check(extractedFiles.containsKey(DATABASE_NAME)) {
        context.getString(R.string.settings_backup_error_missing_database)
    }

    return ExtractedDataBackup(
        directory = tempDir,
        files = extractedFiles,
        thumbnailFiles = extractedThumbnailFiles,
        manifest = manifest
    )
}

private fun inspectBackupDatabase(extracted: ExtractedDataBackup): DatabaseInspection {
    val inspection = inspectDatabaseFiles(extracted.files)
    val manifest = extracted.manifest
    if (manifest != null) {
        check(manifest.formatVersion in 1..DATA_BACKUP_FORMAT_VERSION) {
            "Unsupported backup format version: ${manifest.formatVersion}"
        }
    }
    return inspection
}

private fun inspectDatabaseFiles(files: Map<String, File>): DatabaseInspection {
    val databaseFile = files[DATABASE_NAME]
        ?: error("Database file missing from extracted backup")
    check(databaseFile.exists()) {
        "Database file missing from extracted backup"
    }

    val database = SQLiteDatabase.openDatabase(
        databaseFile.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
    )

    return try {
        check(readIntegrityCheck(database).equals(SQLITE_INTEGRITY_OK, ignoreCase = true)) {
            "Database integrity check failed"
        }
        DatabaseInspection(
            schemaVersion = readPragmaInt(database, "user_version"),
            roomIdentityHash = readRoomIdentityHash(database),
            subtitleCount = readTableCount(database, "subtitles"),
            collectionCount = readTableCount(database, "collections"),
            bookmarkCount = readTableCount(database, "bookmarks"),
            highlightNoteCount = readTableCount(database, "highlight_notes")
        )
    } finally {
        database.close()
    }
}

private fun sanitizeImportedDatabase(databaseDir: File) {
    val database = SQLiteDatabase.openDatabase(
        File(databaseDir, DATABASE_NAME).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE
    )
    try {
        database.execSQL(
            """
            UPDATE ai_cleaning_states
            SET aiCleaningInProgress = 0,
                aiCleaningSourceText = NULL,
                aiCleaningPendingResult = NULL,
                aiCleaningErrorSummary = NULL,
                aiCleaningErrorLog = NULL
            """.trimIndent()
        )
    } finally {
        database.close()
    }
}

private fun validateInstalledDatabaseFiles(databaseDir: File) {
    inspectDatabaseFiles(
        DATA_BACKUP_FILES.associateWith { name -> File(databaseDir, name) }
            .filterValues { it.exists() }
    )
}

private fun backupExistingDatabaseFiles(databaseDir: File, rollbackDir: File) {
    DATA_BACKUP_FILES.forEach { name ->
        val source = File(databaseDir, name)
        if (source.exists()) {
            source.copyTo(File(rollbackDir, name), overwrite = true)
        }
    }
}

private fun backupExistingThumbnailFiles(context: Context, rollbackDir: File) {
    val thumbnailRollbackDir = File(rollbackDir, THUMBNAIL_BACKUP_DIRECTORY).apply {
        deleteRecursively()
        mkdirs()
    }
    thumbnailFiles(context).forEach { file ->
        if (file.exists()) {
            file.copyTo(File(thumbnailRollbackDir, file.name), overwrite = true)
        }
    }
}

private fun replaceDatabaseFiles(databaseDir: File, extractedFiles: Map<String, File>) {
    DATA_BACKUP_FILES.forEach { name ->
        val target = File(databaseDir, name)
        if (target.exists() && !target.delete()) {
            error("Failed to delete existing database file: $name")
        }
    }

    extractedFiles.forEach { (name, sourceFile) ->
        sourceFile.copyTo(File(databaseDir, name), overwrite = true)
    }
}

private fun replaceThumbnailFiles(context: Context, extractedThumbnailFiles: Map<String, File>) {
    val targetDir = VideoThumbnailStore.directory(context).apply {
        deleteRecursively()
        mkdirs()
    }
    extractedThumbnailFiles.forEach { (name, sourceFile) ->
        sourceFile.copyTo(File(targetDir, name), overwrite = true)
    }
}

private fun restoreDatabaseFiles(databaseDir: File, rollbackDir: File) {
    DATA_BACKUP_FILES.forEach { name ->
        val target = File(databaseDir, name)
        if (target.exists()) {
            target.delete()
        }
        val rollbackSource = File(rollbackDir, name)
        if (rollbackSource.exists()) {
            rollbackSource.copyTo(target, overwrite = true)
        }
    }
}

private fun restoreThumbnailFiles(context: Context, rollbackDir: File) {
    val targetDir = VideoThumbnailStore.directory(context).apply {
        deleteRecursively()
        mkdirs()
    }
    val thumbnailRollbackDir = File(rollbackDir, THUMBNAIL_BACKUP_DIRECTORY)
    thumbnailRollbackDir.listFiles()?.forEach { file ->
        file.copyTo(File(targetDir, file.name), overwrite = true)
    }
}

private fun readInstalledDatabaseMetadata(context: Context): InstalledDatabaseMetadata {
    val databasePath = context.getDatabasePath(DATABASE_NAME)
    if (!databasePath.exists()) {
        return InstalledDatabaseMetadata(
            schemaVersion = APP_DATABASE_VERSION,
            roomIdentityHash = null
        )
    }
    val inspection = inspectDatabaseFiles(databaseFiles(context).associateBy { it.name }.filterValues { it.exists() })
    return InstalledDatabaseMetadata(
        schemaVersion = inspection.schemaVersion,
        roomIdentityHash = inspection.roomIdentityHash
    )
}

private fun checkpointCurrentDatabase(context: Context) {
    val databasePath = context.getDatabasePath(DATABASE_NAME)
    if (!databasePath.exists()) {
        return
    }
    val database = SQLiteDatabase.openDatabase(
        databasePath.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE
    )
    try {
        database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close()
    } finally {
        database.close()
    }
}

private fun readIntegrityCheck(database: SQLiteDatabase): String {
    return database.rawQuery("PRAGMA integrity_check", null).useSingleStringOrNull().orEmpty()
}

private fun readPragmaInt(database: SQLiteDatabase, pragma: String): Int {
    return database.rawQuery("PRAGMA $pragma", null).useSingleIntOrNull() ?: 0
}

private fun readRoomIdentityHash(database: SQLiteDatabase): String? {
    return try {
        database.rawQuery(
            "SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1",
            null
        ).useSingleStringOrNull()
    } catch (_: SQLiteException) {
        null
    }
}

private fun readTableCount(database: SQLiteDatabase, tableName: String): Int {
    return try {
        database.rawQuery("SELECT COUNT(*) FROM $tableName", null).useSingleIntOrNull() ?: 0
    } catch (_: SQLiteException) {
        0
    }
}

private fun Cursor.useSingleStringOrNull(): String? = use { cursor ->
    if (!cursor.moveToFirst() || cursor.isNull(0)) {
        null
    } else {
        cursor.getString(0)
    }
}

private fun Cursor.useSingleIntOrNull(): Int? = use { cursor ->
    if (!cursor.moveToFirst() || cursor.isNull(0)) {
        null
    } else {
        cursor.getInt(0)
    }
}

private fun compatibilityIssueMessage(
    context: Context,
    issue: BackupCompatibilityIssue?
): String {
    return when (issue) {
        BackupCompatibilityIssue.SCHEMA_VERSION_MISMATCH -> {
            context.getString(R.string.settings_backup_error_schema_mismatch)
        }

        BackupCompatibilityIssue.ROOM_IDENTITY_MISMATCH -> {
            context.getString(R.string.settings_backup_error_identity_mismatch)
        }

        null -> context.getString(R.string.operation_failed)
    }
}

private fun appVersionName(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (_: Exception) {
        null
    }
}

private fun rebuildApplicationContainer(context: Context, allowDestructiveMigration: Boolean = false) {
    val application = context.applicationContext as YtReaderApplication
    val rebuiltContainer = DefaultAppContainer(context.applicationContext, allowDestructiveMigration)
    rebuiltContainer.subtitleDao
    application.container = rebuiltContainer
}

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

private fun thumbnailFiles(context: Context): List<File> {
    return VideoThumbnailStore.listFiles(context)
}
