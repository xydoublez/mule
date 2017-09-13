/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.interceptor;

import static org.mule.runtime.core.api.context.notification.MessageProcessorNotification.MESSAGE_PROCESSOR_POST_INVOKE;
import static org.mule.runtime.core.api.context.notification.MessageProcessorNotification.MESSAGE_PROCESSOR_PRE_INVOKE;
import static org.mule.runtime.core.api.context.notification.MessageProcessorNotification.createFrom;
import static org.mule.runtime.core.api.util.StreamingUtils.updateEventForStreaming;
import static org.mule.runtime.core.privileged.event.PrivilegedEvent.setCurrentEvent;
import static reactor.core.publisher.Flux.from;
import static reactor.core.publisher.Flux.just;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.notification.MessageProcessorNotification;
import org.mule.runtime.core.api.context.notification.ServerNotificationManager;
import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.event.BaseEventContext;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.api.registry.RegistrationException;
import org.mule.runtime.core.api.streaming.StreamingManager;
import org.mule.runtime.core.api.util.MessagingExceptionResolver;
import org.mule.runtime.core.privileged.event.PrivilegedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * //TODO
 */
public class ReactiveInterceptorsEnricher {

  private final MuleContext muleContext;
  private final StreamingManager streamingManager;
  private final ProcessingStrategy processingStrategy;
  private final List<ReactiveInterceptorAdapter> additionalInterceptors;

  private ReactiveInterceptorsEnricher(MuleContext muleContext,
                                       StreamingManager streamingManager,
                                       ProcessingStrategy processingStrategy){

    this.muleContext = muleContext;
    this.streamingManager = streamingManager;
    this.processingStrategy = processingStrategy;
    this.additionalInterceptors = getAdditionalInterceptors();
  }

  public static ReactiveInterceptorsEnricher create(MuleContext muleContext){
    return create(muleContext, null);
  }

  public static ReactiveInterceptorsEnricher create(MuleContext muleContext, ProcessingStrategy processingStrategy){
    try {
      StreamingManager streamingManager = muleContext.getRegistry().lookupObject(StreamingManager.class);
      return new ReactiveInterceptorsEnricher(muleContext, streamingManager, processingStrategy);
    } catch (RegistrationException e) {
      throw new RuntimeException(e);
    }
  }

  public ReactiveProcessor applyInterceptors(Processor processor) {
    ReactiveProcessor interceptorWrapperProcessorFunction = processor;
    List<BiFunction<Processor, ReactiveProcessor, ReactiveProcessor>> interceptors = resolveInterceptors();
    // Take processor publisher function itself and transform it by applying interceptor transformations onto it.
    for (BiFunction<Processor, ReactiveProcessor, ReactiveProcessor> interceptor : interceptors) {
      interceptorWrapperProcessorFunction = interceptor.apply(processor, interceptorWrapperProcessorFunction);
    }
    return interceptorWrapperProcessorFunction;
  }

  private List<ReactiveInterceptorAdapter> getAdditionalInterceptors() {
    List<ReactiveInterceptorAdapter> additionalInterceptors = new ArrayList<>();
    muleContext.getProcessorInterceptorManager().getInterceptorFactories().forEach(interceptorFactory -> {
      ReactiveInterceptorAdapter reactiveInterceptorAdapter = new ReactiveInterceptorAdapter(interceptorFactory);
      try {
        muleContext.getInjector().inject(reactiveInterceptorAdapter);
      } catch (MuleException e) {
        throw new MuleRuntimeException(e);
      }

      additionalInterceptors.add(0, reactiveInterceptorAdapter
      );
    });

    return additionalInterceptors;
  }

