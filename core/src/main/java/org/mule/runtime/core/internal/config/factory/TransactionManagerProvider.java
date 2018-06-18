/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.config.factory;

import static org.mule.runtime.core.api.config.i18n.CoreMessages.failedToCreate;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.transaction.TransactionManagerFactory;
import org.mule.runtime.core.internal.registry.guice.provider.GuiceProvider;

import com.google.inject.Inject;

import javax.inject.Provider;
import javax.transaction.TransactionManager;

/**
 * {@link Provider} adapter for the configured {@link TransactionManagerFactory}.
 * <p/>
 * Creates a reference to the TransactionManager configured on the MuleContext. This is useful when you need to inject the
 * TransactionManager into other objects such as XA data Sources.
 * <p/>
 * This will first look for a single {@link TransactionManagerFactory} and use it to build the {@link TransactionManager}.
 * <p/>
 * If no {@link TransactionManagerFactory} is found, then it will look for {@link TransactionManager} instances.
 *
 * @since 4.2.0
 */
public class TransactionManagerProvider extends GuiceProvider<TransactionManager> {

  @Inject(optional = true)
  private TransactionManagerFactory txManagerFactory;

  @Inject
  private MuleContext muleContext;

  @Override
  protected TransactionManager doGet() {
    if (muleContext.isDisposing()) {
      // The txManager might be declared, but if it isn't used by the application it won't be created until the
      // muleContext is disposed.
      // At that point, there is no need to create the txManager.
      return null;
    } else if (txManagerFactory != null) {
      try {
        return txManagerFactory.create(muleContext.getConfiguration());
      } catch (Exception e) {
        throw new MuleRuntimeException(failedToCreate("transaction manager"), e);
      }
    }
    return null;
  }
}
