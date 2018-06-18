/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.config.factory;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mule.runtime.api.scheduler.SchedulerConfig.config;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.internal.registry.guice.provider.GuiceProvider;

import javax.inject.Inject;

/**
 * Builds a base {@link SchedulerConfig} to be provided to the calls to {@link SchedulerService}.
 * 
 * @since 4.2
 */
public class SchedulerBaseConfigProvider extends GuiceProvider<SchedulerConfig> {

  @Inject
  private MuleContext muleContext;

  @Override
  protected SchedulerConfig doGet() {
    return config().withPrefix(muleContext.getConfiguration().getId())
        .withShutdownTimeout(() -> muleContext.getConfiguration().getShutdownTimeout(), MILLISECONDS);
  }

}
