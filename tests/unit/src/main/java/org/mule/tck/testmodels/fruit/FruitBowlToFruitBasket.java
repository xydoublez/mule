/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tck.testmodels.fruit;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.transformer.AbstractDiscoverableTransformer;

import java.nio.charset.Charset;

/**
 * Converts a FruitBowl to a FruitBasket (for testing obviously :)
 */
public class FruitBowlToFruitBasket extends AbstractDiscoverableTransformer {

  public FruitBowlToFruitBasket() {
    registerSourceType(DataType.fromType(FruitBowl.class));
    setReturnDataType(DataType.fromType(FruitBasket.class));
  }

  @Override
  protected Object doTransform(Object src, Charset encoding) throws TransformerException {
    FruitBowl bowl = (FruitBowl) src;
    FruitBasket basket = new FruitBasket();
    basket.setFruit(bowl.getFruit());
    return basket;
  }
}
