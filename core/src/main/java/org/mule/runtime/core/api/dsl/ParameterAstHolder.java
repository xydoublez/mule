/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import org.mule.runtime.api.artifact.ast.ComplexParameterValueAst;
import org.mule.runtime.api.artifact.ast.ParameterAst;
import org.mule.runtime.api.artifact.ast.SimpleParameterValueAst;
import org.mule.runtime.api.exception.MuleRuntimeException;

public class ParameterAstHolder {

  private ParameterAst parameterAst;

  public ParameterAstHolder(ParameterAst parameterAst) {
    this.parameterAst = parameterAst;
  }

  public ParameterAst getParameterAst() {
    return parameterAst;
  }

  public SimpleParameterValueAst getSimpleParameterValueAst() {
    if (isSimpleParameter()) {
      return (SimpleParameterValueAst) parameterAst.getValue();
    }
    throw new MuleRuntimeException(createStaticMessage(format("Requested raw value from a non %s parameter: %s",
                                                              SimpleParameterValueAst.class.getSimpleName(), parameterAst)));
  }

  public boolean isSimpleParameter() {
    return parameterAst.getValue() instanceof SimpleParameterValueAst;
  }

  public ComplexParameterValueAst getComplexParameterValueAst() {
    if (isComplexParameter()) {
      return (ComplexParameterValueAst) parameterAst.getValue();
    }
    throw new MuleRuntimeException(createStaticMessage(format("Requested raw value from a non %s parameter: %s",
                                                              ComplexParameterValueAst.class.getSimpleName(), parameterAst)));
  }

  public boolean isComplexParameter() {
    return parameterAst.getValue() instanceof ComplexParameterValueAst;
  }
}
