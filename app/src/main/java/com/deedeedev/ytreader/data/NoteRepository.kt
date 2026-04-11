package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.BookmarkDao
import com.deedeedev.ytreader.data.local.BookmarkEntity
import com.deedeedev.ytreader.data.local.HighlightNoteDao
import com.deedeedev.ytreader.data.local.HighlightNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository(
    private val highlightNoteDao: HighlightNoteDao,
    private val bookmarkDao: BookmarkDao
) {

    fun observeHighlightsBySubtitleId(subtitleId: Long): Flow<List<HighlightNoteEntity>> =
        highlightNoteDao.observeBySubtitleId(subtitleId)

    fun observeHighlightsBySubtitleIds(subtitleIds: List<Long>): Flow<List<HighlightNoteEntity>> =
        highlightNoteDao.observeBySubtitleIds(subtitleIds)

    suspend fun upsertHighlight(note: HighlightNoteEntity): Long = withContext(Dispatchers.IO) {
        highlightNoteDao.upsert(note)
    }

    suspend fun deleteHighlightByRange(subtitleId: Long, highlightStart: Int, highlightEnd: Int) = withContext(Dispatchers.IO) {
        highlightNoteDao.deleteByRange(subtitleId, highlightStart, highlightEnd)
    }

    suspend fun deleteHighlightsBySubtitleId(subtitleId: Long) = withContext(Dispatchers.IO) {
        highlightNoteDao.deleteBySubtitleId(subtitleId)
    }

    fun observeBookmarksBySubtitleId(subtitleId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeBySubtitleId(subtitleId)

    fun observeBookmarksBySubtitleIds(subtitleIds: List<Long>): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeBySubtitleIds(subtitleIds)

    suspend fun upsertBookmark(bookmark: BookmarkEntity): Long = withContext(Dispatchers.IO) {
        bookmarkDao.upsert(bookmark)
    }

    suspend fun deleteBookmarkByAnchor(subtitleId: Long, anchorStart: Int) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteByAnchor(subtitleId, anchorStart)
    }

    suspend fun deleteBookmarksBySubtitleId(subtitleId: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteBySubtitleId(subtitleId)
    }

    suspend fun deleteAllNotesForSubtitle(subtitleId: Long) = withContext(Dispatchers.IO) {
        highlightNoteDao.deleteBySubtitleId(subtitleId)
        bookmarkDao.deleteBySubtitleId(subtitleId)
    }
}
