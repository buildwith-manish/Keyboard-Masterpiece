package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.TapNixDatabase;
import com.tapnix.keyboard.database.daos.BigramDao;
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
public final class DatabaseModule_ProvideBigramDaoFactory implements Factory<BigramDao> {
  private final Provider<TapNixDatabase> dbProvider;

  public DatabaseModule_ProvideBigramDaoFactory(Provider<TapNixDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BigramDao get() {
    return provideBigramDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideBigramDaoFactory create(Provider<TapNixDatabase> dbProvider) {
    return new DatabaseModule_ProvideBigramDaoFactory(dbProvider);
  }

  public static BigramDao provideBigramDao(TapNixDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideBigramDao(db));
  }
}
