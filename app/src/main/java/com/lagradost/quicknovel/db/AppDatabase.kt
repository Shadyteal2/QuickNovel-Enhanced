package com.lagradost.quicknovel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UpdateItem::class, NovelEntity::class, ImplicitInteractionEntity::class, RecommendationCandidateEntity::class], version = 7, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun updateDao(): UpdateDao
    abstract fun novelDao(): NovelDao
    abstract fun interactionDao(): ImplicitInteractionDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indices for implicit_interactions
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_implicit_interactions_novelUrl` ON `implicit_interactions` (`novelUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_implicit_interactions_timestamp` ON `implicit_interactions` (`timestamp`)")

                // Add indices for recommendation_candidates
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_candidates_apiName` ON `recommendation_candidates` (`apiName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recommendation_candidates_lastFetched` ON `recommendation_candidates` (`lastFetched`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicknovel_database"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigration() // safe fallback
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
