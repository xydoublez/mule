/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.config.internal.dsl.model.DependencyNode.Type.INNER;
import static org.mule.runtime.config.internal.dsl.model.DependencyNode.Type.TOP_LEVEL;
import static org.mule.runtime.config.internal.dsl.model.DependencyNode.Type.UNNAMED_TOP_LEVEL;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.artifact.ast.HasNestedComponentsAst;
import org.mule.runtime.api.artifact.ast.ParameterAst;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.dsl.ComponentBuildingDefinitionRegistry;
import org.mule.runtime.config.api.dsl.processor.AbstractAttributeDefinitionVisitor;
import org.mule.runtime.config.internal.BeanDependencyResolver;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.dsl.api.component.ComponentBuildingDefinition;
import org.mule.runtime.dsl.api.component.KeyAttributeDefinitionPair;

public class ArtifactAstDependencyResolver implements BeanDependencyResolver {

  private final ArtifactAst artifactAst;
  private final ComponentBuildingDefinitionRegistry componentBuildingDefinitionRegistry;
  private List<DependencyNode> missingElementNames = new ArrayList<>();
  private Set<DependencyNode> alwaysEnabledComponents = newHashSet();

  /**
   * Creates a new instance associated to a complete {@link ApplicationModel}.
   *
   * @param artifactAst the artifact {@link ArtifactAst}.
   * @param componentBuildingDefinitionRegistry the registry to find the {@link ComponentBuildingDefinition}s associated to each
   *        {@link ComponentModel} that must be resolved.
   */
  public ArtifactAstDependencyResolver(ArtifactAst artifactAst,
                                       ComponentBuildingDefinitionRegistry componentBuildingDefinitionRegistry) {
    this.artifactAst = artifactAst;
    this.componentBuildingDefinitionRegistry = componentBuildingDefinitionRegistry;
    fillAlwaysEnabledComponents();
  }

  private Set<DependencyNode> resolveComponentModelDependencies(ComponentAst componentAst) {
    final Set<DependencyNode> otherRequiredGlobalComponents = resolveComponentDependencies(componentAst);
    return findComponentModelsDependencies(otherRequiredGlobalComponents);
  }

  protected Set<DependencyNode> resolveComponentDependencies(ComponentAst requestedComponentAst) {
    Set<DependencyNode> otherDependencies = new HashSet<>();
    if (requestedComponentAst instanceof HasNestedComponentsAst) {
      ((HasNestedComponentsAst) requestedComponentAst).getNestedComponentsAst()
          .stream()
          .forEach(childComponentAst -> otherDependencies.addAll(resolveComponentDependencies(childComponentAst)));
    }


    final Set<String> parametersReferencingDependencies = new HashSet<>();
    componentBuildingDefinitionRegistry.getBuildingDefinition(requestedComponentAst.getComponentIdentifier())
        .ifPresent(buildingDefinition -> buildingDefinition.getAttributesDefinitions()
            .stream().forEach(attributeDefinition -> {
              attributeDefinition.accept(new AbstractAttributeDefinitionVisitor() {

                @Override
                public void onMultipleValues(KeyAttributeDefinitionPair[] definitions) {
                  stream(definitions)
                      .forEach(keyAttributeDefinitionPair -> keyAttributeDefinitionPair.getAttributeDefinition().accept(this));
                }

                @Override
                public void onReferenceSimpleParameter(String reference) {
                  parametersReferencingDependencies.add(reference);
                }

                @Override
                public void onSoftReferenceSimpleParameter(String softReference) {
                  parametersReferencingDependencies.add(softReference);
                }
              });
            }));

    for (String parametersReferencingDependency : parametersReferencingDependencies) {
      Optional<ParameterAst> parameter = requestedComponentAst
          .getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(parametersReferencingDependency).build());
      if (parameter.isPresent()) {
        appendTopLevelDependency(otherDependencies, requestedComponentAst, parametersReferencingDependency);
      }
    }

