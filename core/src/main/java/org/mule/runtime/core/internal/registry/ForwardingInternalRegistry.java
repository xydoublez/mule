/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.LifecycleException;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.util.Collection;
import java.util.Map;

public class ForwardingInternalRegistry implements InternalRegistry {

  private InternalRegistry registry;

  public void setRegistry(InternalRegistry registry) {
    this.registry = registry;
  }

  @Override
  public <T> T get(String key) {
    return registry.get(key);
  }

  @Override
  public <T> T lookupObject(String key) {
    return registry.lookupObject(key);
  }

  @Override
  public <T> Collection<T> lookupObjects(Class<T> type) {
    return registry.lookupObjects(type);
  }

  @Override
  public <T> Collection<T> lookupLocalObjects(Class<T> type) {
    return registry.lookupLocalObjects(type);
  }

  @Override
  public <T> Collection<T> lookupObjectsForLifecycle(Class<T> type) {
    return registry.lookupObjectsForLifecycle(type);
  }

  @Override
  public <T> T lookupObject(Class<T> clazz) throws RegistrationException {
    return registry.lookupObject(clazz);
  }

  @Override
  public <T> Map<String, T> lookupByType(Class<T> type) {
    return registry.lookupByType(type);
  }

  @Override
  public boolean isSingleton(String key) {
    return registry.isSingleton(key);
  }

  @Override
  public void fireLifecycle(String phase) throws LifecycleException {
    registry.fireLifecycle(phase);
  }

  @Override
  public void initialise() throws InitialisationException {
    registry.initialise();
  }

  @Override
  public void dispose() {
    registry.dispose();
  }

  @Override
  public <T> T inject(T object) throws MuleException {
    return registry.inject(object);
  }
}
