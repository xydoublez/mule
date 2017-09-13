/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.privileged.processor.chain;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.setMuleContextIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import static org.mule.runtime.core.privileged.processor.MessageProcessors.processToApply;
import static org.slf4j.LoggerFactory.getLogger;
import static reactor.core.publisher.Flux.from;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.processor.MessageProcessorChain;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.internal.processor.interceptor.ReactiveInterceptorAdapter;
import org.mule.runtime.core.internal.processor.interceptor.ReactiveInterceptorsEnricher;
import org.mule.runtime.core.privileged.component.AbstractExecutableComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

/**
 * Builder needs to return a composite rather than the first MessageProcessor in the chain. This is so that if this chain is
 * nested in another chain the next MessageProcessor in the parent chain is not injected into the first in the nested chain.
 */
abstract class AbstractMessageProcessorChain extends AbstractExecutableComponent implements MessageProcessorChain {

  private static final Logger LOGGER = getLogger(AbstractMessageProcessorChain.class);

  private final String name;
  private final List<Processor> processors;
  private ProcessingStrategy processingStrategy;
  private ReactiveInterceptorsEnricher interceptorsEnricher;
  private List<ReactiveInterceptorAdapter> additionalInterceptors = new LinkedList<>();
  // private StreamingManager streamingManager;

  AbstractMessageProcessorChain(String name, Optional<ProcessingStrategy> processingStrategyOptional,
                                List<Processor> processors) {
    this.name = name;
    this.processingStrategy = processingStrategyOptional.orElse(null);
    this.processors = processors;
  }

  @Override
  public BaseEvent process(BaseEvent event) throws MuleException {
    return processToApply(event, this);
  }

  @Override
  public Publisher<BaseEvent> apply(Publisher<BaseEvent> publisher) {
    // List<BiFunction<Processor, ReactiveProcessor, ReactiveProcessor>> interceptors = resolveInterceptors();
    Flux<BaseEvent> stream = from(publisher);
    for (Processor processor : getProcessorsToExecute()) {
      // Perform assembly for processor chain by transforming the existing publisher with a publisher function for each processor
      // along with the interceptors that decorate it.
      stream = stream.transform(interceptorsEnricher.applyInterceptors(processor));
    }
    return stream;
  }

  protected List<Processor> getProcessorsToExecute() {
    return processors;
  }

  @Override
  public String toString() {
    StringBuilder string = new StringBuilder();
    string.append(getClass().getSimpleName());
    if (!isBlank(name)) {
      string.append(format(" '%s' ", name));
    }

    Iterator<Processor> mpIterator = processors.iterator();

    final String nl = format("%n");

    // TODO have it print the nested structure with indents increasing for nested MPCs
    if (mpIterator.hasNext()) {
      string.append(format("%n[ "));
      while (mpIterator.hasNext()) {
        Processor mp = mpIterator.next();
        final String indented = replace(mp.toString(), nl, format("%n  "));
        string.append(format("%n  %s", indented));
        if (mpIterator.hasNext()) {
          string.append(", ");
        }
      }
      string.append(format("%n]"));
    }

    return string.toString();
  }

  @Override
  public List<Processor> getMessageProcessors() {
    return processors;
  }

  public Optional<ProcessingStrategy> getProcessingStrategy() {
    return Optional.ofNullable(processingStrategy);
  }

  protected List<Processor> getMessageProcessorsForLifecycle() {
    return processors;
  }

  @Override
  public void setMuleContext(MuleContext muleContext) {
    super.setMuleContext(muleContext);
    setMuleContextIfNeeded(getMessageProcessorsForLifecycle(), muleContext);
  }

  @Override
  public void initialise() throws InitialisationException {
    initialiseIfNeeded(getMessageProcessorsForLifecycle(), true, muleContext);

    interceptorsEnricher = ReactiveInterceptorsEnricher.create(muleContext, processingStrategy);
  }

  @Override
  public void start() throws MuleException {
    List<Processor> startedProcessors = new ArrayList<>();
    try {
      for (Processor processor : getMessageProcessorsForLifecycle()) {
        if (processor instanceof Startable) {
          ((Startable) processor).start();
          startedProcessors.add(processor);
        }
      }
    } catch (MuleException e) {
      stopIfNeeded(getMessageProcessorsForLifecycle());
      throw e;
    }
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(getMessageProcessorsForLifecycle());
  }

  @Override
  public void dispose() {
    disposeIfNeeded(getMessageProcessorsForLifecycle(), LOGGER);
  }

}
