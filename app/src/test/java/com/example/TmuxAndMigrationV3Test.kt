package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.data.TmuxSessionRepository
import com.example.domain.ManagedTmuxSession
import com.example.domain.TmuxCheckResult
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import com.example.ssh.RemoteCommandExecutor
import com.example.ssh.RemoteExecResult
import com.example.ssh.TmuxManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TmuxAndMigrationV3Test {

    private class FakeTmuxExecutor(
        private val responses: (String) -> Result<RemoteExecResult>
    ) : RemoteCommandExecutor {
        val executedCommands = mutableListOf<String>()
        override suspend fun exec(
            command: String,
            timeoutMs: Long,
            maxOutputBytes: Int
        ): Result<RemoteExecResult> {
            executedCommands += command
            return responses(command)
        }
    }

    @Test
    fun `tmux session name sanitization and injection guard block shell metacharacters`() {
        assertTrue(TmuxManager.isValidSafeSessionName("trueSeekr_prod-01"))
        assertFalse(TmuxManager.isValidSafeSessionName("bad; rm -rf /"))
        assertFalse(TmuxManager.isValidSafeSessionName("test\$(whoami)"))

        assertEquals("bad_rm_-rf", TmuxManager.sanitizeSessionNameInput("bad; rm -rf /"))
        assertEquals("tmux new-session -A -s 'trueSeekr_srv_1'\n", TmuxManager.buildAttachCommand("trueSeekr_srv_1"))
        assertThrows(IllegalArgumentException::class.java) {
            TmuxManager.buildAttachCommand("evil;reboot")
        }
        assertEquals("'it'\\''s_quoted'", TmuxManager.posixSingleQuote("it's_quoted"))
        assertEquals(2, TmuxManager.DETACH_SEQUENCE.size)
        assertEquals(0x02.toByte(), TmuxManager.DETACH_SEQUENCE[0])
        assertEquals('d'.code.toByte(), TmuxManager.DETACH_SEQUENCE[1])
    }

    @Test
    fun `tmux availability check shows OS-specific install steps when missing and never auto-installs`() = runBlocking {
        val executor = FakeTmuxExecutor {
            Result.success(
                RemoteExecResult(
                    stdout = "MISSING\nLinux\nOS_ID=debian\n",
                    stderr = "",
                    exitCode = 0
                )
            )
        }
        val check = TmuxManager.checkAvailability(executor)
        assertTrue(check is TmuxCheckResult.NotInstalled)
        val missing = check as TmuxCheckResult.NotInstalled
        assertTrue(missing.detectedOs.contains("debian", ignoreCase = true))
        assertTrue(missing.installInstructions.any { it.contains("apt install -y tmux") })
        assertTrue(executor.executedCommands.none { it.contains("sudo") || it.contains("apt install") })
    }

    @Test
    fun `tmux lists and separates TrueSeekrSSH-managed sessions from external user sessions and kills single target`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java).allowMainThreadQueries().build()
        val vault = CredentialVault(File(context.cacheDir, "tmux_v_${UUID.randomUUID()}"), SoftwareAesGcmCryptoEngine())
        val profileRepo = ProfileRepository(db.profileDao(), db.profileGroupDao(), db.recentConnectionDao(), vault, db)
        val tmuxRepo = TmuxSessionRepository(db.tmuxSessionDao())

        profileRepo.saveProfile(
            com.example.domain.SshProfile(id = "prof-1", name = "Dev", host = "10.0.0.2", username = "dev")
        )
        tmuxRepo.recordSession(ManagedTmuxSession("trueSeekr_prof-1_a", "prof-1", 1000L, 2000L))

        val executor = FakeTmuxExecutor { cmd ->
            when {
                cmd.contains("list-sessions") -> Result.success(
                    RemoteExecResult(
                        stdout = "trueSeekr_prof-1_a|2|1|1700000000|1700000500\nmy-manual-build|3|0|1700000000|1700000200\n",
                        stderr = "",
                        exitCode = 0
                    )
                )
                cmd.contains("kill-session") -> Result.success(RemoteExecResult("", "", 0))
                else -> Result.success(RemoteExecResult("", "", 0))
            }
        }

        val remote = TmuxManager.listRemoteSessions(executor, "prof-1", "trueSeekr", tmuxRepo).getOrThrow()
        assertEquals(2, remote.size)
        val managed = remote.first { it.name == "trueSeekr_prof-1_a" }
        val external = remote.first { it.name == "my-manual-build" }
        assertTrue(managed.isManagedByApp)
        assertTrue(managed.isAttached)
        assertEquals("prof-1", managed.associatedProfileId)
        assertFalse(external.isManagedByApp)

        val killRes = TmuxManager.killRemoteSession(executor, "trueSeekr_prof-1_a", tmuxRepo)
        assertTrue(killRes.isSuccess)
        assertTrue(executor.executedCommands.any { it.contains("kill-session -t 'trueSeekr_prof-1_a'") })
        assertTrue(tmuxRepo.getForProfileOnce("prof-1").isEmpty())
        db.close()
    }

    @Test
    fun `Room MIGRATION_2_3 preserves existing profiles and cascade-deletes tmux sessions with profile`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "mig_v2_v3_${UUID.randomUUID()}.db")
        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ssh_profiles` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `host` TEXT NOT NULL,
                `port` INTEGER NOT NULL,
                `username` TEXT NOT NULL,
                `authMethod` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `groupName` TEXT NOT NULL,
                `tagsSerialized` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `connectTimeoutSec` INTEGER NOT NULL,
                `keepAliveIntervalSec` INTEGER NOT NULL,
                `terminalType` TEXT NOT NULL,
                `terminalFontSizeSp` REAL NOT NULL,
                `terminalTheme` TEXT NOT NULL,
                `initialWorkingDir` TEXT NOT NULL,
                `autoTailscaleConnect` INTEGER NOT NULL DEFAULT 0,
                `credentialRefId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastConnectedAt` INTEGER,
                `connectionCount` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_host_port_username` ON `ssh_profiles` (`host`, `port`, `username`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_groupName` ON `ssh_profiles` (`groupName`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_ssh_profiles_isFavorite` ON `ssh_profiles` (`isFavorite`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `profile_groups` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `isBuiltIn` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recent_connections` (
                `profileId` TEXT NOT NULL PRIMARY KEY,
                `lastConnectedAt` INTEGER NOT NULL,
                `successCount` INTEGER NOT NULL,
                FOREIGN KEY(`profileId`) REFERENCES `ssh_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_connections_lastConnectedAt` ON `recent_connections` (`lastConnectedAt`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `known_hosts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `host` TEXT NOT NULL,
                `port` INTEGER NOT NULL,
                `keyType` TEXT NOT NULL,
                `publicKeyBase64` TEXT NOT NULL,
                `sha256Fingerprint` TEXT NOT NULL,
                `trustedAt` INTEGER NOT NULL,
                `lastVerifiedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_known_hosts_host_port_keyType` ON `known_hosts` (`host`, `port`, `keyType`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_snippets` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `title` TEXT NOT NULL,
                `command` TEXT NOT NULL,
                `groupName` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `requiresConfirmation` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO ssh_profiles VALUES (
                'v2-prof', 'VPS V2', '100.64.0.10', 22, 'ubuntu', 'PRIVATE_KEY', '',
                'Tailscale', '', 0, 15, 30, 'xterm-256color', 13.0, 'OBSIDIAN_AURORA',
                '/home/ubuntu', 1, NULL, 1000, 1000, NULL, 2
            )
            """.trimIndent()
        )
        rawDb.version = 2
        rawDb.close()

        val roomDb = Room.databaseBuilder(context, PocketSshDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*PocketSshDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val preserved = roomDb.profileDao().getProfileById("v2-prof")
        assertNotNull(preserved)
        assertTrue(preserved!!.autoTailscaleConnect)

        val tmuxRepo = TmuxSessionRepository(roomDb.tmuxSessionDao())
        tmuxRepo.recordSession(ManagedTmuxSession("trueSeekr_v2_1", "v2-prof"))
        assertEquals(1, tmuxRepo.observeForProfile("v2-prof").first().size)

        // Cascade delete removes associated tmux_sessions rows when parent profile is deleted
        roomDb.profileDao().deleteProfileById("v2-prof")
        assertTrue(tmuxRepo.getAllOnce().isEmpty())

        roomDb.close()
        dbFile.delete()
        Unit
    }
}
