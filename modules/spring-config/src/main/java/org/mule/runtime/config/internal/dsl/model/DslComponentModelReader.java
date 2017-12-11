/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.config.internal.dsl.model;

import static org.mule.runtime.api.component.ComponentIdentifier.builder;
import static org.mule.runtime.api.util.NameUtils.hyphenize;
import static org.mule.runtime.config.internal.dsl.processor.xml.XmlCustomAttributeHandler.to;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.config.api.dsl.processor.ConfigFile;
import org.mule.runtime.config.internal.dsl.model.config.ConfigurationPropertiesResolver;
import org.mule.runtime.config.internal.dsl.xtext.XtextParser;
import org.mule.runtime.config.internal.model.ComponentModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.xtext.example.mydsl.myDsl.CommonStatement;
import org.xtext.example.mydsl.myDsl.ConstructStatement;
import org.xtext.example.mydsl.myDsl.ExpressionStatement;
import org.xtext.example.mydsl.myDsl.FlowDefinition;
import org.xtext.example.mydsl.myDsl.GlobalDefinition;
import org.xtext.example.mydsl.myDsl.OperationDefinition;
import org.xtext.example.mydsl.myDsl.ParamsCall;
import org.xtext.example.mydsl.myDsl.VariableDeclaration;
import org.xtext.example.mydsl.myDsl.impl.ParameterConstructCallImpl;

public class DslComponentModelReader {

  private static final String MULE_TAG_ELEMENT = "mule";
  private final ConfigurationPropertiesResolver configurationPropertiesResolver;

  public DslComponentModelReader(ConfigurationPropertiesResolver configurationPropertiesResolver) {
    this.configurationPropertiesResolver = configurationPropertiesResolver;
  }

  public ComponentModel extractComponentDefinitionModel(ConfigFile configFile) {
    Resource resource = new XtextParser().parse(configFile);

    ComponentModel.Builder builder = new ComponentModel.Builder();

    builder.setIdentifier(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(MULE_TAG_ELEMENT).build());
    // TODO(pablo.kraan): need config filename here
    builder.setConfigFileName(configFile.getFilename());

    ComponentModel componentModel;

    if (!resource.getContents().isEmpty()) {
      List<ComponentModel> componentModels = new ArrayList<>();
      for (EObject resourceContent : resource.getContents().get(0).eContents()) {
        ComponentModel model = createModel(resourceContent);
        componentModels.add(model);
      }
      componentModels.stream().forEach(componentDefinitionModel -> builder.addChildComponentModel(componentDefinitionModel));
    }

    componentModel = builder.build();

    return componentModel;
  }

  public static ComponentIdentifier getComponentIdentifierByName(String name) {
    ComponentIdentifier.Builder builder = ComponentIdentifier.builder();
    String[] parts = name.split("::");
    builder.namespace(parts.length > 1 ? parts[0] : CORE_PREFIX);
    builder.name(hyphenize(parts.length > 1 ? parts[1] : parts[0]));
    return builder.build();
  }

  // private String resolveValueIfIsPlaceHolder(String value) {
  // Object resolvedValue = configurationPropertiesResolver.resolveValue(value);
  // return resolvedValue instanceof String ? (String) resolvedValue : (resolvedValue != null ? resolvedValue.toString() : null);
  // }

  private ComponentModel createModel(FlowDefinition flowDefinition) {
    System.out.println("flow createModel: " + flowDefinition);

    ComponentModel.Builder componentModelBuilder = new ComponentModel.Builder();
    componentModelBuilder.setIdentifier(ComponentIdentifier.buildFromStringRepresentation("mule:flow"));
    componentModelBuilder.setConfigFileName("fake");
    componentModelBuilder.setLineNumber(123);
    componentModelBuilder.markAsRootComponent();
    componentModelBuilder.addParameter("name", flowDefinition.getName(), false);

    EList<CommonStatement> statements = flowDefinition.getStatements();
    resolveStatements(componentModelBuilder, statements);
    return componentModelBuilder.build();
  }

  private ComponentModel createModel(OperationDefinition operationDefinition) {
    System.out.println("operation createModel: " + operationDefinition);

    ComponentModel.Builder componentModelBuilder = new ComponentModel.Builder();
    componentModelBuilder.setIdentifier(ComponentIdentifier.buildFromStringRepresentation("mule:operation"));
    componentModelBuilder.setConfigFileName("fake");
    componentModelBuilder.setLineNumber(123);
    componentModelBuilder.markAsRootComponent();
    componentModelBuilder.addParameter("name", operationDefinition.getName(), false);

    EList<CommonStatement> statements = operationDefinition.getStatements();
    resolveStatements(componentModelBuilder, statements);
    return componentModelBuilder.build();
  }

  private void resolveStatements(ComponentModel.Builder parentComponentModelBuilder, List<CommonStatement> statements) {
    for (CommonStatement statement : statements) {
      if (statement instanceof VariableDeclaration) {
        parentComponentModelBuilder.addChildComponentModel(createModel(statement));
      } else if (statement instanceof ExpressionStatement) {
        ExpressionStatementResolver expressionStatementResolver = new ExpressionStatementResolver();
        StatementResolutionContext statementResolutionContext = new StatementResolutionContext();
        expressionStatementResolver.resolveComplexStatement((ExpressionStatement) statement, statementResolutionContext);

        for (ComponentModel componentModel : statementResolutionContext.getComponentModels()) {
          parentComponentModelBuilder.addChildComponentModel(componentModel);
        }
      } else if (statement instanceof ConstructStatement) {
        ComponentModel.Builder scopeComponentBuilder = processConstructStatement((ConstructStatement) statement);
        parentComponentModelBuilder.addChildComponentModel(scopeComponentBuilder.build());
      } else {
        throw new IllegalStateException();
      }
    }
  }

