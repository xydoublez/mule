/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.mule.runtime.config.internal.dsl.model.DependencyNode.Type.TOP_LEVEL;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.config.api.LazyComponentInitializer;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;

import com.google.common.collect.ImmutableSet;

/**
 * Generates the minimal required component set to create a configuration component (i.e.: file:config, ftp:connection, a flow
 * MP). This set is defined by the component dependencies.
 * <p/>
 * Based on the requested component, the {@link ComponentModel} configuration associated is introspected to find it dependencies
 * based on it's {@link org.mule.runtime.dsl.api.component.ComponentBuildingDefinition}. This process is recursively done for each
 * of the dependencies in order to find all the required {@link ComponentModel}s that must be created for the requested
 * {@link ComponentModel} to work properly.
 *
 * @since 4.0
 */
// TODO MULE-9688 - refactor this class when the ComponentModel becomes immutable
public class MinimalArtifactAstGenerator {

  private ArtifactAstDependencyResolver dependencyResolver;

  /**
   * Creates a new instance.
   * 
   * @param dependencyResolver a {@link ArtifactAstDependencyResolver} associated with an {@link ApplicationModel}
   */
  public MinimalArtifactAstGenerator(ArtifactAstDependencyResolver dependencyResolver) {
    this.dependencyResolver = dependencyResolver;
  }

  /**
   * Resolves the minimal set of {@link ComponentModel componentModels} for the components that pass the
   * {@link LazyComponentInitializer.ComponentLocationFilter}.
   *
   * @param predicate to select the {@link ComponentModel componentModels} to be enabled.
   * @return the generated {@link ApplicationModel} with the minimal set of {@link ComponentModel}s required.
   */
  public ArtifactAst getMinimalArtifactAst(Predicate<ComponentAst> predicate) {
    List<ComponentAst> required = dependencyResolver.findRequiredComponentModels(predicate);

    required.stream().forEach(componentAst -> {
      final DefaultComponentLocation componentLocation = (DefaultComponentLocation) componentAst.getComponentLocation();
      if (componentLocation != null) {
        enableComponentDependencies(componentAst);
      }
    });
    return dependencyResolver.getArtifactAst();
  }

  /**
   * Resolves the minimal set of {@link ComponentModel componentModels} for the component.
   *
   * @param location {@link Location} for the requested component to be enabled.
   * @return the generated {@link ApplicationModel} with the minimal set of {@link ComponentModel}s required.
   * @throws NoSuchComponentModelException if the location doesn't match to a component.
   */
  public ArtifactAst getMinimalArtifactAst(Location location) {
    ComponentAst requestedComponentModel = dependencyResolver.findRequiredComponentAst(location);
    enableComponentDependencies(requestedComponentModel);
    return dependencyResolver.getArtifactAst();
  }

  /**
   * Enables the {@link ComponentModel} and its dependencies in the {@link ApplicationModel}.
   *
   * @param requestedComponentAst the requested {@link ComponentModel} to be enabled.
   */
  private void enableComponentDependencies(ComponentAst requestedComponentAst) {
    final String requestComponentModelName = requestedComponentAst.getNameParameterValueOrNull();
    final Set<DependencyNode> componentDependencies = dependencyResolver.resolveComponentDependencies(requestedComponentAst);
    final Set<DependencyNode> alwaysEnabledComponents = dependencyResolver.resolveAlwaysEnabledComponents();
    final ImmutableSet.Builder<DependencyNode> otherRequiredGlobalComponentsSetBuilder =
        ImmutableSet.<DependencyNode>builder().addAll(componentDependencies)
            .addAll(alwaysEnabledComponents.stream().filter(dependencyNode -> dependencyNode.isTopLevel()).collect(toList()));
    if (requestComponentModelName != null
        && dependencyResolver.getArtifactAst().getGlobalComponentByName(requestComponentModelName).isPresent()) {
      otherRequiredGlobalComponentsSetBuilder.add(new DependencyNode(requestComponentModelName, TOP_LEVEL));
    }
    Set<DependencyNode> allRequiredComponentModels = resolveDependencies(otherRequiredGlobalComponentsSetBuilder.build());
    enableTopLevelElementDependencies(allRequiredComponentModels);
    enableInnerElementDependencies(allRequiredComponentModels);

    ComponentAst parentComponentAst =
        dependencyResolver.getArtifactAst().getParentComponentAst(requestedComponentAst).orElse(null);
    while (parentComponentAst != null
        && dependencyResolver.getArtifactAst().getParentComponentAst(parentComponentAst).isPresent()) {
      parentComponentAst.setEnabled(true);
      parentComponentAst = dependencyResolver.getArtifactAst().getParentComponentAst(parentComponentAst).get();
    }

    alwaysEnabledComponents.stream()
        .filter(dependencyNode -> dependencyNode.isUnnamedTopLevel() && dependencyNode.getComponentIdentifier().isPresent())
        .forEach(dependencyNode -> dependencyResolver.getArtifactAst()
            .getGlobalComponentByIdentifier(dependencyNode.getComponentIdentifier().get())
            .ifPresent(componentAst -> {
              enableComponentAstAndNestedComponentsAst(componentAst);
            }));


    // Finally we set the requested componentModel as enabled as it could have been disabled when traversing dependencies
    enableComponentAstAndNestedComponentsAst(requestedComponentAst);
    enableParentComponentModels(requestedComponentAst);
  }

