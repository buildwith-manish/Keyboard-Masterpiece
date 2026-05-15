package com.tapnix.keyboard.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tapnix.keyboard.database.daos.BigramDao
import com.tapnix.keyboard.database.daos.ClipboardDao
import com.tapnix.keyboard.database.daos.EmojiDao
import com.tapnix.keyboard.database.daos.WordDao
import com.tapnix.keyboard.database.entities.BigramEntity
import com.tapnix.keyboard.database.entities.ClipboardEntity
import com.tapnix.keyboard.database.entities.EmojiEntity
import com.tapnix.keyboard.database.entities.WordFrequencyEntity

@Database(
    entities = [
        ClipboardEntity::class,
        EmojiEntity::class,
        WordFrequencyEntity::class,
        BigramEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TapNixDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao
    abstract fun emojiDao(): EmojiDao
    abstract fun wordDao(): WordDao
    abstract fun bigramDao(): BigramDao

    companion object {
        @Volatile
        private var INSTANCE: TapNixDatabase? = null

        /**
         * Migration from v1 → v2: adds the bigrams table for adaptive
         * next-word prediction. All existing data is preserved.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bigrams (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word1 TEXT NOT NULL,
                        word2 TEXT NOT NULL,
                        frequency INTEGER NOT NULL DEFAULT 1,
                        last_used INTEGER NOT NULL DEFAULT 0,
                        language TEXT NOT NULL DEFAULT 'en'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bigrams_word1_language ON bigrams(word1, language)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_bigrams_word1_word2_language ON bigrams(word1, word2, language)"
                )
            }
        }

        fun getInstance(context: Context): TapNixDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TapNixDatabase::class.java,
                    "tapnix.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .enableMultiInstanceInvalidation()
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter
    fun fromList(v: List<String>): String = v.joinToString(",")

    @TypeConverter
    fun toList(v: String): List<String> = if (v.isBlank()) emptyList() else v.split(",")
}
