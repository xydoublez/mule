/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.component.ComponentIdentifier.builder;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.UNKNOWN;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import javax.xml.namespace.QName;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComplexParameterValueAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.component.AbstractComponent;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.dsl.ConfigurationProperty;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.internal.dsl.properties.CompositeConfigurationPropertiesProvider;
import org.mule.runtime.core.internal.dsl.properties.ConfigurationPropertiesResolver;
import org.mule.runtime.core.internal.dsl.properties.DefaultConfigurationParameters;
import org.mule.runtime.core.internal.dsl.properties.DefaultConfigurationPropertiesResolver;
import org.mule.runtime.core.internal.dsl.properties.DefaultConfigurationProperty;
import org.mule.runtime.core.internal.dsl.properties.EnvironmentPropertiesConfigurationProvider;
import org.mule.runtime.core.internal.dsl.properties.FileConfigurationPropertiesProvider;
import org.mule.runtime.core.internal.dsl.properties.GlobalPropertyConfigurationPropertiesProvider;
import org.mule.runtime.core.internal.dsl.properties.MapConfigurationPropertiesProvider;
import org.mule.runtime.core.internal.dsl.properties.PropertiesResolverConfigurationProperties;
import org.mule.runtime.dsl.api.ResourceProvider;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;
import org.mule.runtime.dsl.api.properties.ConfigurationParameters;
import org.mule.runtime.dsl.api.properties.ConfigurationPropertiesProvider;
import org.mule.runtime.dsl.api.properties.ConfigurationPropertiesProviderFactory;

import com.google.common.collect.ImmutableMap;

public class ArtifactConfigurationPropertiesProviderFactory {

  public static final String GLOBAL_PROPERTY = "global-property";
  public static final ComponentIdentifier GLOBAL_PROPERTY_IDENTIFIER =
      builder().namespace(CORE_PREFIX).name(GLOBAL_PROPERTY).build();
  private final ResourceProvider resourceProvider;

  public ArtifactConfigurationPropertiesProviderFactory(ResourceProvider resourceProvider) {
    this.resourceProvider = resourceProvider;
  }

  public ConfigurationPropertiesResolver create(ArtifactAst artifactAst,
                                                Optional<ConfigurationProperties> parentConfigurationProperties,
                                                Map<String, String> deploymentProperties) {
    return createConfigurationAttributeResolver(artifactAst, parentConfigurationProperties, deploymentProperties);
  }

