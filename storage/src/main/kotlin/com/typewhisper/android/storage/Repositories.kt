package com.typewhisper.android.storage

import com.typewhisper.android.core.PostProcessing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the history screen needs; the table itself stays behind this seam. */
public class HistoryRepository(private val database: TypeWhisperDatabase) {

    private val dao: TranscriptionDao = database.transcriptions()

    public fun recent(limit: Int = RECENT_LIMIT): Flow<List<TranscriptionRecord>> = dao.recent(limit)

    public fun search(query: String): Flow<List<TranscriptionRecord>> = dao.search(query.trim())

    public suspend fun save(
        text: String,
        engineId: String,
        language: String?,
        durationMs: Long,
    ): TranscriptionRecord {
        val record = TranscriptionRecord(
            text = text,
            engineId = engineId,
            language = language,
            durationMs = durationMs,
        )
        dao.insert(record)
        return record
    }

    public suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    /** The window the statistics screen sums over. */
    public suspend fun since(createdAt: Long): List<TranscriptionRecord> = dao.since(createdAt)

    public companion object {
        public const val RECENT_LIMIT: Int = 200
    }
}

/**
 * Dictionary and snippet rows as :core sees them. :core cannot depend on Room, so the
 * entities are mapped to its plain data classes here rather than the other way round.
 */
public class SubstitutionRepository(private val database: TypeWhisperDatabase) {

    public val dictionary: Flow<List<PostProcessing.DictionaryEntry>> =
        database.dictionary().all().map { rows -> rows.map { PostProcessing.DictionaryEntry(it.spoken, it.written) } }

    public val snippets: Flow<List<PostProcessing.Snippet>> =
        database.snippets().all().map { rows -> rows.map { PostProcessing.Snippet(it.trigger, it.expansion) } }

    public suspend fun save(entry: DictionaryEntry) {
        database.dictionary().save(entry)
    }

    public suspend fun delete(entry: DictionaryEntry) {
        database.dictionary().delete(entry)
    }

    public suspend fun save(snippet: Snippet) {
        database.snippets().save(snippet)
    }

    public suspend fun delete(snippet: Snippet) {
        database.snippets().delete(snippet)
    }
}

/**
 * Named engine + language bundles — the macOS profiles. Applying one is the caller's
 * business (it means writing the two settings it carries); this repository only owns the rows.
 */
public class ProfileRepository(private val database: TypeWhisperDatabase) {

    public val profiles: Flow<List<Profile>> = database.profiles().all()

    public suspend fun save(profile: Profile) {
        database.profiles().save(profile)
    }

    public suspend fun delete(profile: Profile) {
        database.profiles().delete(profile)
    }

    public suspend fun byId(id: String): Profile? = database.profiles().byId(id)
}
