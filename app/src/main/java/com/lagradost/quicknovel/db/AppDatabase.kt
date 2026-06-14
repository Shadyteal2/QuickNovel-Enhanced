package com.lagradost.quicknovel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UpdateItem::class, NovelEntity::class, ImplicitInteractionEntity::class, RecommendationCandidateEntity::class], version = 8, exportSchema = false)
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(novel)")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex >= 0) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                cursor.close()

                if (!columns.contains("bookmarkType")) {
                    db.execSQL("ALTER TABLE `novel` ADD COLUMN `bookmarkType` INTEGER DEFAULT NULL")
                }
                if (!columns.contains("downloadStatus")) {
                    db.execSQL("ALTER TABLE `novel` ADD COLUMN `downloadStatus` INTEGER DEFAULT NULL")
                }
                if (!columns.contains("downloadProgress")) {
                    db.execSQL("ALTER TABLE `novel` ADD COLUMN `downloadProgress` INTEGER DEFAULT NULL")
                }
                if (!columns.contains("downloadTotal")) {
                    db.execSQL("ALTER TABLE `novel` ADD COLUMN `downloadTotal` INTEGER DEFAULT NULL")
                }

                // Add indices for novel table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_bookmarkType` ON `novel` (`bookmarkType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_downloadStatus` ON `novel` (`downloadStatus`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicknovel_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration() // safe fallback
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
