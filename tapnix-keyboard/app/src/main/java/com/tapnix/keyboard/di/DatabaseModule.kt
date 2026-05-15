package com.tapnix.keyboard.di

import android.content.Context
import com.tapnix.keyboard.database.TapNixDatabase
import com.tapnix.keyboard.database.daos.BigramDao
import com.tapnix.keyboard.database.daos.ClipboardDao
import com.tapnix.keyboard.database.daos.EmojiDao
import com.tapnix.keyboard.database.daos.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TapNixDatabase =
        TapNixDatabase.getInstance(context)

    @Provides
    fun provideClipboardDao(db: TapNixDatabase): ClipboardDao = db.clipboardDao()

    @Provides
    fun provideEmojiDao(db: TapNixDatabase): EmojiDao = db.emojiDao()

    @Provides
    fun provideWordDao(db: TapNixDatabase): WordDao = db.wordDao()

    @Provides
    fun provideBigramDao(db: TapNixDatabase): BigramDao = db.bigramDao()
}
