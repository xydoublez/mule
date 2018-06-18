/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import org.mule.runtime.core.api.MuleContext;

import javax.inject.Provider;

public interface InternalRegistryBuilder {

  InternalRegistryBuilder registerObject(String key, Object value);

  default InternalRegistryBuilder registerType(String key, Class<?> objectType) {
    return registerType(key, objectType, true);
  }

  InternalRegistryBuilder registerType(String key, Class<?> objectType, boolean singleton);

  default <T> InternalRegistryBuilder registerProvider(String key,
                                                       Class<T> objectType,
                                                       Provider<? extends T> providerType) {

    return registerProvider(key, objectType, providerType, true);
  }

  <T> InternalRegistryBuilder registerProvider(String key,
                                               Class<T> objectType,
                                               Provider<? extends T> provider,
                                               boolean singleton);

  InternalRegistry build(MuleContext muleContext);

}
