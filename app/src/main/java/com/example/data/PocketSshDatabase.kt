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
        CommandSnippetEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class PocketSshDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun profileGroupDao(): ProfileGroupDao
    abstract fun recentConnectionDao(): RecentConnectionDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun commandSnippetDao(): CommandSnippetDao

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

        fun create(context: Context): PocketSshDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PocketSshDatabase::class.java,
                "pocketssh.db"
            )
                .addMigrations(MIGRATION_1_2)
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
