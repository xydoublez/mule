/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

public class ConstantProvider extends AbstractProvider<Object> {

  private final Object value;

  public ConstantProvider(Object value) {
    this.value = value;
  }

  @Override
  protected Object doGet() {
    return value;
  }
}
