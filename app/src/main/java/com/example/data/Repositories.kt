package com.example.data

import com.example.core.HostFingerprintUtils
import com.example.domain.AuthMethodType
import com.example.domain.CommandSnippet
import com.example.domain.KnownHost
import com.example.domain.ProfileGroup
import com.example.domain.RecentConnection
import com.example.domain.SshProfile
import com.example.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val groupDao: ProfileGroupDao,
    private val recentDao: RecentConnectionDao,
    private val credentialVault: CredentialVault
) {
    val allProfiles: Flow<List<SshProfile>> =
        profileDao.observeAllProfiles().map { list -> list.map { it.toDomain() } }

    val allGroups: Flow<List<ProfileGroup>> =
        groupDao.observeGroups().map { list -> list.map { it.toDomain() } }

    val recentConnections: Flow<List<RecentConnection>> =
        recentDao.observeRecent().map { list -> list.map { it.toDomain() } }

    suspend fun ensureBuiltInGroups() {
        PocketSshDatabase.BUILT_IN_GROUPS.forEach { groupDao.insertIgnore(it) }
    }

    suspend fun getProfileById(id: String): SshProfile? =
        profileDao.getProfileById(id)?.toDomain()

    suspend fun findPotentialDuplicates(host: String, port: Int, username: String, excludeId: String?): List<SshProfile> {
        return profileDao.findMatchingEndpoints(host.trim(), port, username.trim())
            .filter { it.id != excludeId }
            .map { it.toDomain() }
    }

    /**
     * Persists profile metadata to Room AND encrypts any newly provided secret into CredentialVault.
     * Never stores plaintext secrets in Room.
     */
    suspend fun saveProfile(
        profile: SshProfile,
        newPasswordChars: CharArray? = null,
        newPrivateKeyPem: String? = null,
        clearExistingCredential: Boolean = false
    ): SshProfile {
        ensureBuiltInGroups()
        ensureGroupExists(profile.groupName)

        var resolvedCredRef = profile.credentialRefId
        if (clearExistingCredential && resolvedCredRef != null) {
            credentialVault.deleteCredential(resolvedCredRef)
            resolvedCredRef = null
        }

        when (profile.authMethod) {
            AuthMethodType.PASSWORD -> {
                if (newPasswordChars != null && newPasswordChars.isNotEmpty()) {
                    resolvedCredRef = credentialVault.savePassword(resolvedCredRef, newPasswordChars)
                }
            }
            AuthMethodType.PRIVATE_KEY, AuthMethodType.PRIVATE_KEY_PASSPHRASE -> {
                if (!newPrivateKeyPem.isNullOrBlank()) {
                    resolvedCredRef = credentialVault.savePrivateKey(resolvedCredRef, newPrivateKeyPem)
                }
            }
        }

        val now = System.currentTimeMillis()
        val persisted = profile.copy(
            id = profile.id.ifBlank { UUID.randomUUID().toString() },
            name = profile.name.trim(),
            host = profile.host.trim(),
            username = profile.username.trim(),
            groupName = profile.groupName.ifBlank { "Home" },
            credentialRefId = resolvedCredRef,
            updatedAt = now
        )
        profileDao.upsertProfile(SshProfileEntity.fromDomain(persisted))
        return persisted
    }

    suspend fun duplicateProfile(sourceId: String): SshProfile? {
        val original = getProfileById(sourceId) ?: return null
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            credentialRefId = null, // Do not share mutable vault refs silently
            createdAt = now,
            updatedAt = now,
            lastConnectedAt = null,
            connectionCount = 0
        )
        profileDao.upsertProfile(SshProfileEntity.fromDomain(copy))
        return copy
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        profileDao.setFavorite(id, isFavorite, System.currentTimeMillis())
    }

    suspend fun moveToGroup(id: String, groupName: String) {
        ensureGroupExists(groupName)
        profileDao.moveToGroup(id, groupName, System.currentTimeMillis())
    }

    suspend fun renameProfile(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            profileDao.renameProfile(id, trimmed, System.currentTimeMillis())
        }
    }

    /**
     * Deletes the profile. Optionally deletes the associated credential from CredentialVault.
     * Deliberately leaves KnownHost trust entries untouched.
     */
    suspend fun deleteProfile(id: String, removeAssociatedCredential: Boolean) {
        val existing = getProfileById(id)
        profileDao.deleteProfileById(id)
        if (removeAssociatedCredential && existing?.credentialRefId != null) {
            credentialVault.deleteCredential(existing.credentialRefId)
        }
    }

    /**
     * Records a successful connection ONLY after SSH authentication succeeds.
     */
    suspend fun recordSuccessfulConnection(profileId: String, timestamp: Long = System.currentTimeMillis()) {
        val existingProfile = profileDao.getProfileById(profileId) ?: return
        profileDao.recordSuccessfulConnection(existingProfile.id, timestamp)
        val existingRecent = recentDao.getForProfile(profileId)
        val updatedRecent = RecentConnectionEntity(
            profileId = profileId,
            lastConnectedAt = timestamp,
            successCount = (existingRecent?.successCount ?: 0) + 1
        )
        recentDao.upsert(updatedRecent)
    }

    suspend fun clearRecentHistory() {
        recentDao.clearAll()
    }

    suspend fun createCustomGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val existing = groupDao.getAllOnce()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            groupDao.upsert(
                ProfileGroupEntity(
                    name = trimmed,
                    isBuiltIn = false,
                    sortOrder = 100 + existing.size
                )
            )
        }
    }

    suspend fun renameCustomGroup(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || oldName == trimmed) return
        groupDao.renameCustomGroup(oldName, trimmed, profileDao)
    }

    suspend fun deleteCustomGroup(name: String) {
        val profiles = profileDao.getAllProfilesOnce().filter { it.groupName == name }
        profiles.forEach { profileDao.moveToGroup(it.id, "Home", System.currentTimeMillis()) }
        groupDao.deleteCustomGroup(name)
    }

    suspend fun ensureGroupExists(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val existing = groupDao.getAllOnce()
        if (existing.none { it.name == trimmed }) {
            groupDao.insertIgnore(
                ProfileGroupEntity(
                    name = trimmed,
                    isBuiltIn = false,
                    sortOrder = 100 + existing.size
                )
            )
        }
    }

    /**
     * Imports profiles without overwriting existing profiles with the same ID.
     * Imported profiles never gain automatic host key trust.
     */
    suspend fun importProfilesSafely(
        importedProfiles: List<SshProfile>,
        importedGroups: List<String>
    ): Int {
        ensureBuiltInGroups()
        importedGroups.forEach { ensureGroupExists(it) }
        val existingIds = profileDao.getAllProfilesOnce().map { it.id }.toSet()
        var importedCount = 0
        importedProfiles.forEach { raw ->
            val safeId = if (raw.id in existingIds || raw.id.isBlank()) {
                UUID.randomUUID().toString()
            } else {
                raw.id
            }
            val safeName = if (raw.id in existingIds) "${raw.name} (Imported)" else raw.name
            val cleanProfile = raw.copy(
                id = safeId,
                name = safeName,
                credentialRefId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            ensureGroupExists(cleanProfile.groupName)
            profileDao.upsertProfile(SshProfileEntity.fromDomain(cleanProfile))
            importedCount++
        }
        return importedCount
    }

    suspend fun getAllReferencedCredentialIds(): Set<String> {
        return profileDao.getAllProfilesOnce().mapNotNull { it.credentialRefId }.toSet()
    }
}

