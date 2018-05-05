/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import java.util.Properties;

import org.mule.runtime.api.artifact.ast.ComplexParameterValueAst;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.config.api.dsl.processor.ConfigLine;
import org.mule.runtime.config.internal.ComponentAstHolder;
import org.mule.runtime.config.internal.model.ComponentModel;

/**
 * Class used to read xml files from {@link ConfigLine}s, unifying knowledge on how to properly read the files returning the
 * {@link ComponentModel} object.
 *
 * It also replaces the values of the attributes by using the {@link Properties} object parametrized in its constructor.
 */
public class ComponentModelReader {

  private final ConfigurationProperties configurationProperties;

  public ComponentModelReader(ConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
  }

  public ComponentModel extractComponentDefinitionModel(ComponentAstHolder componentAstHolder) {

    ComponentModel.Builder builder = new ComponentModel.Builder()
        .setIdentifier(componentAstHolder.getComponentAst().getComponentIdentifier())
        // .setTextContent(resolveValueIfIsPlaceHolder(configLine.getTextContent())) //TODO what should we do
        .setSourceCodeLocation(componentAstHolder.getComponentAst().getSourceCodeLocation());

    componentAstHolder.getParameters().stream()
        .forEach(parameterAstHolder -> {
          if (parameterAstHolder.isSimpleParameter()) {
            // TODO this should be the concatentaion of the namespace with the name
            builder.addParameter(parameterAstHolder.getParameterAst().getParameterIdentifier().getIdentifier().getName(),
                                 resolveValueIfIsPlaceHolder(parameterAstHolder.getSimpleParameterValueAst().getRawValue()),
                                 false); // TODO review this false.
          } else {
            ComplexParameterValueAst compleParameterValueAst = parameterAstHolder.getComplexParameterValueAst();
            builder.addChildComponentModel(extractComponentDefinitionModel(new ComponentAstHolder(compleParameterValueAst
                .getComponent()))); // TODO review this new ComponentstHolder
          }
        });

    ComponentModel componentModel = builder.build();
    for (ComponentModel innerComponentModel : componentModel.getInnerComponents()) {
      innerComponentModel.setParent(componentModel);
    }
    return componentModel;
  }

  private String resolveValueIfIsPlaceHolder(String value) {
    Object resolvedValue = configurationProperties.resolveProperty(value);
    return resolvedValue instanceof String ? (String) resolvedValue : (resolvedValue != null ? resolvedValue.toString() : null);
  }

}
