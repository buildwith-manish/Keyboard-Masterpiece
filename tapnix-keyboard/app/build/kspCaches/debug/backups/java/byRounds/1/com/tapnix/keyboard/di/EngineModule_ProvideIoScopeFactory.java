package com.tapnix.keyboard.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
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
public final class EngineModule_ProvideIoScopeFactory implements Factory<CoroutineScope> {
  @Override
  public CoroutineScope get() {
    return provideIoScope();
  }

  public static EngineModule_ProvideIoScopeFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineScope provideIoScope() {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideIoScope());
  }

  private static final class InstanceHolder {
    private static final EngineModule_ProvideIoScopeFactory INSTANCE = new EngineModule_ProvideIoScopeFactory();
  }
}
