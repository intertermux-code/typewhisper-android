package com.typewhisper.android.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
public interface TranscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(record: TranscriptionRecord)

    @Delete
    public suspend fun delete(record: TranscriptionRecord)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    public suspend fun deleteById(id: String)

    @Query("SELECT * FROM transcriptions ORDER BY createdAt DESC LIMIT :limit")
    public fun recent(limit: Int): Flow<List<TranscriptionRecord>>

    /** Substring search; SQLite LIKE is case-insensitive for ASCII, which covers dictation text well enough. */
    @Query("SELECT * FROM transcriptions WHERE text LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    public fun search(query: String): Flow<List<TranscriptionRecord>>

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    public suspend fun byId(id: String): TranscriptionRecord?

    @Query("SELECT * FROM transcriptions WHERE createdAt >= :since ORDER BY createdAt DESC")
    public suspend fun since(since: Long): List<TranscriptionRecord>

    @Query("SELECT COUNT(*) FROM transcriptions")
    public suspend fun count(): Int
}

@Dao
public interface DictionaryDao {

    @Upsert
    public suspend fun save(entry: DictionaryEntry)

    @Delete
    public suspend fun delete(entry: DictionaryEntry)

    @Query("SELECT * FROM dictionary ORDER BY spoken COLLATE NOCASE")
    public fun all(): Flow<List<DictionaryEntry>>
}

@Dao
public interface SnippetDao {

    @Upsert
    public suspend fun save(snippet: Snippet)

    @Delete
    public suspend fun delete(snippet: Snippet)

    @Query("SELECT * FROM snippets ORDER BY trigger_word")
    public fun all(): Flow<List<Snippet>>
}

@Dao
public interface ProfileDao {

    @Upsert
    public suspend fun save(profile: Profile)

    @Delete
    public suspend fun delete(profile: Profile)

    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    public fun all(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    public suspend fun byId(id: String): Profile?
}
