/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCK_FACTORY;
import org.mule.runtime.api.lock.LockFactory;

import javax.inject.Inject;
import javax.inject.Named;

public class LocalLockFactoryProvider implements AliasProvider<LockFactory> {

  @Inject
  @Named(OBJECT_LOCK_FACTORY)
  private LockFactory lockFactory;

  @Override
  public LockFactory get() {
    return lockFactory;
  }
}
