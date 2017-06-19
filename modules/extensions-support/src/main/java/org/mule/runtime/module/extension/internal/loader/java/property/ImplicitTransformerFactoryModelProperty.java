/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.loader.java.property;

import org.mule.runtime.api.meta.model.ModelProperty;
import org.mule.runtime.module.extension.internal.runtime.transformer.ImplicitTransformerFactory;

public class ImplicitTransformerFactoryModelProperty implements ModelProperty {

  private ImplicitTransformerFactory implicitTransformerFactory;

  public ImplicitTransformerFactoryModelProperty(ImplicitTransformerFactory implicitTransformerFactory) {
    this.implicitTransformerFactory = implicitTransformerFactory;
  }

  public ImplicitTransformerFactory getImplicitTransformerFactory() {
    return implicitTransformerFactory;
  }

  @Override
  public String getName() {
    return "implicitTransformerFactory";
  }

  @Override
  public boolean isPublic() {
    return false;
  }
}
