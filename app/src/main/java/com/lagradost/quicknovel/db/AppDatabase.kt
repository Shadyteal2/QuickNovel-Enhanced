package com.lagradost.quicknovel.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UpdateItem::class,
        NovelEntity::class,
        ImplicitInteractionEntity::class,
        RecommendationCandidateEntity::class,
        NeoListEntity::class,
        NeoListPinMap::class
    ],
    version = 10,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun updateDao(): UpdateDao
    abstract fun novelDao(): NovelDao
    abstract fun interactionDao(): ImplicitInteractionDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun neoListDao(): NeoListDao

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

        /**
         * Migration 8 → 9: Create NeoLists tables.
         * Safe: only adds new tables; no existing tables are modified.
         *
         * Novel cap for import is enforced at the application layer (200 max),
         * not at the DB schema level, for maximum flexibility.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ─── Create neolists table ────────────────────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `neolists` (
                        `id`           TEXT    NOT NULL,
                        `title`        TEXT    NOT NULL,
                        `description`  TEXT,
                        `coverUrl`     TEXT,
                        `authorHandle` TEXT,
                        `createdAt`    INTEGER NOT NULL,
                        `importedAt`   INTEGER,
                        `isLocked`     INTEGER NOT NULL DEFAULT 1,
                        `isImported`   INTEGER NOT NULL DEFAULT 0,
                        `novels`       TEXT    NOT NULL DEFAULT '[]',
                        `sortOrder`    INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // ─── Create join table ────────────────────────────────────────
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `neolist_pin_map` (
                        `novelHash` TEXT    NOT NULL,
                        `neoListId` TEXT    NOT NULL,
                        `addedAt`   INTEGER NOT NULL,
                        PRIMARY KEY(`novelHash`, `neoListId`)
                    )
                """.trimIndent())

                // ─── Indices for O(log n) bookmark dialog filter + date sort ──
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_neolists_isLocked`  ON `neolists` (`isLocked`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_neolists_createdAt` ON `neolists` (`createdAt`)")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename old pin map table
                db.execSQL("ALTER TABLE `neolist_pin_map` RENAME TO `neolist_pin_map_old`")
                
                // Create new table with composite primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `neolist_pin_map` (
                        `novelHash` TEXT    NOT NULL,
                        `neoListId` TEXT    NOT NULL,
                        `addedAt`   INTEGER NOT NULL,
                        PRIMARY KEY(`novelHash`, `neoListId`)
                    )
                """.trimIndent())
                
                // Copy data (ignoring duplicates)
                db.execSQL("""
                    INSERT OR IGNORE INTO `neolist_pin_map` (`novelHash`, `neoListId`, `addedAt`)
                    SELECT `novelHash`, `neoListId`, `addedAt` FROM `neolist_pin_map_old`
                """.trimIndent())
                
                // Drop old table
                db.execSQL("DROP TABLE `neolist_pin_map_old`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicknovel_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration() // safe fallback
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