  private ConfigurationPropertiesResolver createConfigurationAttributeResolver(ArtifactAst artifactAst,
                                                                               Optional<ConfigurationProperties> parentConfigurationProperties,
                                                                               Map<String, String> deploymentProperties) {

    ConfigurationPropertiesProvider deploymentPropertiesConfigurationProperties = null;
    if (!deploymentProperties.isEmpty()) {
      deploymentPropertiesConfigurationProperties =
          new MapConfigurationPropertiesProvider(deploymentProperties,
                                                 "Deployment properties");
    }

    EnvironmentPropertiesConfigurationProvider environmentPropertiesConfigurationProvider =
        new EnvironmentPropertiesConfigurationProvider();
    ConfigurationPropertiesProvider globalPropertiesConfigurationAttributeProvider =
        createProviderFromGlobalProperties(artifactAst);

    DefaultConfigurationPropertiesResolver environmentPropertiesConfigurationPropertiesResolver =
        new DefaultConfigurationPropertiesResolver(empty(), environmentPropertiesConfigurationProvider);

    DefaultConfigurationPropertiesResolver localResolver =
        new DefaultConfigurationPropertiesResolver(of(new DefaultConfigurationPropertiesResolver(
                                                                                                 deploymentPropertiesConfigurationProperties != null
                                                                                                     ? of(new DefaultConfigurationPropertiesResolver(of(environmentPropertiesConfigurationPropertiesResolver),
                                                                                                                                                     deploymentPropertiesConfigurationProperties))
                                                                                                     : of(environmentPropertiesConfigurationPropertiesResolver),
                                                                                                 globalPropertiesConfigurationAttributeProvider)),
                                                   environmentPropertiesConfigurationProvider);
    List<ConfigurationPropertiesProvider> configConfigurationPropertiesProviders =
        getConfigurationPropertiesProvidersFromComponents(artifactAst, localResolver);
    FileConfigurationPropertiesProvider externalPropertiesConfigurationProvider =
        new FileConfigurationPropertiesProvider(resourceProvider, "External files");

    Optional<org.mule.runtime.core.internal.dsl.properties.ConfigurationPropertiesResolver> parentConfigurationPropertiesResolver =
        of(localResolver);
    if (parentConfigurationProperties.isPresent()) {
      parentConfigurationPropertiesResolver =
          of(new DefaultConfigurationPropertiesResolver(empty(), new ConfigurationPropertiesProvider() {

            @Override
            public Optional<ConfigurationProperty> getConfigurationProperty(String configurationAttributeKey) {
              return parentConfigurationProperties.get().resolveProperty(configurationAttributeKey)
                  .map(value -> new DefaultConfigurationProperty(parentConfigurationProperties, configurationAttributeKey,
                                                                 value));
            }

            @Override
            public String getDescription() {
              return "Domain properties";
            }
          }));
    }

    if (!configConfigurationPropertiesProviders.isEmpty()) {
      CompositeConfigurationPropertiesProvider configurationAttributesProvider =
          new CompositeConfigurationPropertiesProvider(configConfigurationPropertiesProviders);
      parentConfigurationPropertiesResolver = of(new DefaultConfigurationPropertiesResolver(
                                                                                            deploymentPropertiesConfigurationProperties != null
                                                                                                ?
                                                                                                // deployment properties provider
                                                                                                // has to go as parent here so we
                                                                                                // can reference them from
                                                                                                // configuration properties files
                                                                                                of(new DefaultConfigurationPropertiesResolver(parentConfigurationPropertiesResolver,
                                                                                                                                              deploymentPropertiesConfigurationProperties))
                                                                                                : parentConfigurationPropertiesResolver,
                                                                                            configurationAttributesProvider));
    } else if (deploymentPropertiesConfigurationProperties != null) {
      parentConfigurationPropertiesResolver =
          of(new DefaultConfigurationPropertiesResolver(parentConfigurationPropertiesResolver,
                                                        deploymentPropertiesConfigurationProperties));
    }

    DefaultConfigurationPropertiesResolver globalPropertiesConfigurationPropertiesResolver =
        new DefaultConfigurationPropertiesResolver(parentConfigurationPropertiesResolver,
                                                   globalPropertiesConfigurationAttributeProvider);
    DefaultConfigurationPropertiesResolver systemPropertiesResolver =
        new DefaultConfigurationPropertiesResolver(of(globalPropertiesConfigurationPropertiesResolver),
                                                   environmentPropertiesConfigurationProvider);

    DefaultConfigurationPropertiesResolver externalPropertiesResolver =
        new DefaultConfigurationPropertiesResolver(
                                                   deploymentPropertiesConfigurationProperties != null ?
                                                   // deployment properties provider has to go as parent here so we can reference
                                                   // them from external files
                                                       of(new DefaultConfigurationPropertiesResolver(of(systemPropertiesResolver),
                                                                                                     deploymentPropertiesConfigurationProperties))
                                                       : of(systemPropertiesResolver),
                                                   externalPropertiesConfigurationProvider);
    PropertiesResolverConfigurationProperties configurationProperties;

    if (deploymentPropertiesConfigurationProperties == null) {
      configurationProperties = new PropertiesResolverConfigurationProperties(externalPropertiesResolver);
    } else {
      // finally the first configuration properties resolver should be deployment properties as they have precedence over the rest
      configurationProperties =
          new PropertiesResolverConfigurationProperties(new DefaultConfigurationPropertiesResolver(of(externalPropertiesResolver),
                                                                                                   deploymentPropertiesConfigurationProperties));
    }

    try {
      initialiseIfNeeded(configurationProperties.getConfigurationPropertiesResolver());
    } catch (InitialisationException e) {
      throw new MuleRuntimeException(e);
    }
    return configurationProperties.getConfigurationPropertiesResolver();
  }