  private ComponentModel.Builder processConstructStatement(ConstructStatement statement) {
    ComponentModel.Builder componentBuilder = new ComponentModel.Builder();
    componentBuilder.setIdentifier(getComponentIdentifierByName(statement.getName()));
    componentBuilder.setLineNumber(23);
    componentBuilder.setConfigFileName("sdf");
    List<CommonStatement> statementsToResolve = new LinkedList<>();
    if (statement.getName().equals("try")) {
      // It should always be "do"
      ConstructStatement doStatement = (ConstructStatement) statement.getStatements().get(0);
      statementsToResolve.addAll(doStatement.getStatements());
      if (statement.getStatements().size() > 1) {
        statementsToResolve.addAll(statement.getStatements().subList(1, statement.getStatements().size() - 1));
      }
    } else {
      statementsToResolve.addAll(statement.getStatements());
    }
    resolveStatements(componentBuilder, statementsToResolve);

    ParamsCall paramsCall = statement.getParams();
    ExpressionStatementResolver expressionStatementResolver = new ExpressionStatementResolver();
    expressionStatementResolver.resolveParamsCall(new StatementResolutionContext(), componentBuilder, paramsCall);

    return componentBuilder;
  }

  private ComponentModel createModel(GlobalDefinition globalDefinition) {
    System.out.println("global def createModel: " + globalDefinition);

    Map<String, String> attributes = new HashMap<>();
    attributes.put("name", globalDefinition.getName());

    EObject childObject = globalDefinition.eContents().get(0);
    String[] split = ((ParameterConstructCallImpl) childObject).getName().split("::");
    String nameSpace = split.length > 1 ? split[0] : CORE_PREFIX;
    String name = split.length > 1 ? hyphenize(split[1]) : hyphenize(split[0]);

    return extractComponentDefinitionModel("zaraza", childObject, name, attributes, nameSpace, true).build();
  }

  private ComponentModel createModel(VariableDeclaration variableDeclaration) {
    System.out.println("var createModel: " + variableDeclaration);


    ExpressionStatement variableValue = (ExpressionStatement) variableDeclaration.eContents().get(0);
    // TODO(pablo.kraan): add support of DW expressions
    if (variableValue.getLiteral() != null) {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("variableName", variableDeclaration.getName());
      // Removes additional quotes
      String literalValue = variableValue.getLiteral();
      attributes.put("value", literalValue.substring(1, literalValue.length() - 1));
      return extractComponentDefinitionModel("zaraza", variableValue, "set-variable", attributes, CORE_PREFIX, false).build();
    } else if (variableValue.getProcessorCall() != null) {
      ExpressionStatementResolver expressionStatementResolver = new ExpressionStatementResolver();
      StatementResolutionContext statementResolutionContext = new StatementResolutionContext();
      expressionStatementResolver.innerResolveComplexParamStatement(variableValue, statementResolutionContext, true,
                                                                    () -> variableDeclaration.getName());

      return statementResolutionContext.getComponentModels().get(0);
    } else {
      throw new IllegalStateException("Missing model creation logic for " + variableValue);
    }
  }

  private ComponentModel createModel(EObject eObject) {
    if (eObject instanceof FlowDefinition) {
      return createModel((FlowDefinition) eObject);
    }

    if (eObject instanceof GlobalDefinition) {
      return createModel((GlobalDefinition) eObject);
    }

    if (eObject instanceof VariableDeclaration) {
      return createModel((VariableDeclaration) eObject);
    }

    if (eObject instanceof OperationDefinition) {
      return createModel((OperationDefinition) eObject);
    }

    throw new IllegalStateException("Cannot generate component model for: " + eObject.getClass().getName());
  }

  public ComponentModel.Builder extractComponentDefinitionModel(String configFileName, EObject eObject, String elementIdentifier,
                                                                Map<String, String> attributes, String namespace,
                                                                boolean isRootElement) {
    // String elementIdentifier = ""; // name of the tag (flow, logger, set-variable, etc)
    String content = ""; // when a tag has content (like <description>This is the description</description>)
    int lineNumber = 0; // In the XML, should be the line number in the DSL (many elements can point to the same line)

    ComponentModel.Builder builder = new ComponentModel.Builder()
        .setIdentifier(builder()
            .namespace(namespace)
            .name(elementIdentifier)
            .build())
        // TODO(pablo.kraan): support placeholders with resolveValueIfIsPlaceHolder
        .setTextContent(content)
        .setConfigFileName(configFileName)
        .setLineNumber(lineNumber);
    to(builder);
    // TODO(pablo.kraan): node points to the XML... is this needed?
    // .addNode(from(configLine).getNode());

    // Adds the attributes to the model
    for (String attributeName : attributes.keySet()) {
      builder.addParameter(attributeName, attributes.get(attributeName), false);
    }

    for (EObject childObject : eObject.eContents()) {
      if (childObject instanceof ParamsCall) {
        ExpressionStatementResolver expressionStatementResolver = new ExpressionStatementResolver();
        StatementResolutionContext statementResolutionContext = new StatementResolutionContext();
        expressionStatementResolver.resolveParamsCall(statementResolutionContext, builder, (ParamsCall) childObject);
      } else {
        ComponentModel childModel = createModel(childObject);
        builder.addChildComponentModel(childModel);
      }
    }

    if (isRootElement) {
      builder.markAsRootComponent();
    }

    // ComponentModel componentModel = builder.build();
    // for (ComponentModel innerComponentModel : componentModel.getInnerComponents()) {
    // innerComponentModel.setParent(componentModel);
    // }
    // return componentModel;
    return builder;
  }


}
