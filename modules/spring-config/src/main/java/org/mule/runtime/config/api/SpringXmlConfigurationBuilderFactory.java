/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api;

import static java.util.Collections.emptyMap;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;

import java.util.Map;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.config.internal.SpringXmlConfigurationBuilder;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;

/**
 * @since 4.0
 */
public final class SpringXmlConfigurationBuilderFactory {

  private SpringXmlConfigurationBuilderFactory() {
    // Nothing to do
  }

  public static ConfigurationBuilder createConfigurationBuilder(ArtifactAst artifactAst,
                                                                ConfigurationProperties configurationProperties)
      throws ConfigurationException {
    return new SpringXmlConfigurationBuilder(artifactAst, configurationProperties);
  }

  public static ConfigurationBuilder createConfigurationBuilder(ArtifactAst artifactAst,
                                                                ConfigurationProperties configurationProperties, boolean lazyInit)
      throws ConfigurationException {
    return new SpringXmlConfigurationBuilder(artifactAst, configurationProperties, lazyInit);
  }

  public static ConfigurationBuilder createConfigurationBuilder(ArtifactAst artifactAst, MuleContext domainContext,
                                                                ConfigurationProperties configurationProperties)
      throws ConfigurationException {
    final SpringXmlConfigurationBuilder springXmlConfigurationBuilder =
        new SpringXmlConfigurationBuilder(artifactAst, emptyMap(), configurationProperties, APP, false);
    if (domainContext != null) {
      springXmlConfigurationBuilder.setParentContext(domainContext);
    }
    return springXmlConfigurationBuilder;
  }

  public static ConfigurationBuilder createConfigurationBuilder(ArtifactAst artifactAst, Map<String, String> artifactProperties,
                                                                ArtifactType artifactType,
                                                                ConfigurationProperties configurationProperties)
      throws ConfigurationException {
    return new SpringXmlConfigurationBuilder(artifactAst, artifactProperties, configurationProperties, artifactType);
  }

  public static ConfigurationBuilder createConfigurationBuilder(ArtifactAst artifactAst, Map<String, String> artifactProperties,
                                                                ArtifactType artifactType,
                                                                ConfigurationProperties configurationProperties,
                                                                boolean enableLazyInit)
      throws ConfigurationException {
    return new SpringXmlConfigurationBuilder(artifactAst, artifactProperties, configurationProperties, artifactType,
                                             enableLazyInit);
  }

}
