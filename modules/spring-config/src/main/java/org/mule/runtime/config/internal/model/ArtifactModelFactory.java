/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mule.metadata.api.model.ObjectType;
import org.mule.runtime.api.artifact.semantic.Artifact;
import org.mule.runtime.api.artifact.semantic.Component;
import org.mule.runtime.api.artifact.semantic.Construct;
import org.mule.runtime.api.artifact.semantic.Operation;
import org.mule.runtime.api.artifact.semantic.Parameter;
import org.mule.runtime.api.artifact.semantic.ParameterValue;
import org.mule.runtime.api.artifact.semantic.Source;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.artifact.sintax.ComponentDefinition;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.api.meta.model.connection.ConnectionProviderModel;
import org.mule.runtime.api.meta.model.construct.ConstructModel;
import org.mule.runtime.api.meta.model.nested.NestableElementModelVisitor;
import org.mule.runtime.api.meta.model.nested.NestedChainModel;
import org.mule.runtime.api.meta.model.nested.NestedComponentModel;
import org.mule.runtime.api.meta.model.nested.NestedRouteModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.source.SourceModel;
import org.mule.runtime.config.internal.dsl.model.ExtensionsHelper;

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
    return artifactDefinition.getGlobalDefinitions().stream().map(componentDefinition -> {
      Object model = extensionsHelper.findModel(componentDefinition.getIdentifier());
      return createComponent(componentDefinition, model);
    }).collect(Collectors.toList());
  }

  private Component createComponent(ComponentDefinition componentDefinition, Object model) {
    if (model instanceof OperationModel) {
      return createOperation(componentDefinition, (OperationModel) model);
    } else if (model instanceof SourceModel) {
      return createSource(componentDefinition, (SourceModel) model);
    } else if (model instanceof ConstructModel) {
      return createConstructModel(componentDefinition, (ConstructModel) model);
    } else if (model instanceof ConfigurationModel) {

    } else if (model instanceof ConnectionProviderModel) {

    } else if (model instanceof ObjectType) {

    }
    return null;
  }

  private Construct createConstructModel(ComponentDefinition componentDefinition, ConstructModel model) {
    Construct.ConstructBuilder constructBuilder = Construct.builder()
        .withParameters(extractParameters(componentDefinition))
        .withModel(model)
        .withComponentDefinition(componentDefinition);

    model.getNestedComponents().stream()
        .map(nestableElementModel -> {
          nestableElementModel.accept(new NestableElementModelVisitor() {

            @Override
            public void visit(NestedComponentModel component) {
              // TODO defin what do here
            }

            @Override
            public void visit(NestedChainModel component) {

           }

            @Override
            public void visit(NestedRouteModel component) {

            }
          });
          return null;
        });

    return constructBuilder
        .build();
  }

  private Source createSource(ComponentDefinition componentDefinition, SourceModel model) {
    return Source.builder()
        .withSourceModel(model)
        .withComponentDefinition(componentDefinition)
        .withParameters(extractParameters(componentDefinition))
        .build();
  }

  private Operation createOperation(ComponentDefinition componentDefinition, OperationModel model) {
    return Operation.builder()
        .withComponentDefinition(componentDefinition)
        .withOperationModel(model)
        .withParameters(extractParameters(componentDefinition))
        .build();
  }

  private List<Parameter> extractParameters(ComponentDefinition componentDefinition) {
    // TODO missing parameters that do not exists in ext. model as the name parameter
    Stream<Optional<Parameter>> parameterOptionalStream = componentDefinition.getParameterDefinitions().stream()
        .map(parameterDefinition -> extensionsHelper
            .findParameterModel(componentDefinition.getIdentifier(),
                                parameterDefinition.getParameterIdentifierDefinition().getComponentIdentifier())
            .map(parameterModel -> Parameter.builder()
                .withModel(parameterModel)
                .withParameterDefinition(parameterDefinition)
                .withValue(ParameterValue.builder()
                    .withParameterDefinition(parameterDefinition)
                    .build())
                .build()));

    // TODO continue processing childs that may be parameters
    componentDefinition.getChildComponentDefinitions().stream()
        .map(childComponentDefinition -> extensionsHelper.findParameterModel(componentDefinition.getIdentifier(),
                                                                             childComponentDefinition.getIdentifier()));
    return parameterOptionalStream.filter(optional -> optional.isPresent()).map(optional -> optional.orElse(null))
        .collect(Collectors.toList());
  }


}
