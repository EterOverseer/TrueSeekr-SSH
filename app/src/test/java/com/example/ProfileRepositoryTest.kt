package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.KnownHostRepository
import com.example.data.PocketSshDatabase
import com.example.data.ProfileRepository
import com.example.domain.AuthMethodType
import com.example.domain.SshProfile
import com.example.security.CredentialVault
import com.example.security.SoftwareAesGcmCryptoEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileRepositoryTest {

    private lateinit var db: PocketSshDatabase
    private lateinit var vaultDir: File
    private lateinit var vault: CredentialVault
    private lateinit var profileRepo: ProfileRepository
    private lateinit var knownHostRepo: KnownHostRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PocketSshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vaultDir = File(context.cacheDir, "test_vault_${UUID.randomUUID()}").apply { mkdirs() }
        vault = CredentialVault(vaultDir, SoftwareAesGcmCryptoEngine())
        profileRepo = ProfileRepository(
            profileDao = db.profileDao(),
            groupDao = db.profileGroupDao(),
            recentDao = db.recentConnectionDao(),
            credentialVault = vault
        )
        knownHostRepo = KnownHostRepository(db.knownHostDao())
    }

    @After
    fun tearDown() {
        db.close()
        vaultDir.deleteRecursively()
    }

    @Test
    fun `create edit duplicate and delete profile while keeping known hosts intact`() = runTest {
        val profile = SshProfile(
            id = "prof-1",
            name = "ZimaOS Home",
            host = "192.168.1.50",
            port = 22,
            username = "admin",
            authMethod = AuthMethodType.PASSWORD,
            groupName = "Home",
            tags = listOf("nas", "lan")
        )
        val saved = profileRepo.saveProfile(profile, newPasswordChars = "s3cret".toCharArray())
        assertNotNull(saved.credentialRefId)

        val fetched = profileRepo.getProfileById("prof-1")
        assertNotNull(fetched)
        assertEquals("ZimaOS Home", fetched?.name)
        assertEquals("s3cret", vault.readSecretString(fetched?.credentialRefId))

        // Edit profile
        val edited = profileRepo.saveProfile(fetched!!.copy(name = "ZimaOS Updated", port = 2222))
        assertEquals("ZimaOS Updated", edited.name)
        assertEquals(2222, profileRepo.getProfileById("prof-1")?.port)

        // Duplicate profile (must have separate credentialRefId = null)
        val copy = profileRepo.duplicateProfile("prof-1")
        assertNotNull(copy)
        assertNull(copy?.credentialRefId)
        assertEquals(2, profileRepo.allProfiles.first().size)

        // Trust a host key independently
        knownHostRepo.trustHost("192.168.1.50", 2222, "ssh-ed25519", byteArrayOf(1, 2, 3, 4))
        assertNotNull(knownHostRepo.findTrustedHost("192.168.1.50", 2222, "ssh-ed25519"))

        // Delete profile + associated credential; KnownHost MUST remain
        profileRepo.deleteProfile("prof-1", removeAssociatedCredential = true)
        assertNull(profileRepo.getProfileById("prof-1"))
        assertFalse(vault.hasCredential(saved.credentialRefId))
        assertNotNull(knownHostRepo.findTrustedHost("192.168.1.50", 2222, "ssh-ed25519"))
    }

    @Test
    fun `same IP with different usernames and same host with different ports are allowed`() = runTest {
        val p1 = SshProfile(id = "p1", name = "Dev User", host = "10.0.0.10", port = 22, username = "dev")
        val p2 = SshProfile(id = "p2", name = "Root User", host = "10.0.0.10", port = 22, username = "root")
        val p3 = SshProfile(id = "p3", name = "Alt Port", host = "10.0.0.10", port = 2222, username = "dev")

        profileRepo.saveProfile(p1)
        profileRepo.saveProfile(p2)
        profileRepo.saveProfile(p3)

        val all = profileRepo.allProfiles.first()
        assertEquals(3, all.size)
        val potentialDupes = profileRepo.findPotentialDuplicates("10.0.0.10", 22, "dev", excludeId = null)
        assertEquals(1, potentialDupes.size)
        assertEquals("p1", potentialDupes.first().id)
    }

    @Test
    fun `recent connections update only on success and clearRecent preserves profiles`() = runTest {
        val p1 = SshProfile(id = "p-recent", name = "Prod VPS", host = "vps.example.com", port = 22, username = "deploy")
        profileRepo.saveProfile(p1)

        // Initially no recent connections and null lastConnectedAt
        assertNull(profileRepo.getProfileById("p-recent")?.lastConnectedAt)
        assertTrue(profileRepo.recentConnections.first().isEmpty())

        // Record successful auth
        profileRepo.recordSuccessfulConnection("p-recent", 1_700_000_000_000L)
        val afterSuccess = profileRepo.getProfileById("p-recent")
        assertEquals(1_700_000_000_000L, afterSuccess?.lastConnectedAt)
        assertEquals(1, afterSuccess?.connectionCount)
        assertEquals(1, profileRepo.recentConnections.first().size)

        // Clear recent history must not delete saved profile
        profileRepo.clearRecentHistory()
        assertTrue(profileRepo.recentConnections.first().isEmpty())
        assertNotNull(profileRepo.getProfileById("p-recent"))
    }

    @Test
    fun `favorites and custom group lifecycle`() = runTest {
        profileRepo.ensureBuiltInGroups()
        profileRepo.createCustomGroup("Homelab")
        val p = SshProfile(id = "p-fav", name = "Lab", host = "192.168.10.5", port = 22, username = "pi", groupName = "Homelab")
        profileRepo.saveProfile(p)

        profileRepo.toggleFavorite("p-fav", true)
        assertTrue(profileRepo.getProfileById("p-fav")?.isFavorite == true)

        profileRepo.renameCustomGroup("Homelab", "RackLab")
        assertEquals("RackLab", profileRepo.getProfileById("p-fav")?.groupName)

        profileRepo.deleteCustomGroup("RackLab")
        assertEquals("Home", profileRepo.getProfileById("p-fav")?.groupName)
    }
}
