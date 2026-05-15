package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.TapNixDatabase;
import com.tapnix.keyboard.database.daos.WordDao;
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
public final class DatabaseModule_ProvideWordDaoFactory implements Factory<WordDao> {
  private final Provider<TapNixDatabase> dbProvider;

  public DatabaseModule_ProvideWordDaoFactory(Provider<TapNixDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public WordDao get() {
    return provideWordDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideWordDaoFactory create(Provider<TapNixDatabase> dbProvider) {
    return new DatabaseModule_ProvideWordDaoFactory(dbProvider);
  }

  public static WordDao provideWordDao(TapNixDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideWordDao(db));
  }
}
