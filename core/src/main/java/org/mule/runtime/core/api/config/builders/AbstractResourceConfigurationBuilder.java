/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.config.builders;

import static org.mule.runtime.core.api.config.i18n.CoreMessages.objectIsNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;

import org.mule.api.annotation.NoExtend;
import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigResource;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;

import org.slf4j.Logger;

/**
 * Abstract {@link ConfigurationBuilder} implementation used for ConfigurationBuider's that use one of more configuration
 * resources of the same type that are defined using strings or {@link ConfigResource} objects. It is recommended that
 * {@link ConfigResource} objects are used over strings since they can be more descriptive, but Strings will be supported for
 * quite some time.
 */
@NoExtend
public abstract class AbstractResourceConfigurationBuilder extends AbstractConfigurationBuilder {

  private final Map<String, String> artifactProperties;
  protected ArtifactAst artifactAst;

  /**
   * @param artifactConfigResources a comma separated list of configuration files to load, this should be accessible on the
   *        classpath or filesystem
   * @param artifactProperties map of properties that can be referenced from the {@code artifactConfigResources} as external
   *        configuration values
   * @throws org.mule.runtime.core.api.config.ConfigurationException usually if the config resources cannot be loaded
   */
  public AbstractResourceConfigurationBuilder(ArtifactAst artifactAst, Map<String, String> artifactProperties) {
    this.artifactAst = artifactAst;
    this.artifactProperties = artifactProperties;
  }

  /**
   * Override to check for existence of configResouce before invocation, and set resources n configuration afterwards.
   */
  @Override
  public void configure(MuleContext muleContext) throws ConfigurationException {
    if (artifactAst == null) {
      throw new ConfigurationException(objectIsNull("Configuration Resources"));
    }

    super.configure(muleContext);
  }


  public Map<String, String> getArtifactProperties() {
    return artifactProperties;
  }
}
