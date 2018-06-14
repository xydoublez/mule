/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.mule.runtime.api.connectivity.ConnectivityTestingService.CONNECTIVITY_TESTING_SERVICE_KEY;
import static org.mule.runtime.api.metadata.MetadataService.METADATA_SERVICE_KEY;
import static org.mule.runtime.api.serialization.ObjectSerializer.DEFAULT_OBJECT_SERIALIZER_NAME;
import static org.mule.runtime.api.store.ObjectStoreManager.BASE_IN_MEMORY_OBJECT_STORE_KEY;
import static org.mule.runtime.api.store.ObjectStoreManager.BASE_PERSISTENT_OBJECT_STORE_KEY;
import static org.mule.runtime.api.value.ValueProviderService.VALUE_PROVIDER_SERVICE_KEY;
import static org.mule.runtime.config.api.LazyComponentInitializer.LAZY_COMPONENT_INITIALIZER_SERVICE_KEY;
import static org.mule.runtime.config.internal.InjectParamsFromContextServiceProxy.createInjectProviderParamsServiceProxy;
import static org.mule.runtime.core.api.config.MuleProperties.LOCAL_OBJECT_LOCK_FACTORY;
import static org.mule.runtime.core.api.config.MuleProperties.LOCAL_OBJECT_STORE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_CLUSTER_SERVICE;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_COMPONENT_INITIAL_STATE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_CONFIGURATION_PROPERTIES;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_CONNECTION_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_CONVERTER_RESOLVER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_DEFAULT_MESSAGE_PROCESSING_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_DEFAULT_RETRY_POLICY_TEMPLATE;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_EXCEPTION_LOCATION_PROVIDER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_EXPRESSION_LANGUAGE;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_EXPRESSION_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_EXTENSION_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCAL_QUEUE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCAL_STORE_IN_MEMORY;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCAL_STORE_PERSISTENT;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCK_FACTORY;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_LOCK_PROVIDER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_MESSAGE_PROCESSING_FLOW_TRACE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_MULE_CONFIGURATION;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_MULE_CONTEXT;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_MULE_STREAM_CLOSER_SERVICE;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_NOTIFICATION_DISPATCHER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_NOTIFICATION_HANDLER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_NOTIFICATION_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_OBJECT_NAME_PROCESSOR;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_POLICY_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_POLICY_MANAGER_STATE_HANDLER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_PROCESSING_TIME_WATCHER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_QUEUE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_SCHEDULER_BASE_CONFIG;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_SCHEDULER_POOLS_CONFIG;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_SECURITY_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STATISTICS;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STORE_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STREAMING_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_TIME_SUPPLIER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_TRANSACTION_FACTORY_LOCATOR;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_TRANSACTION_MANAGER;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_TRANSFORMATION_SERVICE;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.POLICY;
import static org.mule.runtime.core.internal.interception.ProcessorInterceptorManager.PROCESSOR_INTERCEPTOR_MANAGER_REGISTRY_KEY;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.config.custom.ServiceConfigurator;
import org.mule.runtime.api.exception.ErrorTypeRepository;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lock.LockFactory;
import org.mule.runtime.api.notification.ConnectionNotification;
import org.mule.runtime.api.notification.ConnectionNotificationListener;
import org.mule.runtime.api.notification.CustomNotification;
import org.mule.runtime.api.notification.CustomNotificationListener;
import org.mule.runtime.api.notification.ExceptionNotification;
import org.mule.runtime.api.notification.ExceptionNotificationListener;
import org.mule.runtime.api.notification.ExtensionNotification;
import org.mule.runtime.api.notification.ExtensionNotificationListener;
import org.mule.runtime.api.notification.ManagementNotification;
import org.mule.runtime.api.notification.ManagementNotificationListener;
import org.mule.runtime.api.notification.Notification;
import org.mule.runtime.api.notification.NotificationListener;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.api.notification.SecurityNotification;
import org.mule.runtime.api.notification.SecurityNotificationListener;
import org.mule.runtime.api.notification.TransactionNotification;
import org.mule.runtime.api.notification.TransactionNotificationListener;
import org.mule.runtime.api.scheduler.SchedulerContainerPoolsConfig;
import org.mule.runtime.api.service.Service;
import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.config.internal.dsl.model.config.DefaultComponentInitialStateManager;
import org.mule.runtime.config.internal.factories.ExtensionManagerFactoryBean;
import org.mule.runtime.config.internal.factories.TransactionManagerFactoryBean;
import org.mule.runtime.config.internal.processor.MuleObjectNameProcessor;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.DefaultMuleConfiguration;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.core.api.config.builders.AbstractConfigurationBuilder;
import org.mule.runtime.core.api.context.notification.MuleContextNotification;
import org.mule.runtime.core.api.context.notification.MuleContextNotificationListener;
import org.mule.runtime.core.api.event.EventContextService;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.core.api.retry.policy.NoRetryPolicyTemplate;
import org.mule.runtime.core.api.streaming.DefaultStreamingManager;
import org.mule.runtime.core.api.util.queue.QueueManager;
import org.mule.runtime.core.internal.cluster.DefaultClusterService;
import org.mule.runtime.core.internal.component.DefaultConfigurationComponentLocator;
import org.mule.runtime.core.internal.config.CustomService;
import org.mule.runtime.core.internal.config.CustomServiceRegistry;
import org.mule.runtime.core.internal.connection.DelegateConnectionManagerAdapter;
import org.mule.runtime.core.internal.connectivity.DefaultConnectivityTestingService;
import org.mule.runtime.core.internal.context.MuleContextWithRegistry;
import org.mule.runtime.core.internal.context.notification.DefaultNotificationDispatcher;
import org.mule.runtime.core.internal.context.notification.DefaultNotificationListenerRegistry;
import org.mule.runtime.core.internal.context.notification.MessageProcessingFlowTraceManager;
import org.mule.runtime.core.internal.el.mvel.MVELExpressionLanguage;
import org.mule.runtime.core.internal.event.DefaultEventContextService;
import org.mule.runtime.core.internal.exception.MessagingExceptionLocationProvider;
import org.mule.runtime.core.internal.execution.MuleMessageProcessingManager;
import org.mule.runtime.core.internal.lock.MuleLockFactory;
import org.mule.runtime.core.internal.lock.SingleServerLockProvider;
import org.mule.runtime.core.internal.management.stats.DefaultProcessingTimeWatcher;
import org.mule.runtime.core.internal.metadata.MuleMetadataService;
import org.mule.runtime.core.internal.policy.DefaultPolicyManager;
import org.mule.runtime.core.internal.policy.DefaultPolicyStateHandler;
import org.mule.runtime.core.internal.processor.interceptor.DefaultProcessorInterceptorManager;
import org.mule.runtime.core.internal.registry.DefaultRegistry;
import org.mule.runtime.core.internal.registry.InternalRegistryBuilder;
import org.mule.runtime.core.internal.registry.guice.DefaultRegistryBootstrap;
import org.mule.runtime.core.internal.registry.guice.LocalInMemoryObjectStoreProvider;
import org.mule.runtime.core.internal.registry.guice.LocalLockFactoryProvider;
import org.mule.runtime.core.internal.registry.guice.LocalObjectStoreManagerProvider;
import org.mule.runtime.core.internal.registry.guice.LocalPersistentObjectStoreProvider;
import org.mule.runtime.core.internal.registry.guice.LocalQueueManagerProvider;
import org.mule.runtime.core.internal.security.DefaultMuleSecurityManager;
import org.mule.runtime.core.internal.time.LocalTimeSupplier;
import org.mule.runtime.core.internal.transaction.TransactionFactoryLocator;
import org.mule.runtime.core.internal.transformer.DynamicDataTypeConversionResolver;
import org.mule.runtime.core.internal.util.DefaultStreamCloserService;
import org.mule.runtime.core.internal.util.queue.TransactionalQueueManager;
import org.mule.runtime.core.internal.util.store.MuleDefaultObjectStoreFactory;
import org.mule.runtime.core.internal.util.store.MuleObjectStoreManager;
import org.mule.runtime.core.internal.value.MuleValueProviderService;
import org.mule.runtime.core.privileged.transformer.ExtendedTransformationService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Provider;

