package com.tapnix.keyboard.di;

import android.content.Context;
import com.tapnix.keyboard.database.daos.ClipboardDao;
import com.tapnix.keyboard.engine.ClipboardEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class EngineModule_ProvideClipboardEngineFactory implements Factory<ClipboardEngine> {
  private final Provider<Context> contextProvider;

  private final Provider<ClipboardDao> daoProvider;

  public EngineModule_ProvideClipboardEngineFactory(Provider<Context> contextProvider,
      Provider<ClipboardDao> daoProvider) {
    this.contextProvider = contextProvider;
    this.daoProvider = daoProvider;
  }

  @Override
  public ClipboardEngine get() {
    return provideClipboardEngine(contextProvider.get(), daoProvider.get());
  }

  public static EngineModule_ProvideClipboardEngineFactory create(Provider<Context> contextProvider,
      Provider<ClipboardDao> daoProvider) {
    return new EngineModule_ProvideClipboardEngineFactory(contextProvider, daoProvider);
  }

  public static ClipboardEngine provideClipboardEngine(Context context, ClipboardDao dao) {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideClipboardEngine(context, dao));
  }
}
