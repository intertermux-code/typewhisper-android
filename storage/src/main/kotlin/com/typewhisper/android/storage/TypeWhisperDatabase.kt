package com.typewhisper.android.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptionRecord::class, DictionaryEntry::class, Snippet::class, Profile::class],
    version = 1,
    exportSchema = true,
)
public abstract class TypeWhisperDatabase : RoomDatabase() {

    public abstract fun transcriptions(): TranscriptionDao

    public abstract fun dictionary(): DictionaryDao

    public abstract fun snippets(): SnippetDao

    public abstract fun profiles(): ProfileDao

    public companion object {

        public const val DEFAULT_NAME: String = "typewhisper.db"

        public fun create(context: Context, name: String = DEFAULT_NAME): TypeWhisperDatabase =
            Room.databaseBuilder(context, TypeWhisperDatabase::class.java, name).build()
    }
}