public class RegistryConfigurationBuilder extends AbstractConfigurationBuilder {

  private final InternalRegistryBuilder builder;
  private final ArtifactType artifactType;
  private final CustomServiceRegistry customServiceRegistry;
  private final MuleContextWithRegistry muleContext;

  private static final ImmutableSet<String> APPLICATION_ONLY_SERVICES = ImmutableSet.<String>builder()
      .add(OBJECT_SECURITY_MANAGER)
      .add(OBJECT_DEFAULT_MESSAGE_PROCESSING_MANAGER)
      .add(OBJECT_MULE_STREAM_CLOSER_SERVICE)
      .add(OBJECT_CONVERTER_RESOLVER)
      .add(OBJECT_PROCESSING_TIME_WATCHER)
      .add(OBJECT_EXCEPTION_LOCATION_PROVIDER)
      .add(OBJECT_MESSAGE_PROCESSING_FLOW_TRACE_MANAGER)
      .build();

  private static final ImmutableMap<String, String> OBJECT_STORE_NAME_TO_LOCAL_OBJECT_STORE_NAME =
      ImmutableMap.<String, String>builder()
          .put(BASE_IN_MEMORY_OBJECT_STORE_KEY, OBJECT_LOCAL_STORE_IN_MEMORY)
          .put(BASE_PERSISTENT_OBJECT_STORE_KEY, OBJECT_LOCAL_STORE_PERSISTENT)
          .build();

