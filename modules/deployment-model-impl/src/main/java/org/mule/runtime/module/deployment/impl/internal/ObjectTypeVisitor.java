/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;

import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.dsl.api.component.TypeDefinition;
import org.mule.runtime.dsl.api.component.TypeDefinitionVisitor;

/**
 * Visitor that retrieves the {@code ComponentModel} object {@code Class} based on the component configuration.
 *
 * @since 4.0
 */
// TODO this class has been copied form spring module. We need to find the right place for it.
public class ObjectTypeVisitor implements TypeDefinitionVisitor {

  public static final Class<ArrayList> DEFAULT_COLLECTION_TYPE = ArrayList.class;
  private static final Class<HashMap> DEFAULT_MAP_TYPE = HashMap.class;
  private static final Class<HashSet> DEFAULT_SET_CLASS = HashSet.class;

  private final ComponentAst componentAst;
  private Class<?> type;
  private Optional<TypeDefinition.MapEntryType> mapEntryType = empty();

  public ObjectTypeVisitor(ComponentAst componentAst) {
    this.componentAst = componentAst;
  }

  @Override
  public void onType(Class<?> type) {
    this.type = resolveType(type);
  }

  private Class<?> resolveType(Class<?> type) {
    if (Collection.class.equals(type) || List.class.equals(type)) {
      return DEFAULT_COLLECTION_TYPE;
    } else if (Set.class.equals(type)) {
      return DEFAULT_SET_CLASS;
    } else if (Map.class.equals(type)) {
      return DEFAULT_MAP_TYPE;
    } else {
      return type;
    }
  }

  @Override
  public void onConfigurationAttribute(String attributeName, Class<?> enforcedClass) {
    String attributeValue = null;
    try {
      attributeValue = componentAst.getSameNamespaceSimpleParameterValue(attributeName).orElse(null);
      type =
          ClassUtils.getClass(Thread.currentThread().getContextClassLoader(), attributeValue);
      if (!enforcedClass.isAssignableFrom(type)) {
        throw new MuleRuntimeException(createStaticMessage("Class definition for type %s on element %s is not the same nor inherits from %s",
                                                           attributeValue,
                                                           componentAst.getComponentIdentifier(), enforcedClass.getName()));
      }
    } catch (ClassNotFoundException e) {
      throw new MuleRuntimeException(createStaticMessage("Error while trying to locate Class definition for type %s on element %s",
                                                         attributeValue,
                                                         componentAst.getComponentIdentifier()),
                                     e);
    }
  }

  @Override
  public void onMapType(TypeDefinition.MapEntryType mapEntryType) {
    this.type = mapEntryType.getClass();
    this.mapEntryType =
        of(new TypeDefinition.MapEntryType(resolveType(mapEntryType.getKeyType()), resolveType(mapEntryType.getValueType())));
  }

  public Class<?> getType() {
    return type;
  }

  public Optional<TypeDefinition.MapEntryType> getMapEntryType() {
    return mapEntryType;
  }
}
