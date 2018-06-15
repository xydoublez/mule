/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.LifecycleException;
import org.mule.runtime.core.api.Injector;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.util.Collection;
import java.util.Map;

public interface InternalRegistry extends Initialisable, Disposable, Injector {

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

  /**
   * Will fire any lifecycle methods according to the current lifecycle without actually registering the object in the registry.
   * This is useful for prototype objects that are created per request and would clutter the registry with single use objects.
   *
   * @param object the object to process
   * @return either the same object but with the lifecycle applied or a proxy to it
   * @throws MuleException if the registry fails to perform the lifecycle change for the object.
   */
  Object applyLifecycle(Object object) throws MuleException;

  /**
   * Will fire the given lifecycle {@code phase} without actually registering the object in the registry. This is useful for
   * prototype objects that are created per request and would clutter the registry with single use objects.
   *
   * @param object the object to process
   * @param phase the specific lifecycle phase you want to fire
   * @return either the same object but with the lifecycle applied or a proxy to it
   * @throws MuleException if the registry fails to perform the lifecycle change for the object.
   */
  Object applyLifecycle(Object object, String phase) throws MuleException;

  /**
   * Applies lifecycle phase to an object independent of the current lifecycle phase. All phases between the {@code startPhase}
   * and the {@code endPhase} will be executed.
   *
   * @param object the object to apply lifecycle to
   * @param startPhase the lifecycle phase the object is currently in. Must not be null.
   * @param toPhase the lifecycle phase to transition the object to. Must not be null.
   * @throws MuleException if there is an exception while invoking lifecycle on the object
   */
  void applyLifecycle(Object object, String startPhase, String toPhase) throws MuleException;

  /**
   * Look up a single object by name.
   * <p/>
   * Because {@link #lookupObject(String)} will return objects which had lifecycle phases applied,this method exists for cases in
   * which you want to specify that lifecycle is not to be applied. The actual semantics of that actually depends on the
   * implementation, since invoking this method might return a new instance or an already existing one. If an existing one is
   * returned, then the lifecycle might have been previously applied regardless.
   *
   * @param key the key of the object you're looking for
   * @param applyLifecycle if lifecycle should be applied to the returned object. Passing {@code true} doesn't guarantee that the
   *        lifecycle is applied
   * @return object or {@code null} if not found
   */
  <T> T lookupObject(String key, boolean applyLifecycle);

}
