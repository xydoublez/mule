/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.heisenberg.extension;

import static java.util.Arrays.asList;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;
import org.mule.test.heisenberg.extension.model.Glass;
import org.mule.test.heisenberg.extension.model.Methylamine;

import java.util.List;

public class GlassToMethylamine implements ImplicitTransformer {

  @Override
  public String getName() {
    return "glassToImplicitTransformer";
  }

  @Override
  public List<DataType> getSourceTypes() {
    return asList(DataType.fromType(Glass.class));
  }

  @Override
  public DataType getOutputType() {
    return DataType.fromType(Methylamine.class);
  }

  @Override
  public Object transform(Object value) {
    Glass glass = (Glass) value;
    Methylamine methylamine = new Methylamine();
    methylamine.setImporter(glass.getBrand());
    methylamine.setStolen(false);

    return methylamine;
  }
}