  private void enableComponentAstAndNestedComponentsAst(ComponentAst componentAst) {
    componentAst.setEnabled(true);
    componentAst.getAllNestedComponentAstRecursively()
        .stream()
        .forEach(innerComponentAst -> innerComponentAst.setEnabled(true));
  }

  private void enableInnerElementDependencies(Set<DependencyNode> allRequiredComponentModels) {
    Set<String> noneTopLevelDendencyNames = allRequiredComponentModels.stream()
        .filter(dependencyNode -> !dependencyNode.isTopLevel())
        .map(dependencyNode -> dependencyNode.getComponentName())
        .collect(toSet());

    dependencyResolver.getArtifactAst()
        .getAllNestedComponentsAst()
        .forEach(componentAst -> {
          if (!componentAst.isEnabled() && componentAst.getNameParameterValueOrNull() != null
              && noneTopLevelDendencyNames.contains(componentAst.getNameParameterValueOrNull())) {
            enableComponentAstAndNestedComponentsAst(componentAst);
            enableParentComponentModels(componentAst);
          }
        });
  }

  private void enableTopLevelElementDependencies(Set<DependencyNode> allRequiredComponentModels) {
    Set<String> topLevelDendencyNames = allRequiredComponentModels.stream()
        .filter(dependencyNode -> dependencyNode.isTopLevel())
        .map(dependencyNode -> dependencyNode.getComponentName())
        .collect(toSet());

    Iterator<ComponentAst> iterator =
        dependencyResolver.getArtifactAst().getGlobalComponents().iterator();
    while (iterator.hasNext()) {
      ComponentAst componentAst = iterator.next();
      if (componentAst.getNameParameterValueOrNull() != null
          && topLevelDendencyNames.contains(componentAst.getNameParameterValueOrNull())) {
        enableComponentAstAndNestedComponentsAst(componentAst);
      }
    }
  }

  private void enableParentComponentModels(ComponentAst requestedComponentAst) {
    ComponentAst parentComponentAst =
        dependencyResolver.getArtifactAst().getParentComponentAst(requestedComponentAst).orElse(null);
    while (parentComponentAst != null
        && dependencyResolver.getArtifactAst().getParentComponentAst(parentComponentAst).isPresent()) {
      parentComponentAst.setEnabled(true);
      parentComponentAst = dependencyResolver.getArtifactAst().getParentComponentAst(parentComponentAst).orElse(null);
    }
  }

  /**
   * Resolve all the dependencies for an initial components set.
   *
   * @param initialComponents {@ling Set} of initial components to retrieve their dependencies
   * @return a new {@ling Set} with all the dependencies needed to run all the initial components
   */
  private Set<DependencyNode> resolveDependencies(Set<DependencyNode> initialComponents) {
    Set<DependencyNode> difference = initialComponents;
    Set<DependencyNode> allRequiredComponentModels = new HashSet<>(initialComponents);

    // While there are new dependencies resolved, calculate their dependencies
    // This fixes bugs related to not resolving dependencies of dependencies, such as a config for a config
    // e.g. tlsContext for http request, or a flow-ref inside a flow that is being referenced in another flow.
    while (difference.size() > 0) {
      // Only calculate the dependencies for the difference, to avoid recalculating
      Set<DependencyNode> newDependencies = dependencyResolver.findComponentModelsDependencies(difference);
      newDependencies.removeAll(allRequiredComponentModels);
      allRequiredComponentModels.addAll(newDependencies);
      difference = newDependencies;
    }
    return allRequiredComponentModels;
  }

}
