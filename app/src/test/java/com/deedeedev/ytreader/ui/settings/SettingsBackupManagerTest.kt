package com.deedeedev.ytreader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupManagerTest {

    @Test
    fun determineBackupCompatibility_acceptsMatchingSchemaAndIdentity() {
        val result = determineBackupCompatibility(
            backupSchemaVersion = 18,
            backupIdentityHash = "abc123",
            installedSchemaVersion = 18,
            installedIdentityHash = "abc123"
        )

        assertNull(result)
    }

    @Test
    fun determineBackupCompatibility_rejectsSchemaMismatch() {
        val result = determineBackupCompatibility(
            backupSchemaVersion = 17,
            backupIdentityHash = "abc123",
            installedSchemaVersion = 18,
            installedIdentityHash = "abc123"
        )

        assertEquals(BackupCompatibilityIssue.SCHEMA_VERSION_MISMATCH, result)
    }

    @Test
    fun determineBackupCompatibility_rejectsIdentityMismatch() {
        val result = determineBackupCompatibility(
            backupSchemaVersion = 18,
            backupIdentityHash = "old-hash",
            installedSchemaVersion = 18,
            installedIdentityHash = "new-hash"
        )

        assertEquals(BackupCompatibilityIssue.ROOM_IDENTITY_MISMATCH, result)
    }

    @Test
    fun determineBackupCompatibility_allowsLegacyBackupWithoutIdentityHash() {
        val result = determineBackupCompatibility(
            backupSchemaVersion = 18,
            backupIdentityHash = null,
            installedSchemaVersion = 18,
            installedIdentityHash = "new-hash"
        )

        assertNull(result)
    }

    @Test
    fun parseDataBackupManifest_readsManifestFields() {
        val manifest = parseDataBackupManifest(
            """
            {
              "formatVersion": 1,
              "createdAtEpochMillis": 123456789,
              "appVersionName": "1.0",
              "schemaVersion": 18,
              "roomIdentityHash": "hash-1",
              "subtitleCount": 12,
              "collectionCount": 3,
              "bookmarkCount": 4,
              "highlightNoteCount": 5
            }
            """.trimIndent()
        )

        assertNotNull(manifest)
        assertEquals(1, manifest?.formatVersion)
        assertEquals(123456789L, manifest?.createdAtEpochMillis)
        assertEquals("1.0", manifest?.appVersionName)
        assertEquals(18, manifest?.schemaVersion)
        assertEquals("hash-1", manifest?.roomIdentityHash)
        assertEquals(12, manifest?.subtitleCount)
        assertEquals(3, manifest?.collectionCount)
        assertEquals(4, manifest?.bookmarkCount)
        assertEquals(5, manifest?.highlightNoteCount)
    }

    @Test
    fun parseDataBackupManifest_returnsNullForInvalidJson() {
        assertNull(parseDataBackupManifest("not-json"))
    }
}
