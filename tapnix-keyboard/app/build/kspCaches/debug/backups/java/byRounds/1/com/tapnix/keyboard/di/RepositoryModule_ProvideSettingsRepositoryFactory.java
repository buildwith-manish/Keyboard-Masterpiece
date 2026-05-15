package com.tapnix.keyboard.di;

import android.content.Context;
import com.tapnix.keyboard.settings.SettingsRepository;
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
public final class RepositoryModule_ProvideSettingsRepositoryFactory implements Factory<SettingsRepository> {
  private final Provider<Context> contextProvider;

  public RepositoryModule_ProvideSettingsRepositoryFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SettingsRepository get() {
    return provideSettingsRepository(contextProvider.get());
  }

  public static RepositoryModule_ProvideSettingsRepositoryFactory create(
      Provider<Context> contextProvider) {
    return new RepositoryModule_ProvideSettingsRepositoryFactory(contextProvider);
  }

  public static SettingsRepository provideSettingsRepository(Context context) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideSettingsRepository(context));
  }
}