class KnownHostRepository(
    private val knownHostDao: KnownHostDao
) {
    val allKnownHosts: Flow<List<KnownHost>> =
        knownHostDao.observeAllKnownHosts().map { list -> list.map { it.toDomain() } }

    suspend fun findTrustedHost(host: String, port: Int, keyType: String): KnownHost? {
        return knownHostDao.findHostKey(host.trim().lowercase(), port, keyType)?.toDomain()
    }

    suspend fun trustHost(host: String, port: Int, keyType: String, encodedPublicKey: ByteArray) {
        val normalizedHost = host.trim().lowercase()
        val id = "${HostFingerprintUtils.hostKeyIdentifier(normalizedHost, port)}_$keyType"
        val now = System.currentTimeMillis()
        knownHostDao.upsert(
            KnownHostEntity(
                id = id,
                host = normalizedHost,
                port = port,
                keyType = keyType,
                publicKeyBase64 = HostFingerprintUtils.encodePublicKeyBase64(encodedPublicKey),
                sha256Fingerprint = HostFingerprintUtils.sha256OpenSsh(encodedPublicKey),
                trustedAt = now,
                lastVerifiedAt = now
            )
        )
    }

    suspend fun markVerified(id: String) {
        knownHostDao.touchLastVerified(id, System.currentTimeMillis())
    }

    suspend fun removeTrustedHost(id: String) {
        knownHostDao.deleteById(id)
    }

    suspend fun clearAllTrustedHosts() {
        knownHostDao.deleteAll()
    }
}

