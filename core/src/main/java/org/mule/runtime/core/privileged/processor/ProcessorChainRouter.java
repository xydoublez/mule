/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.privileged.processor;

import static java.util.Collections.emptyList;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.AbstractAnnotatedObject;
import org.mule.runtime.core.DefaultEventContext;
import org.mule.runtime.core.api.processor.MessageProcessorChain;

import java.util.List;

/**
 * Component to be used that supports a collection of {@link MessageProcessorChain}
 */
public class ProcessorChainRouter extends AbstractAnnotatedObject {

  private List<MessageProcessorChain> processorChains = emptyList();
  private String name;

  public Event process(Event event) {
    org.mule.runtime.core.api.Event.Builder builder =
        org.mule.runtime.core.api.Event.builder(DefaultEventContext.create(null, getLocation()));
    org.mule.runtime.core.api.Event defaultEvent = builder.from(event).build();
    try {
      for (MessageProcessorChain processorChain : processorChains) {
        defaultEvent = processorChain.process(defaultEvent);
      }
    } catch (MuleException e) {
      throw new MuleRuntimeException(e);
    }
    return defaultEvent;
  }

}
