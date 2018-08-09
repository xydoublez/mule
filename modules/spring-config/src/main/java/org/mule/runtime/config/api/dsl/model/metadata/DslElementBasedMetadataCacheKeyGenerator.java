/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model.metadata;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.mule.runtime.core.api.el.ExpressionManager.DEFAULT_EXPRESSION_PREFIX;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import static org.mule.runtime.internal.dsl.DslConstants.CONFIG_ATTRIBUTE_NAME;
import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.api.model.ObjectType;
import org.mule.metadata.api.model.StringType;
import org.mule.metadata.api.visitor.MetadataTypeVisitor;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.config.api.dsl.model.DslElementModel;
import org.mule.runtime.core.internal.metadata.cache.CacheKeyPart;
import org.mule.runtime.core.internal.metadata.cache.MetadataCacheKeyGenerator;
import org.mule.runtime.dsl.api.component.config.ComponentConfiguration;
import org.mule.runtime.extension.api.declaration.type.annotation.TypeDslAnnotation;
import org.mule.runtime.extension.api.property.MetadataKeyPartModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * //TODO
 */
public class DslElementBasedMetadataCacheKeyGenerator implements MetadataCacheKeyGenerator<DslElementModel<?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DslElementBasedMetadataCacheKeyGenerator.class);
  private final Function<Location, Optional<DslElementModel<?>>> locator;

  public DslElementBasedMetadataCacheKeyGenerator(Function<Location, Optional<DslElementModel<?>>> locator) {
    this.locator = locator;
  }

  @Override
  public List<CacheKeyPart> generateKey(DslElementModel<?> elementModel) {
    List<CacheKeyPart> keyParts = new ArrayList<>();

    resolveConfigPart(elementModel)
        .ifPresent(keyParts::add);

    Object model = elementModel.getModel();
    if (model instanceof org.mule.runtime.api.meta.model.ComponentModel) {
      keyParts.addAll(resolveMetadataKeyParts(elementModel, (org.mule.runtime.api.meta.model.ComponentModel) model));
    } else {
      keyParts.addAll(resolveGlobalElement(elementModel));
    }

    return keyParts;
  }

  private Optional<CacheKeyPart> resolveConfigPart(DslElementModel<?> elementModel) {
    // TODO Migrate to Stereotypes when config-ref is part of model
    Optional<ComponentConfiguration> configuration = elementModel.getConfiguration();
    if (configuration.isPresent()) {
      String configRef = configuration.get().getParameters().get(CONFIG_ATTRIBUTE_NAME);
      if (!isBlank(configRef)) {
        return getHashedGlobal(configRef);
      }
    }

    return Optional.empty();
  }

  private List<CacheKeyPart> resolveGlobalElement(DslElementModel<?> elementModel) {
    List<CacheKeyPart> keyParts = new ArrayList<>();
    elementModel.getIdentifier()
        .ifPresent(id -> keyParts.add(new CacheKeyPart(String.valueOf(id.hashCode()), id.toString())));

    elementModel.getContainedElements().stream()
        .filter(containedElement -> containedElement.getModel() != null)
        .forEach(containedElement -> {
          if (containedElement.getValue().isPresent()) {
            resolveKeyFromSimpleValue(containedElement).ifPresent(keyParts::add);
          } else {
            List<CacheKeyPart> containedParts = generateKey(containedElement);
            String containedHash = containedParts.stream().map(CacheKeyPart::getHash).collect(joining(""));
            if (!isBlank(containedHash)) {
              keyParts.add(new CacheKeyPart(containedHash, null));
            }
          }
        });

    return keyParts;
  }

  private List<CacheKeyPart> resolveMetadataKeyParts(DslElementModel<?> elementModel,
                                                     org.mule.runtime.api.meta.model.ComponentModel componentModel) {
    List<CacheKeyPart> keyParts = new ArrayList<>();
    componentModel.getAllParameterModels().stream()
        .filter(p -> p.getModelProperty(MetadataKeyPartModelProperty.class).isPresent())
        .map(metadataKeyPart -> elementModel.findElement(metadataKeyPart.getName()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(partElement -> partElement.getValue().isPresent())
        .forEach(partElement -> resolveKeyFromSimpleValue(partElement).ifPresent(keyParts::add));

    return keyParts;
  }

  private Optional<CacheKeyPart> resolveKeyFromSimpleValue(DslElementModel<?> element) {
    if (element == null || !element.getValue().isPresent()) {
      return Optional.empty();
    }

    final String value = element.getValue().get();
    CacheKeyPart valuePart = new CacheKeyPart(String.valueOf(value.hashCode()), null);
    if (value.contains(DEFAULT_EXPRESSION_PREFIX)) {
      return Optional.of(valuePart);
    }

    Reference<CacheKeyPart> reference = new Reference<>();
    if (element.getModel() instanceof ParameterModel) {
      ParameterModel model = (ParameterModel) element.getModel();
      model.getType()
          .accept(new MetadataTypeVisitor() {

            @Override
            public void visitString(StringType stringType) {
              if (!model.getAllowedStereotypes().isEmpty()) {
                getHashedGlobal(value).ifPresent(reference::set);
              }
            }

            @Override
            public void visitArrayType(ArrayType arrayType) {
              if (model.getDslConfiguration().allowsReferences()) {
                getHashedGlobal(value).ifPresent(reference::set);
              }
            }

            @Override
            public void visitObject(ObjectType objectType) {
              if (model.getDslConfiguration().allowsReferences()) {
                getHashedGlobal(value).ifPresent(reference::set);
              }
            }

          });

    } else if (element.getModel() instanceof MetadataType) {
      ((MetadataType) element.getModel()).accept(new MetadataTypeVisitor() {

        @Override
        public void visitArrayType(ArrayType arrayType) {
          getHashedGlobal(value).ifPresent(reference::set);
        }

        @Override
        public void visitObject(ObjectType objectType) {
          boolean canBeGlobal = objectType.getAnnotation(TypeDslAnnotation.class)
              .map(TypeDslAnnotation::allowsTopLevelDefinition).orElse(false);

          if (canBeGlobal) {
            getHashedGlobal(value).ifPresent(reference::set);
          }
        }
      });
    } else {
      LOGGER.warn(format("Unknown model type '%s' found for element '%s'",
                         String.valueOf(element.getModel()),
                         element.getIdentifier().map(Object::toString)
                             .orElseGet(() -> element.getDsl().getNamespace() + ":" + element.getDsl().getAttributeName())));
    }

    return Optional.of(reference.get() == null ? valuePart : reference.get());
  }

  private Optional<CacheKeyPart> getHashedGlobal(String name) {
    if (!isBlank(name)) {
      String globalHash = locator.apply(Location.builder().globalName(name).build())
          .map(this::generateKey)
          .map(parts -> parts.stream().map(CacheKeyPart::getHash).collect(joining("")))
          .orElse("");

      if (!isBlank(globalHash)) {
        return Optional.of(new CacheKeyPart(globalHash, name));
      }
    }
    return Optional.empty();
  }
}
