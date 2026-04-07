package com.lagradost.quicknovel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UpdateItem::class, NovelEntity::class, ImplicitInteractionEntity::class, RecommendationCandidateEntity::class], version = 6, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun updateDao(): UpdateDao
    abstract fun novelDao(): NovelDao
    abstract fun interactionDao(): ImplicitInteractionDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicknovel_database"
                )
                .fallbackToDestructiveMigration() // safe for caching
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
