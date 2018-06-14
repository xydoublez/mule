/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import static com.google.inject.Guice.createInjector;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.internal.context.DefaultMuleContext;
import org.mule.runtime.core.internal.registry.InternalRegistry;
import org.mule.runtime.core.internal.registry.MuleRegistryBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.name.Names;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

public class GuiceRegistryBuilder implements MuleRegistryBuilder {

  private final Map<String, Registration> registrations = new HashMap<>();

  @Override
  public MuleRegistryBuilder registerObject(String key, Object value) {
    return register(new InstanceRegistration(key, value));
  }

  @Override
  public MuleRegistryBuilder registerType(String key, Class<?> objectType, boolean singleton) {
    return register(new TypeRegistration(key, singleton, objectType));
  }

  @Override
  public <T> MuleRegistryBuilder registerProvider(String key,
                                                  Class<T> objectType,
                                                  Class<? extends Provider<? extends T>> providerType,
                                                  boolean singleton) {
    return register(new ProviderRegistration(key, objectType, providerType, singleton));
  }

  private MuleRegistryBuilder register(Registration registration) {
    if (registrations.containsKey(registration.name)) {
      throw new IllegalStateException("There already is an object registered with key: " + registration.name);
    }

    registrations.put(registration.name, registration);
    return this;
  }

  @Override
  public InternalRegistry build(MuleContext muleContext) {
    MuleInjectionModule module = new MuleInjectionModule();
    registrations.values().forEach(r -> r.registerOn(module));

    return new GuiceRegistry(createInjector(module), muleContext, ((DefaultMuleContext) muleContext).getLifecycleInterceptor());
  }

  private abstract class Registration {

    protected final String name;
    protected final boolean singleton;

    public Registration(String name, boolean singleton) {
      this.name = name;
      this.singleton = singleton;
    }

    protected abstract void registerOn(MuleInjectionModule module);
  }


  private class InstanceRegistration extends Registration {

    private final Object value;

    public InstanceRegistration(String name, Object value) {
      super(name, true);
      this.value = value;
    }

    @Override
    protected void registerOn(MuleInjectionModule module) {
      module.bindInstance(name, value);
    }
  }


  private class TypeRegistration extends Registration {

    private final Class<?> type;

    public TypeRegistration(String name, boolean singleton, Class<?> type) {
      super(name, singleton);
      this.type = type;
    }

    @Override
    protected void registerOn(MuleInjectionModule module) {
      module.bindType(name, type, singleton);
    }
  }


  private class ProviderRegistration<T> extends Registration {

    private final Class<T> objectType;
    private final Class<? extends Provider<? extends T>> providerType;

    public ProviderRegistration(String name, Class<T> objectType, Class<? extends Provider<? extends T>> providerType, boolean singleton) {
      super(name, singleton);
      this.objectType = objectType;
      this.providerType = providerType;
    }

    @Override
    protected void registerOn(MuleInjectionModule module) {
      module.bindProvider(name, objectType, providerType, singleton);
    }
  }


  private class MuleInjectionModule extends AbstractModule {

    private void bindInstance(String key, Object value) {
      scope(named(bind(value.getClass()), key).toProvider(new InjectorProvider(value)), true);
    }

    private void bindType(String key, Class type, boolean singleton) {
      scope(named(bind(type), key).to(type), singleton);
    }

    private <T> void bindProvider(String key, Class<T> objectType, Class<? extends Provider<? extends T>> providerType,
                                  boolean singleton) {
      scope(named(bind(objectType), key).toProvider(providerType), singleton);
    }

    private void scope(ScopedBindingBuilder binding, boolean singleton) {
      if (singleton) {
        binding.in(Singleton.class);
      }
    }

    private LinkedBindingBuilder named(AnnotatedBindingBuilder<?> binding, String name) {
      return binding.annotatedWith(Names.named(name));
    }
  }

  private class InjectorProvider<T> implements Provider<T> {

    private final LazyValue<T> value;

    @Inject
    private Injector injector;

    public InjectorProvider(T instance) {
      value = new LazyValue<>(() -> {
        injector.injectMembers(instance);

        return instance;
      });
    }

    @Override
    public T get() {
      return value.get();
    }
  }
}
