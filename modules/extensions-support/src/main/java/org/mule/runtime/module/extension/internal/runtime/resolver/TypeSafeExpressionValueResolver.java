/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.resolver;

import static org.mule.metadata.java.api.utils.JavaTypeUtils.getType;
import static org.mule.runtime.core.api.util.ClassUtils.isInstance;
import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.TransformationService;
import org.mule.runtime.core.api.transformer.TransformationServiceAware;
import org.mule.runtime.core.api.transformer.Transformer;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.api.util.AttributeEvaluator;

import javax.inject.Inject;

/**
 * A {@link ValueResolver} which evaluates expressions and tries to ensure that the output is always of a certain type.
 * <p>
 * If the expression does not return a value of that type, then it tries to locate a {@link Transformer} which can do the
 * transformation from the obtained type to the expected one.
 * <p>
 * It resolves the expressions by making use of the {@link AttributeEvaluator} so that it's compatible with simple
 * expressions and templates alike
 *
 * @param <T>
 * @since 3.7.0
 */
public class TypeSafeExpressionValueResolver<T> extends ExpressionValueResolver implements Initialisable, TransformationServiceAware {

  private TypeSafeTransformer typeSafeTransformer;

  @Inject
  private TransformationService transformationService;

  public TypeSafeExpressionValueResolver(String expression, MetadataType expectedMetadataType) {
    super(expression, DataType.builder().type(getType(expectedMetadataType)).mediaType(MediaType.APPLICATION_JAVA).build());
  }

  @Override
  public T resolve(ValueResolvingContext context) throws MuleException {
    TypedValue typedValue = resolveTypedValue(context);

    Object value = typedValue.getValue();

    if (!isInstance(expectedDataType.getType(), value)) {
      try {
        value = typeSafeTransformer.transform(value, typedValue.getDataType(), expectedDataType);
      } catch (TransformerException e) {
        value = extendedExpressionManager.evaluate("v", expectedDataType,
                                           BindingContext.builder()
                                               .addBinding("v", typedValue)
                    .build(), context.getEvent()).getValue();
      }
    }

    return (T) value;
  }

  @Override
  public void initialise() throws InitialisationException {
    typeSafeTransformer = new TypeSafeTransformer(transformationService);
  }

  @Override
  public void setTransformationService(TransformationService transformationService) {
    this.transformationService = transformationService;
  }
}
