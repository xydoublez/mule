/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice.provider;

import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;

public class TypeProvider<T> extends GuiceProvider<T> {

  private final Class<T> type;

  public TypeProvider(Class<T> type) {
    this.type = type;
  }

  @Override
  protected T doGet() {
    return withContextClassLoader(type.getClassLoader(), type::newInstance);
  }
}