  // Do not use static field. BeanDefinitions are reused and produce weird behaviour
  private final List<Registration> defaultContextServices = unmodifiableList(asList(
      type(OBJECT_TRANSACTION_MANAGER, TransactionManagerFactoryBean.class),
      type(OBJECT_DEFAULT_RETRY_POLICY_TEMPLATE, NoRetryPolicyTemplate.class),
      type(OBJECT_EXPRESSION_LANGUAGE, MVELExpressionLanguage.class),
      type(OBJECT_EXPRESSION_MANAGER, DefaultExpressionManagerFactoryBean.class),
      type(OBJECT_EXTENSION_MANAGER, ExtensionManagerFactoryBean.class),
      type(OBJECT_TIME_SUPPLIER, LocalTimeSupplier.class),
      type(OBJECT_CONNECTION_MANAGER, DelegateConnectionManagerAdapter.class),
      type(METADATA_SERVICE_KEY, MuleMetadataService.class),
      type(OBJECT_MULE_CONFIGURATION, DefaultMuleConfiguration.class),
      type(VALUE_PROVIDER_SERVICE_KEY, MuleValueProviderService.class),
      type(OBJECT_TRANSACTION_FACTORY_LOCATOR, TransactionFactoryLocator.class),
      type(OBJECT_OBJECT_NAME_PROCESSOR, MuleObjectNameProcessor.class),
      type(OBJECT_POLICY_MANAGER, DefaultPolicyManager.class),
      type(PROCESSOR_INTERCEPTOR_MANAGER_REGISTRY_KEY, DefaultProcessorInterceptorManager.class),
      type(OBJECT_POLICY_MANAGER_STATE_HANDLER, DefaultPolicyStateHandler.class),
      registerNotificationManager(),
      type(OBJECT_NOTIFICATION_DISPATCHER, DefaultNotificationDispatcher.class),
      type(NotificationListenerRegistry.REGISTRY_KEY, DefaultNotificationListenerRegistry.class),
      type(EventContextService.REGISTRY_KEY, DefaultEventContextService.class),
      provider(BASE_IN_MEMORY_OBJECT_STORE_KEY, ObjectStore.class, LocalInMemoryObjectStoreProvider.class),
      instance(OBJECT_LOCAL_STORE_IN_MEMORY, new MuleDefaultObjectStoreFactory().createDefaultInMemoryObjectStore()),
      provider(BASE_PERSISTENT_OBJECT_STORE_KEY, ObjectStore.class, LocalPersistentObjectStoreProvider.class),
      instance(OBJECT_LOCAL_STORE_PERSISTENT, new MuleDefaultObjectStoreFactory().createDefaultPersistentObjectStore()),
      type(OBJECT_STORE_MANAGER, MuleObjectStoreManager.class),
      type(OBJECT_QUEUE_MANAGER, TransactionalQueueManager.class),
      type(OBJECT_SECURITY_MANAGER, DefaultMuleSecurityManager.class),
      type(OBJECT_DEFAULT_MESSAGE_PROCESSING_MANAGER, MuleMessageProcessingManager.class),
      type(OBJECT_MULE_STREAM_CLOSER_SERVICE, DefaultStreamCloserService.class),
      type(OBJECT_CONVERTER_RESOLVER, DynamicDataTypeConversionResolver.class),
      type(OBJECT_LOCK_FACTORY, MuleLockFactory.class),
      type(OBJECT_LOCK_PROVIDER, SingleServerLockProvider.class),
      type(OBJECT_PROCESSING_TIME_WATCHER, DefaultProcessingTimeWatcher.class),
      type(OBJECT_EXCEPTION_LOCATION_PROVIDER, MessagingExceptionLocationProvider.class),
      type(OBJECT_MESSAGE_PROCESSING_FLOW_TRACE_MANAGER, MessageProcessingFlowTraceManager.class),
      type(CONNECTIVITY_TESTING_SERVICE_KEY, DefaultConnectivityTestingService.class),
      type(OBJECT_COMPONENT_INITIAL_STATE_MANAGER, DefaultComponentInitialStateManager.class),
      type(OBJECT_STREAMING_MANAGER, DefaultStreamingManager.class),
      type(OBJECT_TRANSFORMATION_SERVICE, ExtendedTransformationService.class),
      instance(OBJECT_SCHEDULER_POOLS_CONFIG, SchedulerContainerPoolsConfig.getInstance()),
      type(OBJECT_SCHEDULER_BASE_CONFIG, SchedulerBaseConfigFactory.class),
      type(OBJECT_CLUSTER_SERVICE, DefaultClusterService.class),
      type(LAZY_COMPONENT_INITIALIZER_SERVICE_KEY, NoOpLazyComponentInitializer.class)
  ));

