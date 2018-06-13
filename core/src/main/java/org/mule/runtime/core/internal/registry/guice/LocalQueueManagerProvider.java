/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;
import org.mule.runtime.core.api.util.queue.QueueManager;

import javax.inject.Inject;
import javax.inject.Named;

public class LocalQueueManagerProvider implements AliasProvider<QueueManager> {

  @Inject
  @Named(OBJECT_QUEUE_MANAGER)
  private QueueManager queueManager;

  @Override
  public QueueManager get() {
    return queueManager;
  }
}
