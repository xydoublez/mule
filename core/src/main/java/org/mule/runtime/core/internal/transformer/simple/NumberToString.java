/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.transformer.simple;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.transformer.AbstractDiscoverableTransformer;

import java.nio.charset.Charset;
import java.text.NumberFormat;

/**
 * <code>NumberToString</code> converts a Number to a String. A NumberFormat is used if one is provided.
 */
public class NumberToString extends AbstractDiscoverableTransformer {

  private NumberFormat numberFormat;

  public NumberToString() {
    setPriorityWeighting(DEFAULT_PRIORITY_WEIGHTING + 1);
    registerSourceType(DataType.NUMBER);
    setReturnDataType(DataType.STRING);
  }

  public NumberToString(NumberFormat numberFormat) {
    this();
    this.numberFormat = numberFormat;
  }

  @Override
  public Object doTransform(Object src, Charset encoding) throws TransformerException {
    if (src == null) {
      return "";
    }

    if (numberFormat != null) {
      return numberFormat.format(src);
    } else {
      return src.toString();
    }
  }
}
