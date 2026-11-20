package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SshProfileEntity::class,
        ProfileGroupEntity::class,
        RecentConnectionEntity::class,
        KnownHostEntity::class,
        CommandSnippetEntity::class,
        TmuxSessionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class PocketSshDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun profileGroupDao(): ProfileGroupDao
    abstract fun recentConnectionDao(): RecentConnectionDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun commandSnippetDao(): CommandSnippetDao
    abstract fun tmuxSessionDao(): TmuxSessionDao

    companion object {
        val BUILT_IN_GROUPS = listOf(
            ProfileGroupEntity("Home", isBuiltIn = true, sortOrder = 0),
            ProfileGroupEntity("LAN", isBuiltIn = true, sortOrder = 1),
            ProfileGroupEntity("Tailscale", isBuiltIn = true, sortOrder = 2),
            ProfileGroupEntity("VPS", isBuiltIn = true, sortOrder = 3),
            ProfileGroupEntity("Development", isBuiltIn = true, sortOrder = 4),
            ProfileGroupEntity("Production", isBuiltIn = true, sortOrder = 5)
        )

        /**
         * Backward-compatible non-destructive migration adding per-profile Tailscale auto-connect support.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE ssh_profiles ADD COLUMN autoTailscaleConnect INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Non-destructive migration adding TrueSeekrSSH-managed remote tmux session metadata.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tmux_sessions` (
                        `sessionName` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastAttachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionName`),
                        FOREIGN KEY(`profileId`) REFERENCES `ssh_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tmux_sessions_profileId` ON `tmux_sessions` (`profileId`)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): PocketSshDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PocketSshDatabase::class.java,
                "pocketssh.db"
            )
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        BUILT_IN_GROUPS.forEach { group ->
                            db.execSQL(
                                "INSERT OR IGNORE INTO profile_groups (name, isBuiltIn, sortOrder) VALUES (?, ?, ?)",
                                arrayOf<Any>(group.name, if (group.isBuiltIn) 1 else 0, group.sortOrder)
                            )
                        }
                    }
                })
                .build()
        }
    }
}
