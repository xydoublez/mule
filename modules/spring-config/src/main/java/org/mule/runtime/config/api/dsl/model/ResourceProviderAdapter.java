/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model;

import java.io.InputStream;

import org.mule.runtime.dsl.internal.ClassLoaderResourceProvider;

public class ResourceProviderAdapter implements ResourceProvider {

  private ClassLoaderResourceProvider classLoaderResourceProvider;

  public ResourceProviderAdapter(ClassLoader classloader) {
    this.classLoaderResourceProvider = new ClassLoaderResourceProvider(classloader);
  }

  @Override
  public InputStream getResourceAsStream(String uri) {
    return classLoaderResourceProvider.getResourceAsStream(uri);
  }
}
