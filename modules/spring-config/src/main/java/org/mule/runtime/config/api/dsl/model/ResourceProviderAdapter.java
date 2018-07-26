/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model;

import java.io.InputStream;

public class ResourceProviderAdapter implements ResourceProvider {

  private org.mule.runtime.dsl.api.ResourceProvider delegate;

  public ResourceProviderAdapter(org.mule.runtime.dsl.api.ResourceProvider resourceProvider) {
    this.delegate = resourceProvider;
  }

  @Override
  public InputStream getResourceAsStream(String uri) {
    return delegate.getResourceAsStream(uri);
  }
}
