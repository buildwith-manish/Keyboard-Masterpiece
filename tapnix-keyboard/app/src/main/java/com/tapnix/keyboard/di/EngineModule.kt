package com.tapnix.keyboard.di

import android.content.Context
import com.tapnix.keyboard.database.daos.BigramDao
import com.tapnix.keyboard.database.daos.ClipboardDao
import com.tapnix.keyboard.database.daos.EmojiDao
import com.tapnix.keyboard.database.daos.WordDao
import com.tapnix.keyboard.engine.ClipboardEngine
import com.tapnix.keyboard.engine.EmojiEngine
import com.tapnix.keyboard.engine.GrammarEngine
import com.tapnix.keyboard.engine.SuggestionEngine
import com.tapnix.keyboard.engine.SwipeTypingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideIoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideClipboardEngine(
        @ApplicationContext context: Context,
        dao: ClipboardDao,
    ): ClipboardEngine = ClipboardEngine(context, dao)

    @Provides
    @Singleton
    fun provideEmojiEngine(
        dao: EmojiDao,
        ioScope: CoroutineScope,
    ): EmojiEngine = EmojiEngine(dao, ioScope)

    @Provides
    @Singleton
    fun provideSuggestionEngine(
        wordDao: WordDao,
        bigramDao: BigramDao,
        ioScope: CoroutineScope,
    ): SuggestionEngine = SuggestionEngine(
        dao = wordDao,
        bigramDao = bigramDao,
        ioScope = ioScope,
    )

    @Provides
    @Singleton
    fun provideGrammarEngine(): GrammarEngine = GrammarEngine()

    @Provides
    @Singleton
    fun provideSwipeTypingEngine(): SwipeTypingEngine = SwipeTypingEngine()
}
