package com.tapnix.keyboard.di;

import com.tapnix.keyboard.database.TapNixDatabase;
import com.tapnix.keyboard.database.daos.EmojiDao;
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
public final class DatabaseModule_ProvideEmojiDaoFactory implements Factory<EmojiDao> {
  private final Provider<TapNixDatabase> dbProvider;

  public DatabaseModule_ProvideEmojiDaoFactory(Provider<TapNixDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public EmojiDao get() {
    return provideEmojiDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideEmojiDaoFactory create(Provider<TapNixDatabase> dbProvider) {
    return new DatabaseModule_ProvideEmojiDaoFactory(dbProvider);
  }

  public static EmojiDao provideEmojiDao(TapNixDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideEmojiDao(db));
  }
}
