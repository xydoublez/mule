/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice.provider;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCAL_STORE_IN_MEMORY;
import org.mule.runtime.api.store.ObjectStore;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

public class LocalInMemoryObjectStoreProvider implements Provider<ObjectStore> {

  @Inject
  @Named(OBJECT_LOCAL_STORE_IN_MEMORY)
  private ObjectStore objectStore;

  @Override
  public ObjectStore get() {
    return objectStore;
  }
}
