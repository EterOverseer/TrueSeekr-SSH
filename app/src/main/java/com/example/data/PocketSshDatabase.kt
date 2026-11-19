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
    version = 1,
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
         * Example forward migration placeholder demonstrating non-destructive schema evolution.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future non-destructive column additions in schema v2
            }
        }

        fun create(context: Context): PocketSshDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PocketSshDatabase::class.java,
                "pocketssh.db"
            )
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
