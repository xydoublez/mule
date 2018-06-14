/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.LifecycleException;
import org.mule.runtime.core.api.Injector;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.util.Collection;
import java.util.Map;

public interface InternalRegistry extends Initialisable, Disposable, Injector {
  // /////////////////////////////////////////////////////////////////////////
  // Lookup methods - these should NOT create a new object, only return existing ones
  // /////////////////////////////////////////////////////////////////////////

  /**
   * Alias method performing the lookup, here to simplify syntax when called from dynamic languages.
   */
  <T> T get(String key);

  /**
   * Look up a single object by name.
   * 
   * @return object or null if not found
   */
  <T> T lookupObject(String key);

  /**
   * Look up all objects of a given type.
   *
   * @return collection of objects or empty collection if none found
   */
  <T> Collection<T> lookupObjects(Class<T> type);

  /**
   * Look up all objects of a given type within the local registry. local means that no parent registry will be search for local
   * objects.
   *
   * @return collection of objects or empty collection if none found
   */
  <T> Collection<T> lookupLocalObjects(Class<T> type);

  /**
   * Look up all objects of a given type that lifecycle should be applied to. This method differs from
   * {@link #lookupObjects(Class)} in that it allows implementations to provide an alternative implementation of lookup for
   * lifecycle. For example only returning pre-existing objects and not creating new ones on the fly.
   * 
   * @return collection of objects or empty collection if none found
   */
  <T> Collection<T> lookupObjectsForLifecycle(Class<T> type);

  /**
   * Look up a single object by type.
   *
   * @return object or null if not found
   * @throws RegistrationException if more than one object is found.
   */
  <T> T lookupObject(Class<T> clazz) throws RegistrationException;

  /**
   * @return key/object pairs
   */
  <T> Map<String, T> lookupByType(Class<T> type);

  /**
   * @return whether the bean for the given key is declared as a singleton.
   */
  boolean isSingleton(String key);

  // TODO should this really be here
  void fireLifecycle(String phase) throws LifecycleException;

}