  private List<ConfigurationPropertiesProvider> getConfigurationPropertiesProvidersFromComponents(ArtifactAst artifactAst,
                                                                                                  org.mule.runtime.core.internal.dsl.properties.ConfigurationPropertiesResolver localResolver) {

    Map<ComponentIdentifier, ConfigurationPropertiesProviderFactory> providerFactoriesMap = new HashMap<>();

    //TODO add code to load the providers from the APIs in spring config module.
    ServiceLoader<ConfigurationPropertiesProviderFactory> providerFactories =
        java.util.ServiceLoader.load(ConfigurationPropertiesProviderFactory.class);
    providerFactories.forEach(service -> {
      ComponentIdentifier componentIdentifier = service.getSupportedComponentIdentifier();
      if (providerFactoriesMap.containsKey(componentIdentifier)) {
        throw new MuleRuntimeException(createStaticMessage("Multiple configuration providers for component: "
            + componentIdentifier));
      }
      providerFactoriesMap.put(componentIdentifier, service);
    });

    List<ConfigurationPropertiesProvider> configConfigurationPropertiesProviders = new ArrayList<>();
    List<ComponentAst> globalComponents = artifactAst.getGlobalComponents();
    for (ComponentAst globalComponentAst : globalComponents) {
      ComponentIdentifier globalComponentAstComponentIdentifier = globalComponentAst.getComponentIdentifier();
      if (!providerFactoriesMap.containsKey(globalComponentAstComponentIdentifier)) {
        continue;
      }

      DefaultConfigurationParameters.Builder configurationParametersBuilder =
          DefaultConfigurationParameters.builder();
      ConfigurationParameters configurationParameters =
          resolveConfigurationParameters(configurationParametersBuilder, globalComponentAst, localResolver);
      ConfigurationPropertiesProvider provider = providerFactoriesMap.get(globalComponentAstComponentIdentifier)
          .createProvider(configurationParameters, resourceProvider);
      if (provider instanceof Component) {
        Component providerComponent = (Component) provider;
        TypedComponentIdentifier typedComponentIdentifier = TypedComponentIdentifier.builder()
            .type(UNKNOWN).identifier(globalComponentAstComponentIdentifier).build();
        DefaultComponentLocation.DefaultLocationPart locationPart =
            new DefaultComponentLocation.DefaultLocationPart(globalComponentAstComponentIdentifier.getName(),
                                                             of(typedComponentIdentifier),
                                                             of(globalComponentAst.getSourceCodeLocation().getFilename()),
                                                             of(globalComponentAst.getSourceCodeLocation().getStartLine()));
        providerComponent.setAnnotations(ImmutableMap.<QName, Object>builder()
            .put(AbstractComponent.LOCATION_KEY,
                 new DefaultComponentLocation(of(globalComponentAstComponentIdentifier.getName()),
                                              singletonList(locationPart)))
            .build());
      }
      configConfigurationPropertiesProviders.add(provider);

      try {
        initialiseIfNeeded(provider);
      } catch (InitialisationException e) {
        throw new MuleRuntimeException(e);
      }

    }
    return configConfigurationPropertiesProviders;
  }

  private ConfigurationParameters resolveConfigurationParameters(DefaultConfigurationParameters.Builder configurationParametersBuilder,
                                                                 ComponentAst componentAst,
                                                                 org.mule.runtime.core.internal.dsl.properties.ConfigurationPropertiesResolver localResolver) {

    // TODO review this code. Local resolver should be applied later on or define what to do if it fails. Also we are not setting
    // the ResolvedValueResult
    componentAst.getParameters()
        .forEach(parameterAst -> {
          if (parameterAst.getValue() instanceof ComplexParameterValueAst) {
            DefaultConfigurationParameters.Builder childParametersBuilder = DefaultConfigurationParameters.builder();
            ConfigurationParameters childConfigurationParameter =
                resolveConfigurationParameters(childParametersBuilder,
                                               ((ComplexParameterValueAst) parameterAst.getValue()).getComponent(),
                                               localResolver);
            configurationParametersBuilder.withComplexParameter(
                                                                ((ComplexParameterValueAst) parameterAst.getValue())
                                                                    .getComponent().getComponentIdentifier(),
                                                                childConfigurationParameter);
          } else {
            configurationParametersBuilder
                .withSimpleParameter(parameterAst.getParameterIdentifier().getIdentifier().getName(),
                                     localResolver.resolveValue(parameterAst.getValueAsSimpleParameterValueAst().getRawValue()));
          }
        });
    return configurationParametersBuilder.build();
  }

  private ConfigurationPropertiesProvider createProviderFromGlobalProperties(ArtifactAst artifactAst) {
    final Map<String, ConfigurationProperty> globalProperties = new HashMap<>();

    List<ComponentAst> globalPropertyComponents = artifactAst.getAllGlobalComponentsById(GLOBAL_PROPERTY_IDENTIFIER);
    globalPropertyComponents.forEach(globalPropertyComponentAst -> {
      String key =
          globalPropertyComponentAst.getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("name").build())
              .map(parameterAst -> parameterAst.getValueAsSimpleParameterValueAst().getRawValue()).orElse(null);
      String rawValue =
          globalPropertyComponentAst.getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("value").build())
              .map(parameterAst -> parameterAst.getValueAsSimpleParameterValueAst().getRawValue()).orElse(null);
      if (key != null && rawValue != null) {
        globalProperties.put(key,
                             new DefaultConfigurationProperty(String.format("global-property - file: %s - lineNumber %s",
                                                                            globalPropertyComponentAst.getSourceCodeLocation()
                                                                                .getFilename(),
                                                                            globalPropertyComponentAst.getSourceCodeLocation()
                                                                                .getStartLine()),
                                                              key, rawValue));

      }
    });
    return new GlobalPropertyConfigurationPropertiesProvider(globalProperties);
  }


}
