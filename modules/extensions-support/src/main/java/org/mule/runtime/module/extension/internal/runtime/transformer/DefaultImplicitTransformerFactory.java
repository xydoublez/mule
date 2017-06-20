/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.transformer;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.util.ClassUtils.instantiateClass;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;

public class DefaultImplicitTransformerFactory implements ImplicitTransformerFactory {

  private Class<? extends ImplicitTransformer> transformerType;

  public DefaultImplicitTransformerFactory(Class<? extends ImplicitTransformer> transformerType) {
    this.transformerType = transformerType;
  }

  @Override
  public ImplicitTransformer create() {
    try {
      return instantiateClass(transformerType);
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage(
                                                         "Could not instantiate ImplicitTransformer of type " + transformerType),
                                     e);
    }
  }
}
