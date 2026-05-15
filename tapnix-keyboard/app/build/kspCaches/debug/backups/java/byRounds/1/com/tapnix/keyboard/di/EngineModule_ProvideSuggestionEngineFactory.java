package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.daos.BigramDao;
import com.tapnix.keyboard.database.daos.WordDao;
import com.tapnix.keyboard.engine.SuggestionEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class EngineModule_ProvideSuggestionEngineFactory implements Factory<SuggestionEngine> {
  private final Provider<WordDao> wordDaoProvider;

  private final Provider<BigramDao> bigramDaoProvider;

  private final Provider<CoroutineScope> ioScopeProvider;

  public EngineModule_ProvideSuggestionEngineFactory(Provider<WordDao> wordDaoProvider,
      Provider<BigramDao> bigramDaoProvider, Provider<CoroutineScope> ioScopeProvider) {
    this.wordDaoProvider = wordDaoProvider;
    this.bigramDaoProvider = bigramDaoProvider;
    this.ioScopeProvider = ioScopeProvider;
  }

  @Override
  public SuggestionEngine get() {
    return provideSuggestionEngine(wordDaoProvider.get(), bigramDaoProvider.get(), ioScopeProvider.get());
  }

  public static EngineModule_ProvideSuggestionEngineFactory create(
      Provider<WordDao> wordDaoProvider, Provider<BigramDao> bigramDaoProvider,
      Provider<CoroutineScope> ioScopeProvider) {
    return new EngineModule_ProvideSuggestionEngineFactory(wordDaoProvider, bigramDaoProvider, ioScopeProvider);
  }

  public static SuggestionEngine provideSuggestionEngine(WordDao wordDao, BigramDao bigramDao,
      CoroutineScope ioScope) {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideSuggestionEngine(wordDao, bigramDao, ioScope));
  }
}
