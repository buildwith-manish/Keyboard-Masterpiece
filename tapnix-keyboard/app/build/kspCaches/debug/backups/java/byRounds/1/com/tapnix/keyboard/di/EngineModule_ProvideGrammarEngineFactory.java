package com.tapnix.keyboard.di;

import com.tapnix.keyboard.engine.GrammarEngine;
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
public final class EngineModule_ProvideGrammarEngineFactory implements Factory<GrammarEngine> {
  @Override
  public GrammarEngine get() {
    return provideGrammarEngine();
  }

  public static EngineModule_ProvideGrammarEngineFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static GrammarEngine provideGrammarEngine() {
    return Preconditions.checkNotNullFromProvides(EngineModule.INSTANCE.provideGrammarEngine());
  }

  private static final class InstanceHolder {
    private static final EngineModule_ProvideGrammarEngineFactory INSTANCE = new EngineModule_ProvideGrammarEngineFactory();
  }
}
