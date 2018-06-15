/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice.provider;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STORE_MANAGER;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.core.internal.registry.guice.AliasProvider;

import javax.inject.Inject;
import javax.inject.Named;

public class LocalObjectStoreManagerProvider implements AliasProvider<ObjectStoreManager> {

  @Inject
  @Named(OBJECT_STORE_MANAGER)
  private ObjectStoreManager objectStoreManager;

  @Override
  public ObjectStoreManager get() {
    return objectStoreManager;
  }
}
