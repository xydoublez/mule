/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.config.factory;

import static org.mule.runtime.core.internal.execution.ClassLoaderInjectorInvocationHandler.createClassLoaderInjectorInvocationHandler;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.el.ExtendedExpressionManager;
import org.mule.runtime.core.internal.el.DefaultExpressionManager;
import org.mule.runtime.core.internal.registry.guice.provider.GuiceProvider;

import javax.inject.Inject;

/**
 * Creates the default {@link org.mule.runtime.core.api.el.ExpressionManager}
 * <p/>
 * This factory creates a proxy on top of the real expression manager. That proxy is used to set the right classloader on the
 * current thread's context classloader before calling any method on the delegate object.
 *
 * @since 4.2.0
 */
public class DefaultExpressionManagerProvider extends GuiceProvider<ExtendedExpressionManager> {

  @Inject
  private MuleContext muleContext;

  @Override
  protected ExtendedExpressionManager doGet() {
    DefaultExpressionManager delegate = new DefaultExpressionManager();
    try {
      muleContext.getInjector().inject(delegate);
    } catch (MuleException e) {
      throw new MuleRuntimeException(e);
    }

    return (ExtendedExpressionManager) createClassLoaderInjectorInvocationHandler(delegate,
                                                                                  muleContext.getExecutionClassLoader());
  }
}
