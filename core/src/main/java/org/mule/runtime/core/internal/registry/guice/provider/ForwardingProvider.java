/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice.provider;

import org.mule.runtime.core.api.util.func.Once;

import javax.inject.Provider;

public class ForwardingProvider<T> extends GuiceProvider<T> {

  private final Provider<T> delegate;
  private final Once.RunOnce initializer;

  public ForwardingProvider(Provider<T> delegate) {
    this.delegate = delegate;
    initializer = Once.of(() -> guiceInjector.injectMembers(delegate));
  }

  @Override
  protected T doGet() {
    initializer.runOnce();
    return delegate.get();
  }
}