  private final DefaultConfigurationComponentLocator componentLocator;
  private final ConfigurationProperties configurationProperties;

  public RegistryConfigurationBuilder(MuleContext muleContext,
                                      ConfigurationProperties configurationProperties,
                                      ArtifactType artifactType,
                                      DefaultConfigurationComponentLocator componentLocator) {
    this.muleContext = (MuleContextWithRegistry) muleContext;
    this.builder = this.muleContext.getRegistryBuilder();
    this.configurationProperties = configurationProperties;
    this.customServiceRegistry = (CustomServiceRegistry) muleContext.getCustomizationService();
    this.artifactType = artifactType;
    this.componentLocator = componentLocator;
  }

  @Override
  protected void doConfigure(MuleContext muleContext) throws Exception {
    builder.registerObject(OBJECT_MULE_CONTEXT, muleContext);
    builder.registerObject(DEFAULT_OBJECT_SERIALIZER_NAME, muleContext.getObjectSerializer());
    builder.registerObject(OBJECT_CONFIGURATION_PROPERTIES, configurationProperties);
    builder.registerObject(ErrorTypeRepository.class.getName(), muleContext.getErrorTypeRepository());
    builder.registerObject(ConfigurationComponentLocator.REGISTRY_KEY, componentLocator);
    builder.registerObject(OBJECT_NOTIFICATION_HANDLER, muleContext.getNotificationManager());
    builder.registerObject(OBJECT_STATISTICS, muleContext.getStatistics());
    loadServiceConfigurators();

    defaultContextServices.stream()
        .filter(service -> !APPLICATION_ONLY_SERVICES.contains(service.key) || artifactType.equals(APP)
            || artifactType.equals(POLICY))
        .forEach(service -> register(service));

    registerBootstrapObjects();
    registerObjectStore();
    registerLockFactory();
    registerQueueManager();
    createCustomServices(muleContext);
  }

