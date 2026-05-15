package com.tapnix.keyboard.database

import android.content.Context
import androidx.room.*
import com.tapnix.keyboard.database.daos.ClipboardDao
import com.tapnix.keyboard.database.daos.EmojiDao
import com.tapnix.keyboard.database.daos.WordDao
import com.tapnix.keyboard.database.entities.ClipboardEntity
import com.tapnix.keyboard.database.entities.EmojiEntity
import com.tapnix.keyboard.database.entities.WordFrequencyEntity

@Database(
    entities = [
        ClipboardEntity::class,
        EmojiEntity::class,
        WordFrequencyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TapNixDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao
    abstract fun emojiDao(): EmojiDao
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile
        private var INSTANCE: TapNixDatabase? = null

        fun getInstance(context: Context): TapNixDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TapNixDatabase::class.java,
                    "tapnix.db"
                )
                    .fallbackToDestructiveMigration()
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
