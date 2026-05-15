package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.TapNixDatabase;
import com.tapnix.keyboard.database.daos.ClipboardDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class DatabaseModule_ProvideClipboardDaoFactory implements Factory<ClipboardDao> {
  private final Provider<TapNixDatabase> dbProvider;

  public DatabaseModule_ProvideClipboardDaoFactory(Provider<TapNixDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ClipboardDao get() {
    return provideClipboardDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideClipboardDaoFactory create(
      Provider<TapNixDatabase> dbProvider) {
    return new DatabaseModule_ProvideClipboardDaoFactory(dbProvider);
  }

  public static ClipboardDao provideClipboardDao(TapNixDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideClipboardDao(db));
  }
}
