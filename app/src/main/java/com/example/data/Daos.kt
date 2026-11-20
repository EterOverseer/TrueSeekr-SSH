package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM ssh_profiles ORDER BY isFavorite DESC, name COLLATE NOCASE ASC")
    fun observeAllProfiles(): Flow<List<SshProfileEntity>>

    @Query("SELECT * FROM ssh_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): SshProfileEntity?

    @Query("SELECT * FROM ssh_profiles WHERE LOWER(host) = LOWER(:host) AND port = :port AND username = :username")
    suspend fun findMatchingEndpoints(host: String, port: Int, username: String): List<SshProfileEntity>

    @Query("SELECT * FROM ssh_profiles")
    suspend fun getAllProfilesOnce(): List<SshProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: SshProfileEntity)

    @Update
    suspend fun updateProfile(entity: SshProfileEntity)

    @Query("UPDATE ssh_profiles SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE ssh_profiles SET groupName = :groupName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToGroup(id: String, groupName: String, updatedAt: Long)

    @Query("UPDATE ssh_profiles SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroupInProfiles(oldName: String, newName: String)

    @Query("UPDATE ssh_profiles SET name = :newName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProfile(id: String, newName: String, updatedAt: Long)

    @Query("UPDATE ssh_profiles SET lastConnectedAt = :timestamp, connectionCount = connectionCount + 1 WHERE id = :id")
    suspend fun recordSuccessfulConnection(id: String, timestamp: Long)

    @Query("DELETE FROM ssh_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)
}

@Dao
interface ProfileGroupDao {
    @Query("SELECT * FROM profile_groups ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeGroups(): Flow<List<ProfileGroupEntity>>

    @Query("SELECT * FROM profile_groups")
    suspend fun getAllOnce(): List<ProfileGroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(group: ProfileGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: ProfileGroupEntity)

    @Query("DELETE FROM profile_groups WHERE name = :name AND isBuiltIn = 0")
    suspend fun deleteCustomGroup(name: String)

    @Transaction
    suspend fun renameCustomGroup(oldName: String, newName: String, profileDao: ProfileDao) {
        val existing = getAllOnce().firstOrNull { it.name == oldName && !it.isBuiltIn } ?: return
        upsert(existing.copy(name = newName))
        profileDao.renameGroupInProfiles(oldName, newName)
        deleteCustomGroup(oldName)
    }
}

@Dao
interface RecentConnectionDao {
    @Query("SELECT * FROM recent_connections ORDER BY lastConnectedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentConnectionEntity>>

    @Query("SELECT * FROM recent_connections WHERE profileId = :profileId LIMIT 1")
    suspend fun getForProfile(profileId: String): RecentConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentConnectionEntity)

    @Query("DELETE FROM recent_connections")
    suspend fun clearAll()
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts ORDER BY host ASC, port ASC")
    fun observeAllKnownHosts(): Flow<List<KnownHostEntity>>

    @Query("SELECT * FROM known_hosts WHERE LOWER(host) = LOWER(:host) AND port = :port AND keyType = :keyType LIMIT 1")
    suspend fun findHostKey(host: String, port: Int, keyType: String): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KnownHostEntity)

    @Query("UPDATE known_hosts SET lastVerifiedAt = :timestamp WHERE id = :id")
    suspend fun touchLastVerified(id: String, timestamp: Long)

    @Query("DELETE FROM known_hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM known_hosts")
    suspend fun deleteAll()
}

@Dao
interface CommandSnippetDao {
    @Query("SELECT * FROM command_snippets ORDER BY groupName ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CommandSnippetEntity>>

    @Query("SELECT COUNT(*) FROM command_snippets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommandSnippetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<CommandSnippetEntity>)

    @Query("DELETE FROM command_snippets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TmuxSessionDao {
    @Query("SELECT * FROM tmux_sessions WHERE profileId = :profileId ORDER BY lastAttachedAt DESC")
    fun observeForProfile(profileId: String): Flow<List<TmuxSessionEntity>>

    @Query("SELECT * FROM tmux_sessions ORDER BY lastAttachedAt DESC")
    suspend fun getAllOnce(): List<TmuxSessionEntity>

    @Query("SELECT * FROM tmux_sessions WHERE profileId = :profileId ORDER BY lastAttachedAt DESC")
    suspend fun getForProfileOnce(profileId: String): List<TmuxSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TmuxSessionEntity)

    @Query("UPDATE tmux_sessions SET lastAttachedAt = :timestamp WHERE sessionName = :sessionName")
    suspend fun touchAttached(sessionName: String, timestamp: Long)

    @Query("DELETE FROM tmux_sessions WHERE sessionName = :sessionName")
    suspend fun deleteByName(sessionName: String)

    @Query("DELETE FROM tmux_sessions WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
