/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.config.factory;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.extension.ExtensionManager;
import org.mule.runtime.core.internal.registry.guice.provider.GuiceProvider;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A {@link Provider} which returns the {@link ExtensionManager} obtained through {@link MuleContext#getExtensionManager()}.
 * The purpose of that is to put the extensionManager in the context, even though it may exist before it.
 *
 * @since 4.2.0
 */
public class ExtensionManagerProvider extends GuiceProvider<ExtensionManager> {

  @Inject
  private MuleContext muleContext;

  @Override
  protected ExtensionManager doGet() {
    return muleContext.getExtensionManager();
  }
}