class SnippetRepository(
    private val snippetDao: CommandSnippetDao
) {
    val allSnippets: Flow<List<CommandSnippet>> =
        snippetDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun seedDefaultsIfEmpty() {
        if (snippetDao.count() > 0) return
        val defaults = listOf(
            CommandSnippet(
                id = "snip-sys-uptime",
                title = "System Uptime & Load",
                command = "uptime && uname -a",
                groupName = "Diagnostics",
                description = "ตรวจสอบเวลาทำงานและ Kernel",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-disk-df",
                title = "Disk Usage (df -h)",
                command = "df -h",
                groupName = "Diagnostics",
                description = "ตรวจสอบพื้นที่ดิสก์คงเหลือ",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-mem-free",
                title = "Memory Usage (free -m)",
                command = "free -m",
                groupName = "Diagnostics",
                description = "ตรวจสอบการใช้งาน RAM",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-docker-ps",
                title = "Docker Containers",
                command = "docker ps --format \"table {{.Names}}\\t{{.Status}}\\t{{.Ports}}\"",
                groupName = "Docker",
                description = "แสดงรายการ Container ที่กำลังทำงาน",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-journalctl",
                title = "Recent System Logs",
                command = "journalctl -n 50 --no-pager",
                groupName = "Logs",
                description = "ดู Systemd Log ล่าสุด 50 บรรทัด",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-tailscale-status",
                title = "Tailscale Status",
                command = "tailscale status",
                groupName = "Network",
                description = "ตรวจสอบสถานะโหนดใน Tailnet",
                requiresConfirmation = false
            ),
            CommandSnippet(
                id = "snip-service-restart",
                title = "Restart Service (ยืนยันก่อนแทรก)",
                command = "sudo systemctl restart ",
                groupName = "Admin",
                description = "รีสตาร์ท Systemd Service (อันตรายบน Production)",
                requiresConfirmation = true
            ),
            CommandSnippet(
                id = "snip-reboot",
                title = "Reboot Server (อันตราย)",
                command = "sudo reboot",
                groupName = "Admin",
                description = "รีบูตเครื่องเซิร์ฟเวอร์ทันที",
                requiresConfirmation = true
            )
        )
        snippetDao.insertAllIgnore(defaults.map { CommandSnippetEntity.fromDomain(it) })
    }

    suspend fun saveSnippet(snippet: CommandSnippet) {
        val withId = if (snippet.id.isBlank()) {
            snippet.copy(id = UUID.randomUUID().toString())
        } else {
            snippet
        }
        snippetDao.upsert(CommandSnippetEntity.fromDomain(withId))
    }

    suspend fun deleteSnippet(id: String) {
        snippetDao.deleteById(id)
    }
}
