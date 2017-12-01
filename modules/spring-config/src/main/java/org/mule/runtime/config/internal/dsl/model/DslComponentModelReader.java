/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.config.internal.dsl.model;

import static org.mule.runtime.api.component.ComponentIdentifier.buildFromStringRepresentation;
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
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.xtext.example.mydsl.myDsl.CommonStatement;
import org.xtext.example.mydsl.myDsl.ExpressionStatement;
import org.xtext.example.mydsl.myDsl.FlowDefinition;
import org.xtext.example.mydsl.myDsl.GlobalDefinition;
import org.xtext.example.mydsl.myDsl.ParamsCall;
import org.xtext.example.mydsl.myDsl.RouterConstruct;
import org.xtext.example.mydsl.myDsl.ScopeConstruct;
import org.xtext.example.mydsl.myDsl.VariableDeclaration;
import org.xtext.example.mydsl.myDsl.impl.ConstructCallImpl;

public class DslComponentModelReader {

  private final ConfigurationPropertiesResolver configurationPropertiesResolver;

  public DslComponentModelReader(ConfigurationPropertiesResolver configurationPropertiesResolver) {
    this.configurationPropertiesResolver = configurationPropertiesResolver;
  }

  public ComponentModel extractComponentDefinitionModel(ConfigFile configFile) {
    Resource resource = new XtextParser().parse(configFile);

    ComponentModel.Builder builder = new ComponentModel.Builder();

    //// TODO(pablo.kraan): check these constants for the identifier
    builder.setIdentifier(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("mule").build());
    // String namespace = CORE_PREFIX ;//configLine.getNamespace() == null ? CORE_PREFIX : configLine.getNamespace();
    // TODO(pablo.kraan): need config filename here
    ComponentModel componentModel = builder.setConfigFileName(configFile.getFilename()).build();
    // .setIdentifier(builder()
    // .namespace(namespace)
    // .name("mule")
    // .build())
    // // TODO(pablo.kraan): content?
    // //.setTextContent(resolveValueIfIsPlaceHolder(configLine.getTextContent()))
    // .setConfigFileName(configFile.getFilename())
    // // TODO(pablo.kraan): line number?
    // //.setLineNumber(configLine.getLineNumber())
    // ;
    // to(builder).addNode(from(configLine).getNode());
    // for (SimpleConfigAttribute simpleConfigAttribute : configLine.getConfigAttributes().values()) {
    // builder.addParameter(simpleConfigAttribute.getName(), resolveValueIfIsPlaceHolder(simpleConfigAttribute.getValue()),
    // simpleConfigAttribute.isValueFromSchema());
    // }
    //
    // List<ComponentModel> componentModels = configLine.getChildren().stream()
    // .map(childConfigLine -> extractComponentDefinitionModel(childConfigLine, configFileName))
    // .collect(Collectors.toList());
    // componentModels.stream().forEach(componentDefinitionModel -> builder.addChildComponentModel(componentDefinitionModel));
    // ConfigLine parent = configLine.getParent();
    // if (parent != null && isConfigurationTopComponent(parent)) {
    // builder.markAsRootComponent();
    // }

    if (!resource.getContents().isEmpty()) {
      List<ComponentModel> componentModels = new ArrayList<>();
      for (EObject resourceContent : resource.getContents().get(0).eContents()) {
        ComponentModel model = createModel(resourceContent);
        componentModels.add(model);
      }
      componentModels.stream().forEach(componentDefinitionModel -> builder.addChildComponentModel(componentDefinitionModel));

      for (ComponentModel innerComponentModel : componentModel.getInnerComponents()) {
        innerComponentModel.setParent(componentModel);
      }
    }

    return componentModel;
  }

  // private String resolveValueIfIsPlaceHolder(String value) {
  // Object resolvedValue = configurationPropertiesResolver.resolveValue(value);
  // return resolvedValue instanceof String ? (String) resolvedValue : (resolvedValue != null ? resolvedValue.toString() : null);
  // }

  private ComponentModel createModel(FlowDefinition flowDefinition) {
    System.out.println("flow createModel: " + flowDefinition);

    Map<String, String> attributes = new HashMap<>();
    attributes.put("name", flowDefinition.getName());

    ComponentModel.Builder componentModelBuilder =
      extractComponentDefinitionModel("zaraza", flowDefinition, "flow", attributes, CORE_PREFIX, true);

    EList<CommonStatement> statements = flowDefinition.getStatements();
    resolveStatements(componentModelBuilder, statements);
    return componentModelBuilder.build();
  }

