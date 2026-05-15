package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.daos.EmojiDao;
import com.tapnix.keyboard.engine.EmojiEngine;
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
public final class EngineModule_ProvideEmojiEngineFactory implements Factory<EmojiEngine> {
  private final Provider<EmojiDao> daoProvider;

  private final Provider<CoroutineScope> ioScopeProvider;

  public EngineModule_ProvideEmojiEngineFactory(Provider<EmojiDao> daoProvider,
      Provider<CoroutineScope> ioScopeProvider) {
    this.daoProvider = daoProvider;
    this.ioScopeProvider = ioScopeProvider;
  }

  @Override
  public EmojiEngine get() {
    return provideEmojiEngine(daoProvider.get(), ioScopeProvider.get());
  }

  public static EngineModule_ProvideEmojiEngineFactory create(Provider<EmojiDao> daoProvider,
      Provider<CoroutineScope> ioScopeProvider) {
    return new EngineModule_ProvideEmojiEngineFactory(daoProvider, ioScopeProvider);
  }

  public static EmojiEngine provideEmojiEngine(EmojiDao dao, CoroutineScope ioScope) {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideEmojiEngine(dao, ioScope));
  }
}
