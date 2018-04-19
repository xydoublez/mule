/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.model;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mule.metadata.api.model.ObjectType;
import org.mule.runtime.api.artifact.semantic.Artifact;
import org.mule.runtime.api.artifact.semantic.Component;
import org.mule.runtime.api.artifact.semantic.Configuration;
import org.mule.runtime.api.artifact.semantic.ConnectionProvider;
import org.mule.runtime.api.artifact.semantic.Construct;
import org.mule.runtime.api.artifact.semantic.Operation;
import org.mule.runtime.api.artifact.semantic.Parameter;
import org.mule.runtime.api.artifact.semantic.ParameterValue;
import org.mule.runtime.api.artifact.semantic.Route;
import org.mule.runtime.api.artifact.semantic.Source;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.artifact.sintax.ComponentDefinition;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.api.meta.model.connection.ConnectionProviderModel;
import org.mule.runtime.api.meta.model.construct.ConstructModel;
import org.mule.runtime.api.meta.model.nested.NestedChainModel;
import org.mule.runtime.api.meta.model.nested.NestedComponentModel;
import org.mule.runtime.api.meta.model.nested.NestedRouteModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.api.meta.model.source.SourceModel;
import org.mule.runtime.config.internal.dsl.model.ExtensionsHelper;
import org.mule.runtime.internal.dsl.DslConstants;

public class ArtifactModelFactory {

  private final Set<ExtensionModel> extensionsModels;
  private final ExtensionsHelper extensionsHelper;

  public ArtifactModelFactory(Set<ExtensionModel> extensionModels) {
    this.extensionsModels = extensionModels;
    this.extensionsHelper = new ExtensionsHelper(extensionModels);
  }

  public Artifact createFrom(ArtifactDefinition artifactDefinition) {
    Artifact.ArtifactBuilder artifactBuilder = Artifact.builder().withArtifactDefinition(Optional.of(artifactDefinition));
    return artifactBuilder.withGlobalComponents(createGlobalComponents(artifactDefinition)).build();
  }

  private List<Component> createGlobalComponents(ArtifactDefinition artifactDefinition) {
    return artifactDefinition.getGlobalDefinitions().stream()
        .map(componentDefinition -> createComponent(componentDefinition, empty())).collect(Collectors.toList());
  }

  private Component createComponent(ComponentDefinition componentDefinition, Optional<ConstructModel> constructModel) {
    Object model = extensionsHelper.findModel(componentDefinition.getIdentifier());
    if (model == null && constructModel.isPresent()) {
      model = extensionsHelper.findWithinModel(componentDefinition.getIdentifier(), constructModel.get());
    }
    if (model instanceof OperationModel) {
      return createOperation(componentDefinition, (OperationModel) model);
    } else if (model instanceof SourceModel) {
      return createSource(componentDefinition, (SourceModel) model);
    } else if (model instanceof ConstructModel) {
      return createConstruct(componentDefinition, (ConstructModel) model);
    } else if (model instanceof ConfigurationModel) {
      return createConfiguration(componentDefinition, (ConfigurationModel) model);
    } else if (model instanceof ConnectionProviderModel) {
      return createConnectionProvider(componentDefinition, (ConnectionProviderModel) model);
    } else if (model instanceof ObjectType) {
      return createObject(componentDefinition, (ObjectType) model);
    } else if (model instanceof NestedRouteModel) {
      return createRoute(componentDefinition, (NestedRouteModel) model);
    } else if (model instanceof NestedChainModel) {

    } else if (model instanceof NestedComponentModel) {

    }
    // TODO improve
    throw new RuntimeException();
  }

  private Component createRoute(ComponentDefinition componentDefinition, NestedRouteModel model) {
    return Route.builder()
        .withComponentDefinition(componentDefinition)
        .withParameters(extractParameters(componentDefinition, model))
        .withModel(model)
        .withProcessorComponents(extractProcessorComponents(componentDefinition))
        .build();
  }

