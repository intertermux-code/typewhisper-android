package com.typewhisper.android.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** One dictation that reached the user, kept so history can be searched and replayed. */
@Entity(tableName = "transcriptions")
public data class TranscriptionRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val engineId: String,
    val language: String?,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A spoken phrase the user wants written differently, e.g. a product name. */
@Entity(tableName = "dictionary")
public data class DictionaryEntry(
    @PrimaryKey val spoken: String,
    val written: String,
)

/** A short trigger expanded into a longer canned text on the way out. */
@Entity(tableName = "snippets")
public data class Snippet(
    // `trigger` is a SQLite keyword, so the column is named around it.
    @PrimaryKey @ColumnInfo(name = "trigger_word") val trigger: String,
    val expansion: String,
)

/** A named bundle of engine + language + normalization, the macOS profiles equivalent. */
@Entity(tableName = "profiles")
public data class Profile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val engineId: String,
    val language: String?,
    val normalizeNumbers: Boolean = true,
)
