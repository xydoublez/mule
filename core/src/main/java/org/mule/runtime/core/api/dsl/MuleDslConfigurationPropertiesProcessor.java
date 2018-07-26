/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.dsl.ConfigurationPropertyResolutionException;
import org.mule.runtime.api.dsl.ResolvedValue;
import org.mule.runtime.api.util.Either;
import org.mule.runtime.core.internal.dsl.properties.ConfigurationPropertiesResolver;
import org.mule.runtime.dsl.api.ResourceProvider;

public class MuleDslConfigurationPropertiesProcessor {


  private final ConfigurationPropertiesResolver configurationPropertiesResolver;
  private final ResourceProvider resourceProvider;
  private ArtifactAst artifactAst;

  public MuleDslConfigurationPropertiesProcessor(ArtifactAst artifactAst,
                                                 ConfigurationPropertiesResolver configurationPropertiesResolver,
                                                 ResourceProvider resourceProvider) {
    this.artifactAst = artifactAst;
    this.configurationPropertiesResolver = configurationPropertiesResolver;
    this.resourceProvider = resourceProvider;
  }

  public void resolveConfigurationPlaceholders() {
    new ArtifactAstHelper(artifactAst).executeOnEverySimpleParameterAst(simpleParameterValueAst -> {
      String rawValue = simpleParameterValueAst.getRawValue();
      try {
        Object value = this.configurationPropertiesResolver.resolveValue(rawValue);
        simpleParameterValueAst.setResolvedValueResult(Either.right(new ResolvedValue(value)));
      } catch (ConfigurationPropertyResolutionException e) {
        simpleParameterValueAst.setResolvedValueResult(Either.left(e));
      }
    });
  }

  // TODO return validation result.
  public void semanticValidation() {

  }



}