  private void resolveStatements(ComponentModel.Builder parentComponentModelBuilder, EList<CommonStatement> statements) {
    for (CommonStatement statement : statements) {

      if (statement instanceof VariableDeclaration) {

      } else if (statement instanceof ExpressionStatement) {
        ExpressionStatementResolver expressionStatementResolver = new ExpressionStatementResolver();
        StatementResolutionContext statementResolutionContext = new StatementResolutionContext();
        expressionStatementResolver.resolveComplexStatement((ExpressionStatement) statement, statementResolutionContext);
        statementResolutionContext.getComponentModels();
        for (ComponentModel componentModel : statementResolutionContext.getComponentModels()) {
          parentComponentModelBuilder.addChildComponentModel(componentModel);
        }
      } else if (statement instanceof ScopeConstruct) {
        ComponentModel.Builder foreachComponentBuilder = processScope((ScopeConstruct) statement);
        parentComponentModelBuilder.addChildComponentModel(foreachComponentBuilder.build());
      } else if (statement instanceof RouterConstruct) {
        RouterConstruct routerConstruct = (RouterConstruct) statement;
        ParamsCall paramsCall = routerConstruct.getParams();
        ComponentModel.Builder routerConstructModel = new ComponentModel.Builder();
        routerConstructModel.setIdentifier(buildFromStringRepresentation(routerConstruct.getName().replace("::", ":")));
        routerConstructModel.setLineNumber(23);
        routerConstructModel.setConfigFileName("sdf");
        for (ScopeConstruct scopeConstruct : routerConstruct.getRoutes()) {
          ComponentModel.Builder foreachComponentBuilder = processScope(scopeConstruct);
          routerConstructModel.addChildComponentModel(foreachComponentBuilder.build());
        }
        parentComponentModelBuilder.addChildComponentModel(routerConstructModel.build());
      } else {
        throw new IllegalStateException();
      }
    }
  }

  private ComponentModel.Builder processScope(ScopeConstruct statement) {
    ScopeConstruct scopeConstruct = statement;
    ParamsCall paramsCall = scopeConstruct.getParams();
    ComponentModel.Builder foreachComponentBuilder = new ComponentModel.Builder();
    foreachComponentBuilder.setIdentifier(buildFromStringRepresentation(scopeConstruct.getName().replace("::", ":")));
    foreachComponentBuilder.setLineNumber(23);
    foreachComponentBuilder.setConfigFileName("sdf");
    resolveStatements(foreachComponentBuilder, scopeConstruct.getStatements());
    return foreachComponentBuilder;
  }

  private ComponentModel createModel(GlobalDefinition globalDefinition) {
    System.out.println("global def createModel: " + globalDefinition);

    Map<String, String> attributes = new HashMap<>();
    attributes.put("name", globalDefinition.getName());

    EObject childObject = globalDefinition.eContents().get(0);
    String[] split = ((ConstructCallImpl) childObject).getName().split("::");
    String nameSpace = split[0];
    String name = hyphenize(split[1]);

    return extractComponentDefinitionModel("zaraza", childObject, name, attributes, nameSpace,true).build();
  }

  private ComponentModel createModel(VariableDeclaration variableDeclaration) {
    System.out.println("var createModel: " + variableDeclaration);


    EObject variableValue = variableDeclaration.eContents().get(0);
    if (variableValue instanceof ExpressionStatement) {
      // TODO(pablo.kraan): add support ofr DW expressions
      Map<String, String> attributes = new HashMap<>();
      attributes.put("variableName", variableDeclaration.getName());
      // Removes additional quotes
      String literalValue = ((ExpressionStatement) variableValue).getListeral();
      attributes.put("value", literalValue.substring(1, literalValue.length()-1));
      return extractComponentDefinitionModel("zaraza", variableValue, "set-variable", attributes, CORE_PREFIX,false).build();
    } else {
      return null;
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

    throw new IllegalStateException("Cannot generate component model for: " + eObject.getClass().getName());
    //System.out.println("generic createModel: " + eObject);
    //return extractComponentDefinitionModel("zaraza", eObject, "");
  }

  public ComponentModel.Builder extractComponentDefinitionModel(String configFileName, EObject eObject, String elementIdentifier,
                                                        Map<String, String> attributes, String namespace, boolean isRootElement) {
    //String elementIdentifier = "";  // name of the tag (flow, logger, set-variable, etc)
    String content = ""; // when a tag has content (like <description>This is the description</description>)
    int lineNumber = 0; // In the XML, should be the line number in the DSL (many elements can point to the same line)

    // TODO(pablo.kraan): need the real name space

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
    // TODO(pablo.kraan): node points to the XML... is needed?
    // .addNode(from(configLine).getNode());


    // TODO(pablo.kraan): here it must take the parameter from the DSL
    for (String attributeName : attributes.keySet()) {
      // TODO(pablo.kraan): resolve placeholders
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

    //ComponentModel componentModel = builder.build();
    //for (ComponentModel innerComponentModel : componentModel.getInnerComponents()) {
    //  innerComponentModel.setParent(componentModel);
    //}
    //return componentModel;
    return builder;
  }


}
