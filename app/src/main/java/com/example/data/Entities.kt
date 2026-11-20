package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.AuthMethodType
import com.example.domain.CommandSnippet
import com.example.domain.KnownHost
import com.example.domain.ManagedTmuxSession
import com.example.domain.ProfileGroup
import com.example.domain.RecentConnection
import com.example.domain.SshProfile
import com.example.domain.TerminalThemePreset

@Entity(
    tableName = "ssh_profiles",
    indices = [
        Index(value = ["host", "port", "username"]),
        Index(value = ["groupName"]),
        Index(value = ["isFavorite"])
    ]
)
data class SshProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: String,
    val description: String,
    val groupName: String,
    val tagsSerialized: String,
    val isFavorite: Boolean,
    val connectTimeoutSec: Int,
    val keepAliveIntervalSec: Int,
    val terminalType: String,
    val terminalFontSizeSp: Float,
    val terminalTheme: String,
    val initialWorkingDir: String,
    @ColumnInfo(defaultValue = "0")
    val autoTailscaleConnect: Boolean = false,
    val credentialRefId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastConnectedAt: Long?,
    val connectionCount: Int
) {
    fun toDomain(): SshProfile = SshProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = runCatching { AuthMethodType.valueOf(authMethod) }.getOrDefault(AuthMethodType.PASSWORD),
        description = description,
        groupName = groupName,
        tags = if (tagsSerialized.isBlank()) emptyList() else tagsSerialized.split("|").filter { it.isNotBlank() },
        isFavorite = isFavorite,
        connectTimeoutSec = connectTimeoutSec,
        keepAliveIntervalSec = keepAliveIntervalSec,
        terminalType = terminalType,
        terminalFontSizeSp = terminalFontSizeSp,
        terminalTheme = runCatching { TerminalThemePreset.valueOf(terminalTheme) }.getOrDefault(TerminalThemePreset.CHARCOAL_LIME),
        initialWorkingDir = initialWorkingDir,
        autoTailscaleConnect = autoTailscaleConnect,
        credentialRefId = credentialRefId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastConnectedAt = lastConnectedAt,
        connectionCount = connectionCount
    )

    companion object {
        fun fromDomain(profile: SshProfile): SshProfileEntity = SshProfileEntity(
            id = profile.id,
            name = profile.name.trim(),
            host = profile.host.trim(),
            port = profile.port,
            username = profile.username.trim(),
            authMethod = profile.authMethod.name,
            description = profile.description.trim(),
            groupName = profile.groupName.ifBlank { "Home" },
            tagsSerialized = profile.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("|"),
            isFavorite = profile.isFavorite,
            connectTimeoutSec = profile.connectTimeoutSec,
            keepAliveIntervalSec = profile.keepAliveIntervalSec,
            terminalType = profile.terminalType.ifBlank { "xterm-256color" },
            terminalFontSizeSp = profile.terminalFontSizeSp,
            terminalTheme = profile.terminalTheme.name,
            initialWorkingDir = profile.initialWorkingDir.trim(),
            autoTailscaleConnect = profile.autoTailscaleConnect,
            credentialRefId = profile.credentialRefId,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
            lastConnectedAt = profile.lastConnectedAt,
            connectionCount = profile.connectionCount
        )
    }
}

@Entity(tableName = "profile_groups")
data class ProfileGroupEntity(
    @PrimaryKey val name: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int
) {
    fun toDomain(): ProfileGroup = ProfileGroup(name = name, isBuiltIn = isBuiltIn, sortOrder = sortOrder)
}

@Entity(
    tableName = "recent_connections",
    foreignKeys = [
        ForeignKey(
            entity = SshProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lastConnectedAt"])]
)
data class RecentConnectionEntity(
    @PrimaryKey val profileId: String,
    val lastConnectedAt: Long,
    val successCount: Int
) {
    fun toDomain(): RecentConnection = RecentConnection(
        profileId = profileId,
        lastConnectedAt = lastConnectedAt,
        successCount = successCount
    )
}

@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port", "keyType"], unique = true)]
)
data class KnownHostEntity(
    @PrimaryKey val id: String,
    val host: String,
    val port: Int,
    val keyType: String,
    val publicKeyBase64: String,
    val sha256Fingerprint: String,
    val trustedAt: Long,
    val lastVerifiedAt: Long
) {
    fun toDomain(): KnownHost = KnownHost(
        id = id,
        host = host,
        port = port,
        keyType = keyType,
        publicKeyBase64 = publicKeyBase64,
        sha256Fingerprint = sha256Fingerprint,
        trustedAt = trustedAt,
        lastVerifiedAt = lastVerifiedAt
    )
}

@Entity(tableName = "command_snippets")
data class CommandSnippetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val command: String,
    val groupName: String,
    val description: String,
    val requiresConfirmation: Boolean,
    val createdAt: Long
) {
    fun toDomain(): CommandSnippet = CommandSnippet(
        id = id,
        title = title,
        command = command,
        groupName = groupName,
        description = description,
        requiresConfirmation = requiresConfirmation,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(snippet: CommandSnippet): CommandSnippetEntity = CommandSnippetEntity(
            id = snippet.id,
            title = snippet.title.trim(),
            command = snippet.command,
            groupName = snippet.groupName.ifBlank { "General" },
            description = snippet.description.trim(),
            requiresConfirmation = snippet.requiresConfirmation,
            createdAt = snippet.createdAt
        )
    }
}

@Entity(
    tableName = "tmux_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SshProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class TmuxSessionEntity(
    @PrimaryKey val sessionName: String,
    val profileId: String,
    val createdAt: Long,
    val lastAttachedAt: Long
) {
    fun toDomain(): ManagedTmuxSession = ManagedTmuxSession(
        sessionName = sessionName,
        profileId = profileId,
        createdAt = createdAt,
        lastAttachedAt = lastAttachedAt
    )

    companion object {
        fun fromDomain(session: ManagedTmuxSession): TmuxSessionEntity = TmuxSessionEntity(
            sessionName = session.sessionName,
            profileId = session.profileId,
            createdAt = session.createdAt,
            lastAttachedAt = session.lastAttachedAt
        )
    }
}