  private List<BiFunction<Processor, ReactiveProcessor, ReactiveProcessor>> resolveInterceptors() {
    List<BiFunction<Processor, ReactiveProcessor, ReactiveProcessor>> interceptors =
      new ArrayList<>();

    // #1 Add all additional interceptors.
    interceptors.addAll(additionalInterceptors);

    // #2 Update MessagingException with failing processor if required, create Error and set error context.
    interceptors.add((processor, next) -> stream -> from(stream)
      .transform(next)
      .onErrorMap(MessagingException.class, resolveMessagingException(processor)));

    // #3 Update ThreadLocal event before processor execution once on processor thread.
    interceptors.add((processor, next) -> stream -> from(stream)
      .cast(PrivilegedEvent.class)
      .doOnNext(event -> setCurrentEvent(event))
      .cast(BaseEvent.class)
      .transform(next));

    // #4 Apply processing strategy. This is done here to ensure notifications and interceptors do not execute on async processor
    // threads which may be limited to avoid deadlocks.
    // Use anonymous ReactiveProcessor to apply processing strategy to processor + previous interceptors
    // while using the processing type of the processor itself.
    if (processingStrategy != null) {
      interceptors
        .add((processor, next) -> processingStrategy.onProcessor(new ReactiveProcessor() {

          @Override
          public Publisher<BaseEvent> apply(Publisher<BaseEvent> eventPublisher) {
            return next.apply(eventPublisher);
          }

          @Override
          public ProcessingType getProcessingType() {
            return processor.getProcessingType();
          }
        }));
    }

    // #5 Update ThreadLocal event after processor execution once back on flow thread.
    interceptors.add((processor, next) -> stream -> from(stream)
      .transform(next)
      .cast(PrivilegedEvent.class)
      .doOnNext(result -> setCurrentEvent(result))
      .cast(BaseEvent.class));

    // #6 Fire MessageProcessor notifications before and after processor execution.
    interceptors.add((processor, next) -> stream -> from(stream)
      .cast(PrivilegedEvent.class)
      .doOnNext(preNotification(processor))
      .cast(BaseEvent.class)
      .transform(next)
      .cast(PrivilegedEvent.class)
      .doOnNext(postNotification(processor))
      .doOnError(MessagingException.class, errorNotification(processor))
      .cast(BaseEvent.class));

    // #7 If the processor returns a CursorProvider, then have the StreamingManager manage it
    interceptors.add((processor, next) -> stream -> from(stream)
      .transform(next)
      .map(updateEventForStreaming(streamingManager)));


    // #8 Handle errors that occur during Processor execution. This is done outside to any scheduling to ensure errors in
    // scheduling such as RejectedExecutionException's can be handled cleanly
    interceptors.add((processor, next) -> stream -> from(stream).concatMap(event -> just(event)
      .transform(next)
      .onErrorResume(RejectedExecutionException.class,
                     throwable -> Mono.from(((BaseEventContext) event.getContext())
                                              .error(resolveException((Component) processor, event, throwable)))
                       .then(Mono.empty()))
      .onErrorResume(MessagingException.class,
                     throwable -> {
                       throwable = resolveMessagingException(processor).apply(throwable);
                       return Mono.from(((BaseEventContext) event.getContext()).error(throwable)).then(Mono.empty());
                     })));

    return interceptors;
  }

  private MessagingException resolveException(Component processor, BaseEvent event, Throwable throwable) {
    MessagingExceptionResolver exceptionResolver = new MessagingExceptionResolver(processor);
    return exceptionResolver.resolve(new MessagingException(event, throwable, processor), muleContext);
  }

  private Function<MessagingException, MessagingException> resolveMessagingException(Processor processor) {
    if (processor instanceof Component) {
      MessagingExceptionResolver exceptionResolver = new MessagingExceptionResolver((Component) processor);
      return exception -> exceptionResolver.resolve(exception, muleContext);
    } else {
      return exception -> exception;
    }
  }

  private Consumer<PrivilegedEvent> preNotification(Processor processor) {
    return event -> {
      if (event.isNotificationsEnabled()) {
        fireNotification(muleContext.getNotificationManager(), event, processor, null,
                         MESSAGE_PROCESSOR_PRE_INVOKE);
      }
    };
  }

  private Consumer<PrivilegedEvent> postNotification(Processor processor) {
    return event -> {
      if (event.isNotificationsEnabled()) {
        fireNotification(muleContext.getNotificationManager(), event, processor, null,
                         MESSAGE_PROCESSOR_POST_INVOKE);

      }
    };
  }

  private Consumer<MessagingException> errorNotification(Processor processor) {
    return exception -> {
      if (((PrivilegedEvent) exception.getEvent()).isNotificationsEnabled()) {
        fireNotification(muleContext.getNotificationManager(), exception.getEvent(), processor, exception,
                         MESSAGE_PROCESSOR_POST_INVOKE);
      }
    };
  }

  private void fireNotification(ServerNotificationManager serverNotificationManager, BaseEvent event, Processor processor,
                                MessagingException exceptionThrown, int action) {
    if (serverNotificationManager != null
      && serverNotificationManager.isNotificationEnabled(MessageProcessorNotification.class)) {

      if (((Component) processor).getLocation() != null) {
        serverNotificationManager
          .fireNotification(createFrom(event, ((Component) processor).getLocation(), (Component) processor,
                                       exceptionThrown, action));
      }
    }
  }

}