  private void loadServiceConfigurators() {
    new SpiServiceRegistry()
        .lookupProviders(ServiceConfigurator.class, Service.class.getClassLoader())
        .forEach(customizer -> customizer.configure(customServiceRegistry));
  }

  private void createCustomServices(MuleContext muleContext) {
    customServiceRegistry.getCustomServices().entrySet()
        .forEach(entry -> register(getCustomServiceRegistration(entry.getValue(), entry.getKey())));
  }

  private void register(Registration registration) {
    String serviceId = registration.key;
    registration = customServiceRegistry.getOverriddenService(serviceId)
        .map(customService -> getCustomServiceRegistration(customService, serviceId))
        .orElse(registration);

    registration.register();
  }

  private Registration getCustomServiceRegistration(CustomService customService, String serviceId) {
    Registration registration;

    Optional<Class> customServiceClass = customService.getServiceClass();
    Optional<Object> customServiceImpl = customService.getServiceImpl();
    if (customServiceClass.isPresent()) {
      registration = type(serviceId, customServiceClass.get());
    } else if (customServiceImpl.isPresent()) {
      if (customServiceImpl.get() instanceof Service) {
        registration = instance(serviceId, createInjectProviderParamsServiceProxy((Service) customServiceImpl.get(),
                                                                                  new DefaultRegistry(muleContext)));
      } else {
        registration = instance(serviceId, customServiceImpl.get());
      }
    } else {
      throw new IllegalStateException("A custom service must define a service class or instance");
    }

    // TODO: Replace this logic somehow
    //if (OBJECT_STORE_MANAGER.equals(serviceId)) {
    //  registration.setPrimary(true);
    //}

    return registration;
  }

  private void registerQueueManager() {
    AtomicBoolean customManagerDefined = new AtomicBoolean(false);
    customServiceRegistry.getOverriddenService(OBJECT_QUEUE_MANAGER).ifPresent(customService -> {
      customManagerDefined.set(true);
      register(getCustomServiceRegistration(customService, OBJECT_QUEUE_MANAGER));
    });

    if (customManagerDefined.get()) {
      register(type(OBJECT_LOCAL_QUEUE_MANAGER, TransactionalQueueManager.class));
    } else {
      register(provider(OBJECT_LOCAL_QUEUE_MANAGER, QueueManager.class, LocalQueueManagerProvider.class));
    }
  }

  private void registerLockFactory() {
    AtomicBoolean customLockFactoryWasDefined = new AtomicBoolean(false);
    customServiceRegistry.getOverriddenService(OBJECT_LOCK_FACTORY).ifPresent(customService -> {
      customLockFactoryWasDefined.set(true);
      register(getCustomServiceRegistration(customService, OBJECT_LOCK_FACTORY));
    });

    if (customLockFactoryWasDefined.get()) {
      register(type(OBJECT_LOCK_FACTORY, MuleLockFactory.class));
    } else {
      register(provider(LOCAL_OBJECT_LOCK_FACTORY, LockFactory.class, LocalLockFactoryProvider.class));
    }
  }

