package com.deedeedev.ytreader.data

import com.deedeedev.ytreader.data.local.SearchHistoryDao
import com.deedeedev.ytreader.data.local.SearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {

    fun observeAll(): Flow<List<SearchHistoryEntity>> = searchHistoryDao.getAll()

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        searchHistoryDao.getCount()
    }

    suspend fun upsert(entry: SearchHistoryEntity) = withContext(Dispatchers.IO) {
        searchHistoryDao.upsert(entry)
    }

    suspend fun deleteOldest(limit: Int) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteOldest(limit)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        searchHistoryDao.delete(id)
    }
}