    // Special cases for flow-ref and configuration
    if (isCoreComponent(requestedComponentAst.getComponentIdentifier(), "flow-ref")) {
      appendTopLevelDependency(otherDependencies, requestedComponentAst, "name");
    } else if (isAggregatorComponent(requestedComponentAst, "aggregatorName")) {
      // TODO (MULE-14429): use extensionModel to get the dependencies instead of ComponentBuildingDefinition to solve cases like
      // this (flow-ref)
      // TODO review namespace used
      String name =
          requestedComponentAst.getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("aggregatorName").build())
              .filter(parameterAst -> parameterAst.getValueAsSimpleParameterValueAst().getResolvedValueResult().isRight())
              .map(parameterAst -> parameterAst.getValueAsSimpleParameterValueAst().getResolvedValueResult().getRight()
                  .getResolvedValue().toString())
              .orElse(null);
      DependencyNode dependency = new DependencyNode(name, INNER);
      if (artifactAst.getComponentByName(name).isPresent()) {
        otherDependencies.add(dependency);
      } else {
        missingElementNames.add(dependency);
      }
    } else if (isCoreComponent(requestedComponentAst.getComponentIdentifier(), "configuration")) {
      appendTopLevelDependency(otherDependencies, requestedComponentAst, "defaultErrorHandler-ref");
    }

    return otherDependencies;
  }

  protected Set<DependencyNode> findComponentModelsDependencies(Set<DependencyNode> componentModelNames) {
    Set<DependencyNode> componentsToSearchDependencies = new HashSet<>(componentModelNames);
    Set<DependencyNode> foundDependencies = new LinkedHashSet<>();
    Set<DependencyNode> alreadySearchedDependencies = new HashSet<>();
    do {
      componentsToSearchDependencies.addAll(foundDependencies);
      for (DependencyNode dependencyNode : componentsToSearchDependencies) {
        if (!alreadySearchedDependencies.contains(dependencyNode)) {
          alreadySearchedDependencies.add(dependencyNode);
          foundDependencies.addAll(resolveComponentDependencies(findRequiredComponentAst(dependencyNode.getComponentName())));
        }
      }
      foundDependencies.addAll(componentModelNames);

    } while (!foundDependencies.containsAll(componentsToSearchDependencies));
    return foundDependencies;
  }

  private void appendTopLevelDependency(Set<DependencyNode> otherDependencies, ComponentAst requestedComponentAst,
                                        String parametersReferencingDependency) {
    // TODO review parameter namespace used for lookup
    DependencyNode dependency =
        new DependencyNode(requestedComponentAst
            .getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(parametersReferencingDependency).build())
            .get().getValueAsSimpleParameterValueAst().getResolvedValueResult().getRight().getResolvedValue().toString(),
                           TOP_LEVEL);
    Optional<ComponentAst> componentAst = artifactAst.getGlobalComponentByName(dependency.getComponentName());
    if (componentAst.isPresent()) {
      otherDependencies.add(dependency);
    } else {
      missingElementNames.add(dependency);
    }
  }

  private boolean isCoreComponent(ComponentIdentifier componentIdentifier, String name) {
    return componentIdentifier.getNamespace().equals(CORE_PREFIX) && componentIdentifier.getName().equals(name);
  }

  private boolean isAggregatorComponent(ComponentAst componentAst, String referenceNameParameter) {
    // TODO review parameter identifier namespace
    return componentAst.getComponentIdentifier().getNamespace().equals("aggregators")
        && componentAst.getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(referenceNameParameter).build())
            .isPresent();
  }

  private ComponentAst findRequiredComponentAst(String name) {
    return artifactAst.getGlobalComponentByName(name)
        .orElseThrow(() -> new NoSuchComponentModelException(createStaticMessage("No named component with name " + name)));
  }

  protected ComponentAst findRequiredComponentAst(Location location) {
    final Reference<ComponentAst> foundComponentAstReference = new Reference<>();
    Optional<ComponentAst> globalComponent = artifactAst.getGlobalComponentByName(location.getGlobalName());
    globalComponent.ifPresent(componentAst -> findComponentWithLocation(componentAst, location)
        .ifPresent(foundComponentModel -> foundComponentAstReference.set(foundComponentModel)));
    if (foundComponentAstReference.get() == null) {
      throw new NoSuchComponentModelException(createStaticMessage("No object found at location " + location.toString()));
    }
    return foundComponentAstReference.get();
  }

  private Optional<ComponentAst> findComponentWithLocation(ComponentAst componentAst, Location location) {
    if (componentAst.getComponentLocation().getLocation().equals(location.toString())) {
      return of(componentAst);
    }

    if (componentAst instanceof HasNestedComponentsAst) {
      for (ComponentAst nestedComponentAst : ((HasNestedComponentsAst) componentAst).getNestedComponentsAst()) {
        Optional<ComponentAst> foundComponent = findComponentWithLocation(nestedComponentAst, location);
        if (foundComponent.isPresent()) {
          return foundComponent;
        }
      }
    }
    return empty();
  }

  /**
   * @param componentName the name attribute value of the component
   * @return the dependencies of the component with component name {@code #componentName}. An empty collection if there is no
   *         component with such name.
   */
  // TODO (MULE-14453: When creating ApplicationModel and ComponentModels inner beans should have a name so they can be later
  // retrieved)
  public Collection<String> resolveComponentDependencies(String componentName) {
    try {
      ComponentAst requiredComponentAst = findRequiredComponentAst(componentName);
      return resolveComponentModelDependencies(requiredComponentAst)
          .stream()
          .filter(dependencyNode -> dependencyNode.isTopLevel())
          .map(dependencyNode -> dependencyNode.getComponentName())
          .collect(toList());
    } catch (NoSuchComponentModelException e) {
      return emptyList();
    }
  }

  public ArtifactAst getArtifactAst() {
    return artifactAst;
  }

  @Override
  public List<Object> resolveBeanDependencies(String beanName) {
    return null;
  }

  public List<ComponentAst> findRequiredComponentModels(Predicate<ComponentAst> predicate) {
    List<ComponentAst> components = new ArrayList<>();
    artifactAst.getAllNestedComponentsAst()
        .stream()
        .forEach(componentAst -> {
          if (predicate.test(componentAst)) {
            components.add(componentAst);
          }
        });
    return components;

  }

  /**
   * @return the set of component names that must always be enabled.
   */
  public Set<DependencyNode> resolveAlwaysEnabledComponents() {
    return alwaysEnabledComponents;
  }

  private void fillAlwaysEnabledComponents() {
    this.artifactAst.getGlobalComponents()
        .stream()
        .forEach(componentAst -> {
          ComponentIdentifier componentIdentifier = componentAst.getComponentIdentifier();
          Optional<ComponentBuildingDefinition<?>> buildingDefinition =
              this.componentBuildingDefinitionRegistry.getBuildingDefinition(componentIdentifier);
          buildingDefinition.ifPresent(definition -> {
            if (definition.isAlwaysEnabled()) {
              Optional<ParameterAst> nameParameterOptional = componentAst.getNameParameter();
              if (nameParameterOptional.isPresent()
                  && nameParameterOptional.get().getValueAsSimpleParameterValueAst().getResolvedValueResult().isRight()) {
                alwaysEnabledComponents
                    .add(new DependencyNode(nameParameterOptional.get().getValueAsSimpleParameterValueAst()
                        .getResolvedValueResult().getRight().getResolvedValue().toString(), componentIdentifier, TOP_LEVEL));
              } else { // TODO this had the condition if (componentModel.isRoot()) but it has no sense
                alwaysEnabledComponents.add(new DependencyNode(null, componentIdentifier, UNNAMED_TOP_LEVEL));
              }
            }
          });
        });
  }

  public List<DependencyNode> getMissingDependencies() {
    return missingElementNames;
  }
}