  private void registerObjectStore() {
    AtomicBoolean anyBaseStoreWasRedefined = new AtomicBoolean(false);
    OBJECT_STORE_NAME_TO_LOCAL_OBJECT_STORE_NAME.entrySet().forEach(objectStoreLocal -> customServiceRegistry
        .getOverriddenService(objectStoreLocal.getKey()).ifPresent(customService -> {
          anyBaseStoreWasRedefined.set(true);
          register(getCustomServiceRegistration(customService, objectStoreLocal.getKey()));
        }));

    if (anyBaseStoreWasRedefined.get()) {
      MuleObjectStoreManager local = new MuleObjectStoreManager();
      local.setBasePersistentStoreKey(OBJECT_LOCAL_STORE_PERSISTENT);
      local.setBaseTransientStoreKey(OBJECT_LOCAL_STORE_IN_MEMORY);

      register(instance(LOCAL_OBJECT_STORE_MANAGER, local));
    } else {
      register(provider(LOCAL_OBJECT_STORE_MANAGER, ObjectStoreManager.class, LocalObjectStoreManagerProvider.class));
    }
  }

  private Registration registerNotificationManager() {
    ServerNotificationManagerConfigurator configurator = new ServerNotificationManagerConfigurator();
    configurator.setEnabledNotifications(
        ImmutableList.<NotificationConfig<? extends Notification, ? extends NotificationListener>>builder()
            .add(new NotificationConfig.EnabledNotificationConfig<>(MuleContextNotificationListener.class,
                                                                    MuleContextNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(SecurityNotificationListener.class,
                                                                    SecurityNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(ManagementNotificationListener.class,
                                                                    ManagementNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(ConnectionNotificationListener.class,
                                                                    ConnectionNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(CustomNotificationListener.class, CustomNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(ExceptionNotificationListener.class,
                                                                    ExceptionNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(TransactionNotificationListener.class,
                                                                    TransactionNotification.class))
            .add(new NotificationConfig.EnabledNotificationConfig<>(ExtensionNotificationListener.class,
                                                                    ExtensionNotification.class))
            .build());

    return instance(OBJECT_NOTIFICATION_MANAGER, configurator);
  }

  private void registerBootstrapObjects() {
    try {
      DefaultRegistryBootstrap bootstrap =
          new DefaultRegistryBootstrap(artifactType, muleContext, (key, value) -> register(instance(key, value)));
      bootstrap.initialise();
    } catch (InitialisationException e) {
      throw new RuntimeException(e);
    }
  }

  private Registration instance(String key, Object value) {
    return new InstanceRegistration(key, value);
  }

  private Registration type(String key, Class<?> type) {
    return new TypeRegistration(key, type);
  }

  private Registration provider(String key, Class<?> objectType, Class<? extends Provider> providerType) {
    return new ProviderRegistration(key, objectType, providerType);
  }

  private abstract class Registration {

    protected final String key;

    public Registration(String key) {
      this.key = key;
    }

    protected abstract void register();
  }


  private class InstanceRegistration extends Registration {

    private final Object value;

    public InstanceRegistration(String name, Object value) {
      super(name);
      this.value = value;
    }

    @Override
    protected void register() {
      builder.registerObject(key, value);
    }
  }


  private class TypeRegistration extends Registration {

    private Class<?> type;

    public TypeRegistration(String key, Class<?> type) {
      super(key);
      this.type = type;
    }

    @Override
    protected void register() {
      builder.registerType(key, type);
    }
  }


  private class ProviderRegistration<T> extends Registration {

    private final Class<T> objectType;
    private final Class<? extends Provider<? extends T>> providerType;

    public ProviderRegistration(String key, Class<T> objectType, Class<? extends Provider<? extends T>> providerType) {
      super(key);
      this.objectType = objectType;
      this.providerType = providerType;
    }

    @Override
    protected void register() {
      builder.registerProvider(key, objectType, providerType);
    }
  }
}
