/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.spring;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.component.Component.ANNOTATIONS_PROPERTY_NAME;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ERROR_HANDLER;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ON_ERROR;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.OPERATION;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ROUTER;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.SCOPE;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.SOURCE;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.ON_ERROR_CONTINE_IDENTIFIER;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.ON_ERROR_PROPAGATE_IDENTIFIER;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.namespace.QName;

import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.config.internal.dsl.model.ComponentLocationVisitor;
import org.mule.runtime.config.internal.dsl.model.ExtensionModelHelper;
import org.mule.runtime.config.internal.dsl.model.SpringComponentModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.api.processor.InterceptingMessageProcessor;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.source.MessageSource;
import org.mule.runtime.core.internal.exception.ErrorHandler;
import org.mule.runtime.core.internal.routing.AbstractSelectiveRouter;
import org.mule.runtime.core.privileged.exception.TemplateOnErrorHandler;
import org.mule.runtime.core.privileged.processor.Router;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;

public class ComponentModelHelper {

  /**
   * Resolves the {@link org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType} from a {@link ComponentModel}.
   *
   * @param componentModel a {@link ComponentModel} that represents a component in the configuration.
   * @param extensionModelHelper helper to access components in extension model
   * @return the componentModel type.
   */
  public static TypedComponentIdentifier.ComponentType resolveComponentType(ComponentModel componentModel,
                                                                            ExtensionModelHelper extensionModelHelper) {
    if (componentModel.getIdentifier().equals(ON_ERROR_CONTINE_IDENTIFIER)
        || componentModel.getIdentifier().equals(ON_ERROR_PROPAGATE_IDENTIFIER)) {
      return ON_ERROR;
    }
    return extensionModelHelper.findComponentType(componentModel);
  }

  public static boolean isAnnotatedObject(ComponentAst componentAst) {
    return isOfType(componentAst, Component.class);
  }

  public static boolean isProcessor(ComponentAst componentAst) {
    return isOfType(componentAst, Processor.class)
        || isOfType(componentAst, InterceptingMessageProcessor.class)
        || componentAst.getComponentType().map(type -> type.equals(OPERATION)).orElse(false)
        || componentAst.getComponentType().map(type -> type.equals(ROUTER)).orElse(false)
        || componentAst.getComponentType().map(type -> type.equals(SCOPE)).orElse(false);
  }

  public static boolean isMessageSource(ComponentAst componentAst) {
    return isOfType(componentAst, MessageSource.class)
        || componentAst.getComponentType().map(type -> type.equals(SOURCE)).orElse(false);
  }

  public static boolean isErrorHandler(ComponentAst componentAst) {
    return isOfType(componentAst, ErrorHandler.class)
        || componentAst.getComponentType().map(type -> type.equals(ERROR_HANDLER)).orElse(false);
  }

  public static boolean isTemplateOnErrorHandler(ComponentAst componentAst) {
    return isOfType(componentAst, TemplateOnErrorHandler.class)
        || componentAst.getComponentType().map(type -> type.equals(ON_ERROR)).orElse(false);
  }

  private static boolean isOfType(ComponentAst componentAst, Class type) {
    Class<?> componentModelType = componentAst.getType();
    if (componentModelType == null) {
      return false;
    }
    return CommonBeanDefinitionCreator.areMatchingTypes(type, componentModelType);
  }

  public static void addAnnotation(QName annotationKey, Object annotationValue, ComponentAst componentAst) {
    // TODO MULE-10970 - remove condition once everything is AnnotatedObject.
    if (!ComponentModelHelper.isAnnotatedObject(componentAst)
        && !componentAst.getComponentIdentifier().getName().equals("flow-ref")) {
      return;
    }
    BeanDefinition beanDefinition = (BeanDefinition) componentAst.getBeanDefinition();
    if (beanDefinition == null) {
      // This is the case of components that are references
      return;
    }
    updateAnnotationValue(annotationKey, annotationValue, beanDefinition);
  }

  public static void updateAnnotationValue(QName annotationKey, Object annotationValue, BeanDefinition beanDefinition) {
    PropertyValue propertyValue =
        beanDefinition.getPropertyValues().getPropertyValue(ANNOTATIONS_PROPERTY_NAME);
    Map<QName, Object> annotations;
    if (propertyValue == null) {
      annotations = new HashMap<>();
      propertyValue = new PropertyValue(ANNOTATIONS_PROPERTY_NAME, annotations);
      beanDefinition.getPropertyValues().addPropertyValue(propertyValue);
    } else {
      annotations = (Map<QName, Object>) propertyValue.getValue();
    }
    annotations.put(annotationKey, annotationValue);
  }

  public static <T> Optional<T> getAnnotation(QName annotationKey, SpringComponentModel componentModel) {
    if (componentModel.getBeanDefinition() == null) {
      return empty();
    }
    PropertyValue propertyValue =
        componentModel.getBeanDefinition().getPropertyValues().getPropertyValue(ANNOTATIONS_PROPERTY_NAME);
    Map<QName, Object> annotations;
    if (propertyValue == null) {
      return empty();
    } else {
      annotations = (Map<QName, Object>) propertyValue.getValue();
      return ofNullable((T) annotations.get(annotationKey));
    }
  }

  public static boolean isRouter(ComponentAst componentAst) {
    return isOfType(componentAst, Router.class) || isOfType(componentAst, AbstractSelectiveRouter.class)
        || ComponentLocationVisitor.BATCH_JOB_COMPONENT_IDENTIFIER.equals(componentAst.getComponentIdentifier())
        || ComponentLocationVisitor.BATCH_PROCESSS_RECORDS_COMPONENT_IDENTIFIER.equals(componentAst.getComponentIdentifier())
        || componentAst.getComponentType().map(type -> type.equals(ROUTER)).orElse(false);
  }
}
