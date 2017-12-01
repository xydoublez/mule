/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import static java.lang.String.format;
import static org.mule.runtime.api.component.ComponentIdentifier.buildFromStringRepresentation;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.api.functional.Either;

import org.xtext.example.mydsl.myDsl.ExpressionStatement;
import org.xtext.example.mydsl.myDsl.OperationCallType;
import org.xtext.example.mydsl.myDsl.ParamCall;
import org.xtext.example.mydsl.myDsl.ParamsCall;
import org.xtext.example.mydsl.myDsl.ProcessorCall;

public class ExpressionStatementResolver {


  /**
   * Resolves complex statement like ProcessorCall or OperationCall
   * 
   * @param expressionStatement
   * @param statementResolutionContext
   */
  public void resolveComplexStatement(ExpressionStatement expressionStatement,
                                      StatementResolutionContext statementResolutionContext) {
    innerResolveComplexParamStatement(expressionStatement, statementResolutionContext, false);
  }

  /**
   * Resolves an statement used within a parameter.
   * <p/>
   * It will create all the required {@link ComponentModel}s to fulfill the complex statement including all intermediate variables
   *
   * //TODO get rid of all the variables created as auxiliary
   *
   * @param expressionStatement expression
   * @param statementResolutionContext resolution context
   * @param assignTargetValue if true it will automatically add the generated variable name as a target parameter, otherwise it
   *        won't
   * @return the auxiliary variable name associated with the statement at the right position or the value to assign to the
   *         parameter in the left.
   */
  private Either<String, String> innerResolveComplexParamStatement(ExpressionStatement expressionStatement,
                                                                   StatementResolutionContext statementResolutionContext,
                                                                   boolean assignTargetValue) {
    if (expressionStatement.getListeral() != null) {
      // Removes additional quotes
      return Either.left(expressionStatement.getListeral().substring(1, expressionStatement.getListeral().length()-1));
    }
    if (expressionStatement.getExpression() != null) {
      String expression = expressionStatement.getExpression().getExpression();
      return Either.left(format("#[%s]", expression));
    }

    ComponentModel.Builder lineComponentModelBuilder = new ComponentModel.Builder();
    lineComponentModelBuilder.setConfigFileName("fake");
    lineComponentModelBuilder.setLineNumber(34);
    ParamsCall paramsCall;

    if (expressionStatement.getOperationCall() != null) {
      OperationCallType operationCall = expressionStatement.getOperationCall();
      lineComponentModelBuilder
          .setIdentifier(buildFromStringRepresentation(operationCall.getOperation().getName().replace("::", ":")));
      paramsCall = operationCall.getParamsCall();
    } else {
      ProcessorCall processorCall = expressionStatement.getProcessorCall();
      lineComponentModelBuilder.setIdentifier(buildFromStringRepresentation(processorCall.getName().replace("::", ":")));
      paramsCall = processorCall.getParamsCall();
    }

    resolveParamsCall(statementResolutionContext, lineComponentModelBuilder, paramsCall);

    String auxVarName = statementResolutionContext.generateNextAuxVarName();
    if (assignTargetValue) {
      lineComponentModelBuilder.addParameter("target", auxVarName, false);
    }
    statementResolutionContext.addComponentModel(lineComponentModelBuilder.build());
    return Either.right(auxVarName);
  }

  public void resolveParamsCall(StatementResolutionContext statementResolutionContext,
                                 ComponentModel.Builder lineComponentModelBuilder, ParamsCall paramsCall) {
    for (ParamCall paramCall : paramsCall.getParams()) {
      ExpressionStatement paramExpressionStatement = paramCall.getExpressionStatement();
      Either<String, String> resolvedStatement =
          innerResolveComplexParamStatement(paramExpressionStatement, statementResolutionContext, true);
      lineComponentModelBuilder.addParameter(paramCall.getName(),
                                             resolvedStatement.isLeft() ? resolvedStatement.getLeft()
                                                 : format("#[vars.%s]", resolvedStatement.getRight()),
                                             false);
    }
  }
}
