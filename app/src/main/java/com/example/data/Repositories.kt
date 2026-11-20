package com.example.data

import androidx.room.withTransaction
import com.example.core.HostFingerprintUtils
import com.example.domain.AuthMethodType
import com.example.domain.CommandSnippet
import com.example.domain.KnownHost
import com.example.domain.ManagedTmuxSession
import com.example.domain.ProfileGroup
import com.example.domain.RecentConnection
import com.example.domain.SshProfile
import com.example.security.BackupCryptoManager
import com.example.security.CredentialVault
import com.example.security.ImportDuplicateStrategy
import com.example.security.ImportExecutionSummary
import com.example.security.ProfileBackupBundle
import com.example.security.StoredCredentialKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class DeletedProfilesResult(
    val deletedProfilesSanitized: List<SshProfile>,
    val deletedCredentialCount: Int
)

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val groupDao: ProfileGroupDao,
    private val recentDao: RecentConnectionDao,
    private val credentialVault: CredentialVault,
    private val database: PocketSshDatabase? = null,
    private val tmuxSessionDao: TmuxSessionDao? = null
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

    suspend fun duplicateProfile(
        sourceId: String,
        includeCredential: Boolean = false
    ): SshProfile? {
        val original = getProfileById(sourceId) ?: return null
        val now = System.currentTimeMillis()
        var newId = UUID.randomUUID().toString()
        while (profileDao.getProfileById(newId) != null) {
            newId = UUID.randomUUID().toString()
        }

        var duplicatedCredRef: String? = null
        if (includeCredential && !original.credentialRefId.isNullOrBlank()) {
            val existingSummary = credentialVault.getSummary(original.credentialRefId)
            val secretMaterial = credentialVault.readSecretString(original.credentialRefId)
            if (!secretMaterial.isNullOrBlank() && existingSummary != null) {
                duplicatedCredRef = when (existingSummary.kind) {
                    StoredCredentialKind.PASSWORD -> {
                        val chars = secretMaterial.toCharArray()
                        try {
                            credentialVault.savePassword(null, chars)
                        } finally {
                            chars.fill('\u0000')
                        }
                    }
                    StoredCredentialKind.PRIVATE_KEY -> {
                        credentialVault.savePrivateKey(null, secretMaterial)
                    }
                }
            }
        }

        val copy = original.copy(
            id = newId,
            name = "${original.name} (Copy)",
            credentialRefId = duplicatedCredRef,
            createdAt = now,
            updatedAt = now,
            lastConnectedAt = null,
            connectionCount = 0
        )
        return try {
            profileDao.upsertProfile(SshProfileEntity.fromDomain(copy))
            copy
        } catch (t: Throwable) {
            duplicatedCredRef?.let { ref -> runCatching { credentialVault.deleteCredential(ref) } }
            throw t
        }
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
     * Deletes a single profile safely. Optionally deletes its associated credential from CredentialVault.
     * Deliberately leaves KnownHost trust entries, external user files, and remote tmux sessions untouched.
     */
    suspend fun deleteProfile(id: String, removeAssociatedCredential: Boolean) {
        deleteProfiles(listOf(id), removeAssociatedCredential)
    }

    /**
     * Atomically deletes one or more profiles from Room (plus local non-secret recent & tmux metadata),
     * and only after transaction commit removes unreferenced app-managed credentials from CredentialVault.
     * Returns non-secret sanitized snapshots suitable for Undo without retaining secrets or vault refs.
     */
    suspend fun deleteProfiles(
        ids: List<String>,
        removeAssociatedCredentials: Boolean
    ): DeletedProfilesResult {
        val uniqueIds = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) {
            return DeletedProfilesResult(emptyList(), 0)
        }

        val targets = uniqueIds.mapNotNull { getProfileById(it) }
        if (targets.isEmpty()) {
            return DeletedProfilesResult(emptyList(), 0)
        }

        val targetIds = targets.map { it.id }
        val candidateCredRefs = targets.mapNotNull { it.credentialRefId?.takeIf(String::isNotBlank) }.distinct()
        val effectiveTmuxDao = tmuxSessionDao ?: database?.tmuxSessionDao()

        val dbDeleteBlock: suspend () -> Unit = {
            targetIds.chunked(500).forEach { chunk ->
                recentDao.deleteForProfiles(chunk)
                effectiveTmuxDao?.deleteForProfiles(chunk)
                profileDao.deleteProfilesByIds(chunk)
            }
        }

        if (database != null) {
            database.withTransaction { dbDeleteBlock() }
        } else {
            dbDeleteBlock()
        }

        var deletedCreds = 0
        candidateCredRefs.forEach { refId ->
            val stillReferenced = profileDao.countProfilesUsingCredentialRef(refId) > 0
            if (!stillReferenced) {
                credentialVault.clearEphemeralPassphrase(refId)
                if (removeAssociatedCredentials && credentialVault.hasCredential(refId)) {
                    credentialVault.deleteCredential(refId)
                    deletedCreds++
                }
            }
        }

        val sanitizedSnapshots = targets.map { profile ->
            profile.copy(credentialRefId = null)
        }
        return DeletedProfilesResult(
            deletedProfilesSanitized = sanitizedSnapshots,
            deletedCredentialCount = deletedCreds
        )
    }

    /**
     * Restores non-secret profile metadata from Undo. Always enforces credentialRefId = null
     * so deleted passwords or private keys are never retained or revived via Undo.
     */
    suspend fun restoreNonSecretProfilesFromUndo(snapshots: List<SshProfile>): Int {
        if (snapshots.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var restored = 0
        val dbRestoreBlock: suspend () -> Unit = {
            ensureBuiltInGroups()
            snapshots.forEach { snap ->
                val sanitized = snap.copy(
                    credentialRefId = null,
                    updatedAt = now
                )
                ensureGroupExists(sanitized.groupName)
                profileDao.upsertProfile(SshProfileEntity.fromDomain(sanitized))
                restored++
            }
        }
        if (database != null) {
            database.withTransaction { dbRestoreBlock() }
        } else {
            dbRestoreBlock()
        }
        return restored
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
        val bundle = ProfileBackupBundle(
            formatVersion = ProfileBackupBundle.CURRENT_FORMAT_VERSION,
            groups = importedGroups,
            profiles = importedProfiles
        )
        return importWithStrategy(bundle, ImportDuplicateStrategy.KEEP_BOTH).totalApplied
    }

    /**
     * Atomically imports profiles inside a Room transaction using the selected duplicate resolution strategy.
     * Re-encrypts any embedded backup credentials through this device's CredentialVault (Android Keystore).
     * On failure, rolls back the Room transaction and deletes any newly written vault files.
     */
    suspend fun importWithStrategy(
        bundle: ProfileBackupBundle,
        duplicateStrategy: ImportDuplicateStrategy
    ): ImportExecutionSummary {
        val existingProfiles = profileDao.getAllProfilesOnce().map { it.toDomain() }
        val existingById = existingProfiles.associateBy { it.id }.toMutableMap()
        val existingByEndpoint = existingProfiles.associateBy { BackupCryptoManager.endpointKey(it) }.toMutableMap()

        val newlyCreatedVaultRefs = mutableListOf<String>()
        val obsoleteVaultRefsToDeleteAfterCommit = mutableListOf<String>()

        var importedNew = 0
        var replaced = 0
        var keptBoth = 0
        var skippedDuplicate = 0
        var reEncryptedCreds = 0
        var skippedInvalid = bundle.rawInvalidCount

        val dbBlock: suspend () -> Unit = {
            ensureBuiltInGroups()
            bundle.groups.forEach { ensureGroupExists(it) }
            val now = System.currentTimeMillis()

            bundle.profiles.forEach { raw ->
                if (!BackupCryptoManager.isValidProfile(raw)) {
                    skippedInvalid++
                    return@forEach
                }

                val endpointKey = BackupCryptoManager.endpointKey(raw)
                val matchedExisting = existingById[raw.id] ?: existingByEndpoint[endpointKey]

                val targetId: String
                val targetName: String
                var targetExistingCredRef: String? = null

                if (matchedExisting != null) {
                    when (duplicateStrategy) {
                        ImportDuplicateStrategy.SKIP -> {
                            skippedDuplicate++
                            return@forEach
                        }
                        ImportDuplicateStrategy.REPLACE -> {
                            targetId = matchedExisting.id
                            targetName = raw.name.trim()
                            targetExistingCredRef = matchedExisting.credentialRefId
                            replaced++
                        }
                        ImportDuplicateStrategy.KEEP_BOTH -> {
                            var candidateId = UUID.randomUUID().toString()
                            while (existingById.containsKey(candidateId)) {
                                candidateId = UUID.randomUUID().toString()
                            }
                            targetId = candidateId
                            targetName = if (raw.id == matchedExisting.id || raw.name.equals(matchedExisting.name, ignoreCase = true)) {
                                "${raw.name.trim()} (Imported)"
                            } else {
                                raw.name.trim()
                            }
                            keptBoth++
                        }
                    }
                } else {
                    targetId = raw.id.ifBlank { UUID.randomUUID().toString() }
                    targetName = raw.name.trim()
                    importedNew++
                }

                val embeddedCred = bundle.embeddedCredentials[raw.id]
                val finalCredRefId = if (embeddedCred != null && embeddedCred.secretMaterial.isNotBlank()) {
                    val freshRefId = when (embeddedCred.kind) {
                        StoredCredentialKind.PASSWORD -> {
                            val chars = embeddedCred.secretMaterial.toCharArray()
                            try {
                                credentialVault.savePassword(null, chars)
                            } finally {
                                chars.fill('\u0000')
                            }
                        }
                        StoredCredentialKind.PRIVATE_KEY -> {
                            credentialVault.savePrivateKey(null, embeddedCred.secretMaterial)
                        }
                    }
                    newlyCreatedVaultRefs += freshRefId
                    if (targetExistingCredRef != null && targetExistingCredRef != freshRefId) {
                        obsoleteVaultRefsToDeleteAfterCommit += targetExistingCredRef
                    }
                    reEncryptedCreds++
                    freshRefId
                } else {
                    // When replacing metadata-only without embedded credentials, keep existing vault ref if same host/auth
                    targetExistingCredRef
                }

                val finalProfile = raw.copy(
                    id = targetId,
                    name = targetName,
                    host = raw.host.trim(),
                    username = raw.username.trim(),
                    groupName = raw.groupName.ifBlank { "Home" },
                    credentialRefId = finalCredRefId,
                    createdAt = matchedExisting?.takeIf { duplicateStrategy == ImportDuplicateStrategy.REPLACE }?.createdAt ?: now,
                    updatedAt = now
                )
                ensureGroupExists(finalProfile.groupName)
                profileDao.upsertProfile(SshProfileEntity.fromDomain(finalProfile))
                existingById[finalProfile.id] = finalProfile
                existingByEndpoint[BackupCryptoManager.endpointKey(finalProfile)] = finalProfile
            }
        }

        try {
            if (database != null) {
                database.withTransaction { dbBlock() }
            } else {
                dbBlock()
            }
            // Safe to prune superseded credentials only after DB commit succeeds
            obsoleteVaultRefsToDeleteAfterCommit.forEach { oldRef ->
                runCatching { credentialVault.deleteCredential(oldRef) }
            }
        } catch (t: Throwable) {
            // Roll back any newly created vault credential files on DB transaction failure
            newlyCreatedVaultRefs.forEach { newRef ->
                runCatching { credentialVault.deleteCredential(newRef) }
            }
            throw t
        }

        return ImportExecutionSummary(
            importedNewCount = importedNew,
            replacedCount = replaced,
            keptBothCount = keptBoth,
            skippedDuplicateCount = skippedDuplicate,
            reEncryptedCredentialsCount = reEncryptedCreds,
            skippedInvalidCount = skippedInvalid
        )
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

class TmuxSessionRepository(
    private val tmuxSessionDao: TmuxSessionDao
) {
    fun observeForProfile(profileId: String): Flow<List<ManagedTmuxSession>> =
        tmuxSessionDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun getAllOnce(): List<ManagedTmuxSession> =
        tmuxSessionDao.getAllOnce().map { it.toDomain() }

    suspend fun getForProfileOnce(profileId: String): List<ManagedTmuxSession> =
        tmuxSessionDao.getForProfileOnce(profileId).map { it.toDomain() }

    suspend fun recordSession(session: ManagedTmuxSession) {
        tmuxSessionDao.upsert(TmuxSessionEntity.fromDomain(session))
    }

    suspend fun touchAttached(sessionName: String, timestamp: Long = System.currentTimeMillis()) {
        tmuxSessionDao.touchAttached(sessionName, timestamp)
    }

    suspend fun removeSession(sessionName: String) {
        tmuxSessionDao.deleteByName(sessionName)
    }

    suspend fun removeForProfile(profileId: String) {
        tmuxSessionDao.deleteForProfile(profileId)
    }
}
