/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import javax.inject.Provider;

public class ForwaringProvider<T> extends AbstractProvider<T> {

  private final Provider<T> delegate;

  public ForwaringProvider(Provider<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  protected T doGet() {
    return delegate.get();
  }
}
