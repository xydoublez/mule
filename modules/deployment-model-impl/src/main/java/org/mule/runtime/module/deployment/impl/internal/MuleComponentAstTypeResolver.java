/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal;

import static java.util.Collections.emptySet;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.util.ClassUtils.loadClass;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.dsl.ComponentBuildingDefinitionRegistry;
import org.mule.runtime.core.api.registry.ServiceRegistry;
import org.mule.runtime.core.internal.dsl.properties.RuntimeConfigurationException;
import org.mule.runtime.dsl.api.component.ComponentBuildingDefinition;
import org.mule.runtime.dsl.api.component.ComponentBuildingDefinitionProvider;
import org.mule.runtime.internal.dsl.DslConstants;
import org.mule.runtime.module.extension.internal.config.ExtensionBuildingDefinitionProvider;

//TODO find right place were this class belongs
public class MuleComponentAstTypeResolver {

  private final Optional<ComponentBuildingDefinitionRegistry> componentBuildingDefinitionRegistry;

  public MuleComponentAstTypeResolver(Optional<ComponentBuildingDefinitionRegistry> componentBuildingDefinitionRegistry) {
    this.componentBuildingDefinitionRegistry = componentBuildingDefinitionRegistry;
  }

  /**
   * Resolves the types of each component model when possible.
   */
  public void resolveComponentTypes(ArtifactAst artifactAst) {
    // TODO MULE-13894 enable this once changes are completed and no componentBuildingDefinition is needed
    // checkState(componentBuildingDefinitionRegistry.isPresent(),
    // "ApplicationModel was created without a " + ComponentBuildingDefinitionProvider.class.getName());
    componentBuildingDefinitionRegistry.ifPresent(buildingDefinitionRegistry -> {
      artifactAst.getAllNestedComponentsAst()
          .stream()
          .forEach(componentAst -> {
            Optional<ComponentBuildingDefinition<?>> buildingDefinition =
                buildingDefinitionRegistry.getBuildingDefinition(componentAst.getComponentIdentifier());
            buildingDefinition.map(definition -> {
              ObjectTypeVisitor typeDefinitionVisitor = new ObjectTypeVisitor(componentAst);
              definition.getTypeDefinition().visit(typeDefinitionVisitor);
              componentAst.setType(typeDefinitionVisitor.getType());
              return definition;
            }).orElseGet(() -> {
              String classParameter = componentAst
                  .getParameter(ComponentIdentifier.builder().namespace(DslConstants.CORE_PREFIX).namespace("class").build())
                  .map(parameterAst -> {
                    if (parameterAst.getValueAsSimpleParameterValueAst().getResolvedValueResult().isRight()) {
                      return parameterAst.getValueAsSimpleParameterValueAst().getResolvedValueResult().getRight()
                          .getResolvedValue().toString();
                    }
                    return null;
                  }).orElse(null);
              if (classParameter != null) {
                try {
                  componentAst.setType(
                                       loadClass(classParameter, Thread.currentThread().getContextClassLoader()));
                } catch (ClassNotFoundException e) {
                  throw new RuntimeConfigurationException(createStaticMessage(e.getMessage()), e);
                }
              }
              return null;
            });
          });
    });
  }

  /**
   * Registers the {@link ComponentBuildingDefinition} by searching in {@link ServiceRegistry} for
   * {@link ComponentBuildingDefinitionProvider providers}. {@link ExtensionBuildingDefinitionProvider} is handled in particular
   * to set it with the {@link List} of {@link ExtensionModel extensionModels}.
   *
   * @param serviceRegistry {@link ServiceRegistry} for look up operation.
   * @param classLoader {@link ClassLoader} to look up for providers.
   * @param componentBuildingDefinitionRegistry {@link ComponentBuildingDefinitionRegistry} to register the
   *        {@link ComponentBuildingDefinition}.
   * @param extensionModels list of {@link ExtensionModel} to be added in case if there is a
   *        {@link ExtensionBuildingDefinitionProvider} in the list of providers discovered.
   * @param providerListFunction {@link Function} to get the list of {@link ComponentBuildingDefinition} for extensions allowing
   *        them to be cached them and reused.
   */
  public static void registerComponentBuildingDefinitions(ServiceRegistry serviceRegistry, ClassLoader classLoader,
                                                          ComponentBuildingDefinitionRegistry componentBuildingDefinitionRegistry,
                                                          Optional<Set<ExtensionModel>> extensionModels,
                                                          Function<ComponentBuildingDefinitionProvider, List<ComponentBuildingDefinition>> providerListFunction) {

    serviceRegistry.lookupProviders(ComponentBuildingDefinitionProvider.class, classLoader)
        .forEach(componentBuildingDefinitionProvider -> {
          boolean isExtensionBuildingDefinitionProvider =
              componentBuildingDefinitionProvider instanceof ExtensionBuildingDefinitionProvider;
          if (isExtensionBuildingDefinitionProvider) {
            ((ExtensionBuildingDefinitionProvider) componentBuildingDefinitionProvider)
                .setExtensionModels(extensionModels.orElse(emptySet()));
          }
          componentBuildingDefinitionProvider.init();

          List<ComponentBuildingDefinition> componentBuildingDefinitions =
              isExtensionBuildingDefinitionProvider ? providerListFunction.apply(componentBuildingDefinitionProvider)
                  : componentBuildingDefinitionProvider.getComponentBuildingDefinitions();

          componentBuildingDefinitions.forEach(componentBuildingDefinitionRegistry::register);
        });
  }

}
