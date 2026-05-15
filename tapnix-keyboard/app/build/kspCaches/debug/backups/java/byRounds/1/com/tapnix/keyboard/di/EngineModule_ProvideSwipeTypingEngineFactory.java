package com.tapnix.keyboard.di;

import com.tapnix.keyboard.engine.SwipeTypingEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class EngineModule_ProvideSwipeTypingEngineFactory implements Factory<SwipeTypingEngine> {
  @Override
  public SwipeTypingEngine get() {
    return provideSwipeTypingEngine();
  }

  public static EngineModule_ProvideSwipeTypingEngineFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SwipeTypingEngine provideSwipeTypingEngine() {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideSwipeTypingEngine());
  }

  private static final class InstanceHolder {
    private static final EngineModule_ProvideSwipeTypingEngineFactory INSTANCE = new EngineModule_ProvideSwipeTypingEngineFactory();
  }
}
