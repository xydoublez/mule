/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.component.ComponentIdentifier.builder;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.component.ComponentIdentifier;

public class ComponentAstHolder {

  private static final ComponentIdentifier CORE_NAME_PARAMETER_IDENTIFIER =
      builder().namespace(CORE_PREFIX).name("name").build();

  private static final ComponentIdentifier CORE_VALUE_PARAMETER_IDENTIFIER =
      builder().namespace(CORE_PREFIX).name("value").build();

  private final ComponentAst componentAst;
  private boolean enabled = true;
  private Map<ComponentIdentifier, ParameterAstHolder> parameterAstHolderMap = new HashMap<>();


  public ComponentAstHolder(ComponentAst componentAst) {
    this.componentAst = componentAst;
  }

  public ComponentAst getComponentAst() {
    return componentAst;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Optional<ParameterAstHolder> getNameParameter() {
    // TODO review for those that have are from a custom extension
    Optional<ParameterAstHolder> parameterAstHolder = getParameterAstHolder(CORE_NAME_PARAMETER_IDENTIFIER);
    if (!parameterAstHolder.isPresent()) {
      // TODO this is because with are not taking into account the namespacer
      return componentAst.getParameters()
          .stream()
          .filter(parameterAst -> parameterAst.getParameterIdentifier().getIdentifier().getName().equals("name"))
          .map(parameterAst -> new ParameterAstHolder(parameterAst))
          .findAny();
    }
    return parameterAstHolder;
  }

  public Optional<ParameterAstHolder> getValueParameter() {
    // TODO review for those that have are from a custom extension
    return getParameterAstHolder(CORE_VALUE_PARAMETER_IDENTIFIER);
  }

  public Optional<ParameterAstHolder> getParameterAstHolder(ComponentIdentifier componentIdentifier) {
    return Optional.ofNullable(ofNullable(parameterAstHolderMap.get(componentIdentifier))
        .orElseGet(() -> componentAst.getParameter(componentIdentifier)
            .map(parameterAst -> {
              ParameterAstHolder parameterAstHolder = new ParameterAstHolder(parameterAst);
              parameterAstHolderMap.put(componentIdentifier, parameterAstHolder);
              return parameterAstHolder;
            }).orElse(null)));
  }

  public List<ParameterAstHolder> getParameters() {
    return componentAst.getParameters().stream()
        .map(parameterAst -> getParameterAstHolder(parameterAst.getParameterIdentifier().getIdentifier()))
        .filter(optional -> optional.isPresent())
        .map(Optional::get)
        .collect(Collectors.toList());
  }

}
