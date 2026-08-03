package com.bubble.rikkahub.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bubble.rikkahub.data.local.dao.CachedConversationDao
import com.bubble.rikkahub.data.local.dao.CustomizationDao
import com.bubble.rikkahub.data.local.dao.PendingMessageDao
import com.bubble.rikkahub.data.local.entity.CachedConversationEntity
import com.bubble.rikkahub.data.local.entity.CustomizationEntity
import com.bubble.rikkahub.data.local.entity.PendingMessageEntity

@Database(
    entities = [
        CustomizationEntity::class,
        CachedConversationEntity::class,
        PendingMessageEntity::class
    ],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customizationDao(): CustomizationDao
    abstract fun cachedConversationDao(): CachedConversationDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_conversations` (" +
                        "`conversationId` TEXT NOT NULL PRIMARY KEY, " +
                        "`title` TEXT NOT NULL, " +
                        "`assistantId` TEXT, " +
                        "`isPinned` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`messagesJson` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`conversationId` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `cached_conversations` ADD COLUMN `lastReadAt` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customizations` ADD COLUMN `chatBackgroundUri` TEXT")
                db.execSQL("ALTER TABLE `customizations` ADD COLUMN `chatBackgroundColor` INTEGER")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "bubble_rikkahub.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        }
    }
}
