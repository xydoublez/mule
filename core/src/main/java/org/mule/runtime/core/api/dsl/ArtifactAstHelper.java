/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import static java.lang.System.identityHashCode;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.artifact.ast.ConfigurationAst;
import org.mule.runtime.api.artifact.ast.ConnectionProviderAst;
import org.mule.runtime.api.artifact.ast.ConstructAst;
import org.mule.runtime.api.artifact.ast.SimpleParameterValueAst;
import org.mule.runtime.api.component.ComponentIdentifier;

public class ArtifactAstHelper {

  private ArtifactAst artifactAst;

  // TOOD refactor to reuse what we have in ComponentAstHolder
  private Map<Integer, ComponentAstHolder> componentAstHolderMap = new HashMap<>();
  private Map<ComponentIdentifier, ParameterAstHolder> parameterAstHolderMap = new HashMap<>();

  public ArtifactAstHelper(ArtifactAst artifactAst) {
    this.artifactAst = artifactAst;
  }

  public void executeOnGlobalComponents(Consumer<ComponentAstHolder> task) {
    this.artifactAst.getGlobalComponents().stream()
        .map(this::toHolder)
        .forEach(task);
  }

  public void executeOnEverySimpleParameterAst(Consumer<SimpleParameterValueAst> task) {
    this.artifactAst.getGlobalComponents().stream()
        .forEach(componentAst -> componentAst.getNestedSimpleParameterValues().stream()
            .map(pair -> pair.getSecond())
            .forEach(task));
  }

  public static void executeOnNestedProcessors(ComponentAst componentAst, Consumer<ComponentAstHolder> task) {
    if (componentAst instanceof ConstructAst) {
      ConstructAst constructAst = (ConstructAst) componentAst;
      constructAst.getNestedComponentsAst().stream()
          .forEach(innerComponentAst -> {
            task.accept(new ComponentAstHolder(innerComponentAst));
            executeOnNestedProcessors(innerComponentAst, task);
          });
    }
  }

  public ComponentAstHolder toHolder(ComponentAst componentAst) {
    int objectId = identityHashCode(componentAst);
    if (componentAstHolderMap.containsKey(objectId)) {
      return componentAstHolderMap.get(objectId);
    }
    ComponentAstHolder componentAstHolder = new ComponentAstHolder(componentAst);
    componentAstHolderMap.put(objectId, componentAstHolder);
    return componentAstHolder;
  }

  public Optional<ParameterAstHolder> getParameterAstHolder(ComponentIdentifier componentIdentifier) {
    return ofNullable(ofNullable(parameterAstHolderMap.get(componentIdentifier))
        .orElseGet(() -> artifactAst.getParameter(componentIdentifier)
            .map(parameterAst -> {
              ParameterAstHolder parameterAstHolder = new ParameterAstHolder(parameterAst);
              parameterAstHolderMap.put(componentIdentifier, parameterAstHolder);
              return parameterAstHolder;
            })
            .orElse(null)));
  }

  public ArtifactAst getArtifactAst() {
    return artifactAst;
  }

  /**
   * Searchs for a {@link ConnectionProviderAst} within a {@link ComponentAst}
   * 
   * @param componentAst any {@link ComponentAst}
   * @return the {@link ConnectionProviderAst} if the component is a configuration and contains a connection provider. Empty
   *         otherwise.
   */
  public static Optional<ConnectionProviderAst> getConnectionProviderAst(ComponentAst componentAst) {
    if (!(componentAst instanceof ConfigurationAst)) {
      return empty();
    }
    return ((ConfigurationAst) componentAst).getConnectionProvider();
  }
}
