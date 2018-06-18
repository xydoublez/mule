/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice.provider;

import org.mule.runtime.api.component.Component;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.api.lifecycle.LifecycleStateAware;
import org.mule.runtime.core.api.component.DefaultConfigurationComponentLocator;
import org.mule.runtime.dsl.api.component.ComponentFactory;

import com.google.inject.Injector;

import javax.inject.Inject;
import javax.inject.Provider;

public abstract class GuiceProvider<T> implements Provider<T> {

  @Inject
  private MuleContext muleContext;

  @Inject
  private DefaultConfigurationComponentLocator componentLocator;

  @Inject
  protected Injector guiceInjector;

  @Override
  public final T get() {
    T object = doGet();

    guiceInjector.injectMembers(object);

    if (object instanceof MuleContextAware) {
      ((MuleContextAware) object).setMuleContext(muleContext);
    }

    if (object instanceof LifecycleStateAware) {
      ((LifecycleStateAware) object).setLifecycleState(muleContext.getLifecycleManager().getState());
    }

    if (!(object instanceof ComponentFactory) && object instanceof Component
        && ((Component) object).getLocation() != null) {

      componentLocator.addComponent((Component) object);
    }

    return object;
  }

  protected abstract T doGet();
}
