/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.loader.java.type.runtime;

import static java.util.Optional.ofNullable;
import static org.mule.runtime.module.extension.internal.util.IntrospectionUtils.isInstantiable;
import org.mule.runtime.extension.api.exception.IllegalTransformerModelDefinitionException;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;
import org.mule.runtime.module.extension.internal.loader.java.type.TransformerElement;
import org.mule.runtime.module.extension.internal.runtime.transformer.DefaultImplicitTransformerFactory;

import java.lang.annotation.Annotation;
import java.util.Optional;

public class TransformerElementWrapper implements TransformerElement {

  private final Class<? extends ImplicitTransformer> declaringClass;

  public TransformerElementWrapper(Class<? extends ImplicitTransformer> declaringClass) {
    this.declaringClass = declaringClass;
    if (!isInstantiable(declaringClass)) {
      throw new IllegalTransformerModelDefinitionException(String.format(
                                                                         "Implicit transformer of type '%s' is not instantiable. Implicit transformers are required to be of a "
                                                                             + "concrete type and have a default public transformer",
                                                                         declaringClass.getName()));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return new DefaultImplicitTransformerFactory(declaringClass).create().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class getDeclaringClass() {
    return declaringClass;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Annotation[] getAnnotations() {
    return declaringClass.getAnnotations();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <A extends Annotation> Optional<A> getAnnotation(Class<A> annotationClass) {
    return ofNullable(declaringClass.getAnnotation(annotationClass));
  }
}
