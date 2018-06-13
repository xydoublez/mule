/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.Injector;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.util.StringUtils;
import org.mule.runtime.core.internal.lifecycle.LifecycleInterceptor;
import org.mule.runtime.core.internal.lifecycle.phases.NotInLifecyclePhase;
import org.mule.runtime.core.internal.registry.AbstractInternalRegistry;
import org.mule.runtime.core.internal.registry.LifecycleRegistry;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.internal.SingletonScope;
import com.google.inject.name.Named;
import com.google.inject.spi.DefaultBindingScopingVisitor;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GuiceRegistry extends AbstractInternalRegistry implements LifecycleRegistry, Injector {

  private final com.google.inject.Injector injector;
  private ConcurrentMap<String, Binding<?>> bindingsByKey = new ConcurrentHashMap<>();
  private ConcurrentMap<Class, Map<String, Binding>> bindingsByType = new ConcurrentHashMap<>();

  public GuiceRegistry(com.google.inject.Injector injector,
                       MuleContext muleContext,
                       LifecycleInterceptor lifecycleInterceptor) {
    super(muleContext, lifecycleInterceptor);
    this.injector = injector;
  }

  @Override
  protected void doInitialise() throws InitialisationException {
  }

  @Override
  public <T> T inject(T object) throws MuleException {
    injector.injectMembers(object);
    return object;
  }

  @Override
  protected void doDispose() {
  }

  @Override
  public Object applyLifecycle(Object object) throws MuleException {
    getLifecycleManager().applyCompletedPhases(object);
    return object;
  }

  @Override
  public Object applyLifecycle(Object object, String phase) throws MuleException {
    if (phase == null) {
      getLifecycleManager().applyCompletedPhases(object);
    } else {
      getLifecycleManager().applyPhase(object, NotInLifecyclePhase.PHASE_NAME, phase);
    }
    return object;
  }

  @Override
  public void applyLifecycle(Object object, String startPhase, String toPhase) throws MuleException {
    getLifecycleManager().applyPhase(object, startPhase, toPhase);
  }

  @Override
  public <T> T lookupObject(String key, boolean applyLifecycle) {
    if (StringUtils.isBlank(key)) {
      LOGGER.warn(createStaticMessage("Detected a lookup attempt with an empty or null key").getMessage(),
                  new Throwable().fillInStackTrace());
      return null;
    }

    T object = lookupObject(key);
    applyLifecycleIfPrototype(object, key, applyLifecycle);
    return object;
  }

  @Override
  public <T> T lookupObject(String key) {
    return (T) getBinding(key).getProvider().get();
  }

  @Override
  public <T> T lookupObject(Class<T> type) throws RegistrationException {
    return injector.getInstance(type);
  }

  @Override
  public <T> Collection<T> lookupObjects(Class<T> type) {
    return (Collection<T>) getBindings(type).values().stream().map(b -> b.getProvider().get()).collect(toList());
  }

  @Override
  public <T> Collection<T> lookupLocalObjects(Class<T> type) {
    return injector.getBindings().entrySet().stream()
        .filter(entry -> type.isAssignableFrom(entry.getKey().getTypeLiteral().getRawType()))
        .map(entry -> (T) entry.getValue().getProvider().get())
        .collect(toList());
  }

  @Override
  public <T> Collection<T> lookupObjectsForLifecycle(Class<T> type) {
    return (Collection<T>) getBindings(type).values().stream()
        .filter(this::isSingleton)
        .map(b -> b.getProvider().get()).collect(toList());
  }

  @Override
  public <T> Map<String, T> lookupByType(Class<T> type) {
    return getBindings(type).entrySet().stream()
        .collect(toMap(entry -> entry.getKey(), entry -> (T) entry.getValue().getProvider().get()));
  }

  @Override
  public boolean isSingleton(String key) {
    return isSingleton(getBinding(key));
  }

  public boolean isSingleton(Binding<?> binding) {
    return binding.acceptScopingVisitor(new DefaultBindingScopingVisitor<Boolean>() {

      @Override
      public Boolean visitEagerSingleton() {
        return true;
      }

      @Override
      public Boolean visitScope(Scope scope) {
        return scope instanceof SingletonScope;
      }
    });
  }

  private void applyLifecycleIfPrototype(Object object, String key, boolean applyLifecycle) {
    if (applyLifecycle && !isSingleton(key)) {
      try {
        getLifecycleManager().applyCompletedPhases(object);
      } catch (Exception e) {
        throw new MuleRuntimeException(createStaticMessage("Could not apply lifecycle into prototype object " + key), e);
      }
    }
  }

  private Binding<?> getBinding(String key) {
    return bindingsByKey.computeIfAbsent(key, k -> injector.getAllBindings().entrySet().stream()
        .filter(entry -> getName(entry.getKey()).equals(key))
        .map(Map.Entry::getValue)
        .findFirst().orElseThrow(() -> new NoSuchElementException())
    );
  }

  private Map<String, Binding> getBindings(Class type) {
    return bindingsByType.computeIfAbsent(type, c ->
      injector.getAllBindings().entrySet().stream()
          .filter(entry -> type.isAssignableFrom(entry.getKey().getTypeLiteral().getRawType()))
          .collect(toMap(entry -> getName(entry.getKey()), entry -> entry.getValue()))
    );
  }

  private String getName(Key<?> key) {
    if (key.getAnnotation() instanceof Named) {
      return ((Named) key.getAnnotation()).value();
    }

    throw new IllegalStateException("Binding doesn't have a name: " + key.toString());
  }

}
