/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mule.runtime.api.component.ComponentIdentifier.builder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.mule.metadata.api.model.ObjectType;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.api.meta.model.construct.ConstructModel;
import org.mule.runtime.api.meta.model.construct.HasConstructModels;
import org.mule.runtime.api.meta.model.nested.NestableElementModel;
import org.mule.runtime.api.meta.model.operation.HasOperationModels;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.api.meta.model.source.HasSourceModels;
import org.mule.runtime.api.meta.model.source.SourceModel;
import org.mule.runtime.api.meta.model.util.ExtensionWalker;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.extension.api.dsl.syntax.DslElementSyntax;
import org.mule.runtime.extension.api.dsl.syntax.resolver.DslSyntaxResolver;

public class ExtensionsHelper {

  private final DslResolvingContext resolvingContext;
  private final Map<ExtensionModel, DslSyntaxResolver> resolvers;
  private Set<ExtensionModel> extensionModels;

  public ExtensionsHelper(Set<ExtensionModel> extensionModels) {
    this.extensionModels = extensionModels;
    this.resolvingContext = DslResolvingContext.getDefault(extensionModels);
    this.resolvers = resolvingContext.getExtensions().stream()
        .collect(toMap(e -> e, e -> DslSyntaxResolver.getDefault(e, resolvingContext)));
  }

  public Object findModel(ComponentIdentifier identifier) {

    // TODO this code is duplicated from ConfigurationBasedElementModelFactory

    Optional<Map.Entry<ExtensionModel, DslSyntaxResolver>> entry = findExtensionEntry(identifier);

    if (!entry.isPresent()) {
      return null;
    }

    ExtensionModel currentExtension = entry.get().getKey();
    DslSyntaxResolver dsl = entry.get().getValue();

    Reference<Object> elementModel = new Reference<>();
    new ExtensionWalker() {

      @Override
      protected void onConfiguration(ConfigurationModel model) {
        final DslElementSyntax elementDsl = dsl.resolve(model);
        getIdentifier(elementDsl).ifPresent(elementId -> {
          if (elementId.equals(identifier)) {
            elementModel.set(model);
            stop();
          }
        });
      }

      @Override
      protected void onConstruct(HasConstructModels owner, ConstructModel model) {
        final DslElementSyntax elementDsl = dsl.resolve(model);
        getIdentifier(elementDsl).ifPresent(elementId -> {
          if (elementId.equals(identifier)) {
            elementModel.set(model);
            stop();
          }
        });
      }

      @Override
      protected void onOperation(HasOperationModels owner, OperationModel model) {
        final DslElementSyntax elementDsl = dsl.resolve(model);
        getIdentifier(elementDsl).ifPresent(elementId -> {
          if (elementId.equals(identifier)) {
            elementModel.set(model);
            stop();
          }
        });
      }

      @Override
      protected void onSource(HasSourceModels owner, SourceModel model) {
        final DslElementSyntax elementDsl = dsl.resolve(model);
        getIdentifier(elementDsl).ifPresent(elementId -> {
          if (elementId.equals(identifier)) {
            elementModel.set(model);
            stop();
          }
        });
      }

    }.walk(currentExtension);

    if (elementModel.get() == null) {
      resolveBasedOnTypes(currentExtension, identifier, dsl)
          .ifPresent(elementModel::set);
    }

    return elementModel.get();

  }

  private Optional<Map.Entry<ExtensionModel, DslSyntaxResolver>> findExtensionEntry(ComponentIdentifier identifier) {
    return resolvers.entrySet().stream()
        .filter(e -> e.getKey().getXmlDslModel().getPrefix().equals(identifier.getNamespace()))
        .findFirst();
  }

  private Optional<ComponentIdentifier> getIdentifier(DslElementSyntax dsl) {
    if (isNotBlank(dsl.getElementName()) && isNotBlank(dsl.getPrefix())) {
      return Optional.of(builder()
          .name(dsl.getElementName())
          .namespace(dsl.getPrefix())
          .build());
    }

    return empty();
  }

  private Optional<ObjectType> resolveBasedOnTypes(ExtensionModel extensionModel, ComponentIdentifier identifier,
                                                   DslSyntaxResolver dsl) {
    return extensionModel.getTypes().stream()
        .map(type -> resolveBasedOnType(dsl, type, identifier))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }


  private Optional<ObjectType> resolveBasedOnType(DslSyntaxResolver dsl,
                                                  ObjectType type,
                                                  ComponentIdentifier componentIdentifier) {
    Optional<DslElementSyntax> typeDsl = dsl.resolve(type);
    if (typeDsl.isPresent()) {
      Optional<ComponentIdentifier> elementIdentifier = getIdentifier(typeDsl.get());
      if (elementIdentifier.isPresent() && elementIdentifier.get().equals(componentIdentifier)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public Optional<ParameterModel> findParameterModel(ParameterizedModel parameterizedModel,
                                                     ComponentIdentifier parameterIdentifier) {

    Optional<Map.Entry<ExtensionModel, DslSyntaxResolver>> entry = findExtensionEntry(parameterIdentifier);

    if (!entry.isPresent()) {
      return null;
    }

    DslSyntaxResolver dsl = entry.get().getValue();

    return parameterizedModel.getAllParameterModels()
        .stream()
        .filter(parameterModel -> dsl.resolve(parameterModel).getAttributeName().equals(parameterIdentifier.getName()))
        .findAny();
  }


  public Optional<ParameterModel> findParameterModel(ComponentIdentifier componentIdentifier,
                                                     ComponentIdentifier parameterIdentifier) {

    Object model = findModel(componentIdentifier);
    if (model instanceof ComponentModel) {
      // TODO have in mind the namespace of the attribute
      // TODO have in mind complex child elements that are parameter models
      return findParameterModel((ParameterizedModel) model, parameterIdentifier);
    } else {
      throw new RuntimeException(componentIdentifier + " " + parameterIdentifier);
    }
  }

  public Object findWithinModel(ComponentIdentifier identifier, ConstructModel constructModel) {

    Optional<Map.Entry<ExtensionModel, DslSyntaxResolver>> entry = findExtensionEntry(identifier);

    if (!entry.isPresent()) {
      return null;
    }

    DslSyntaxResolver dsl = entry.get().getValue();

    Optional<? extends NestableElementModel> nestedElementModelOptional = constructModel.getNestedComponents().stream()
        .filter(elementModel -> getIdentifier(dsl.resolve(elementModel))
            .map(foundIdentifier -> foundIdentifier.equals(identifier))
            .orElse(false))
        .findFirst();

    // TODO change to not return null
    return nestedElementModelOptional.orElse(null);
  }

}
