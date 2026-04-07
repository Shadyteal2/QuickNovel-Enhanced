package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookmarkMigrationManager {
    internal const val MIGRATION_KEY = "BOOKMARK_ROOM_MIGRATION_DONE"

    suspend fun migrateIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val isDone = getKey<Boolean>(MIGRATION_KEY) ?: false
        if (isDone) return@withContext

        try {
            val dao = AppDatabase.getDatabase(context).novelDao()
            val bookmarkStateKeys = getKeys(RESULT_BOOKMARK_STATE) ?: emptyList()
            
            val entitiesToInsert = mutableListOf<NovelEntity>()
            
            for (stateKey in bookmarkStateKeys) {
                val bookmarkType = getKey<Int>(stateKey) ?: continue
                val bookmarkDataId = stateKey.replaceFirst(RESULT_BOOKMARK_STATE, RESULT_BOOKMARK)
                val cached = getKey<ResultCached>(bookmarkDataId) ?: continue
                
                entitiesToInsert.add(
                    NovelEntity(
                        id = cached.id,
                        source = cached.source,
                        name = cached.name,
                        author = cached.author,
                        posterUrl = cached.poster,
                        rating = cached.rating,
                        peopleVoted = null, // Not in ResultCached
                        views = null,
                        synopsis = cached.synopsis,
                        tags = cached.tags,
                        apiName = cached.apiName,
                        lastUpdated = null,
                        lastDownloaded = cached.cachedTime,
                        bookmarkType = bookmarkType
                    )
                )
            }
            
            if (entitiesToInsert.isNotEmpty()) {
                dao.insertAll(entitiesToInsert)
            }
            
            setKey(MIGRATION_KEY, true)
        } catch (t: Throwable) {
            logError(t)
        }
    }
}