  private List<Component> extractProcessorComponents(ComponentDefinition componentDefinition) {
    // TODO filter those that are parameters
    return componentDefinition.getChildComponentDefinitions().stream()
        .map(childComponentDefinition -> createComponent(childComponentDefinition, empty()))
        .collect(Collectors.toList());
  }

  private Component createObject(ComponentDefinition componentDefinition, ObjectType model) {
    // TODO implement
    return null;
  }

  private Component createConnectionProvider(ComponentDefinition componentDefinition, ConnectionProviderModel model) {
    return ConnectionProvider.builder()
        .withModel(model)
        .withComponentDefinition(componentDefinition)
        .withParameters(extractParameters(componentDefinition, model))
        .build();
  }

  private Component createConfiguration(ComponentDefinition componentDefinition, ConfigurationModel model) {
    return Configuration.builder()
        .withModel(model)
        .withComponentDefinition(componentDefinition)
        .withParameters(extractParameters(componentDefinition, model))
        .build();
  }

  private Construct createConstruct(ComponentDefinition componentDefinition, ConstructModel model) {
    Construct.ConstructBuilder constructBuilder = Construct.builder()
        .withParameters(extractParameters(componentDefinition, model))
        .withModel(model)
        .withComponentDefinition(componentDefinition);

    constructBuilder.withProcessorComponents(componentDefinition.getChildComponentDefinitions()
        .stream() // add predicate to filter childs that are parameters
        .map(childComponentDefinition -> createComponent(childComponentDefinition, of(model)))
        .collect(Collectors.toList()));

    return constructBuilder
        .build();
  }

  private Source createSource(ComponentDefinition componentDefinition, SourceModel model) {
    return Source.builder()
        .withSourceModel(model)
        .withComponentDefinition(componentDefinition)
        .withParameters(extractParameters(componentDefinition, model))
        .build();
  }

  private Operation createOperation(ComponentDefinition componentDefinition, OperationModel model) {
    return Operation.builder()
        .withComponentDefinition(componentDefinition)
        .withOperationModel(model)
        .withParameters(extractParameters(componentDefinition, model))
        .build();
  }

  private List<Parameter> extractParameters(ComponentDefinition componentDefinition, ParameterizedModel parameterizedModel) {
    // TODO missing parameters that do not exists in ext. model as the name parameter
    Stream<Optional<Parameter>> parameterOptionalStream = componentDefinition.getParameterDefinitions().stream()
        .map(parameterDefinition -> extensionsHelper
            .findParameterModel(parameterizedModel,
                                parameterDefinition.getParameterIdentifierDefinition().getComponentIdentifier())
            .map(parameterModel -> Parameter.builder()
                .withModel(parameterModel)
                .withParameterDefinition(parameterDefinition)
                .withValue(ParameterValue.builder()
                    .withParameterValueDefinition(parameterDefinition.getParameterValueDefinition())
                    .build())
                .build()));



    // TODO continue processing childs that may be parameters. This is doing nothing right now.
    componentDefinition.getChildComponentDefinitions().stream()
        .map(childComponentDefinition -> extensionsHelper.findParameterModel(componentDefinition.getIdentifier(),
                                                                             childComponentDefinition.getIdentifier()));
    List<Parameter> parameters =
        parameterOptionalStream.filter(optional -> optional.isPresent()).map(optional -> optional.orElse(null))
            .collect(Collectors.toList());

    // TODO do not add if already exists
    componentDefinition.getParameterDefinitions().stream()
        .filter(parameterDefinition -> parameterDefinition.getParameterIdentifierDefinition().getComponentIdentifier()
            .equals(ComponentIdentifier.builder().namespace(DslConstants.CORE_PREFIX).name("name").build()))
        .findAny()
        .ifPresent(parameterDefinition -> {
          parameters.add(Parameter.builder()
              .withParameterDefinition(parameterDefinition)
              .withValue(ParameterValue.builder()
                  .withParameterValueDefinition(parameterDefinition.getParameterValueDefinition())
                  .build())
              .build());
        });

    return parameters;
  }


}
