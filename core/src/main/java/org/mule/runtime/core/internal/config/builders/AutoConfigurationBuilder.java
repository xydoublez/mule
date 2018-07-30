/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.config.builders;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.util.ClassUtils.getResource;
import static org.mule.runtime.core.api.util.PropertiesUtils.loadProperties;

import java.util.Map;
import java.util.Properties;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.component.ConfigurationProperties;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.i18n.I18nMessageFactory;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.core.api.config.builders.AbstractResourceConfigurationBuilder;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.core.internal.config.ParentMuleContextAwareConfigurationBuilder;

/**
 * Configures Mule from a configuration resource or comma separated list of configuration resources by auto-detecting the
 * ConfigurationBuilder to use for each resource. This is resolved by either checking the classpath for config modules e.g.
 * spring-config or by using the file extension or a combination.
 */
public class AutoConfigurationBuilder extends AbstractResourceConfigurationBuilder
    implements ParentMuleContextAwareConfigurationBuilder {

  private final ArtifactType artifactType;
  private MuleContext parentContext;

  public AutoConfigurationBuilder(ArtifactAst artifactAst, Map<String, String> artifactProperties, ArtifactType artifactType,
                                  ConfigurationProperties configurationProperties) {
    super(artifactAst, artifactProperties, configurationProperties);
    this.artifactType = artifactType;
  }

  @Override
  protected void doConfigure(MuleContext muleContext) throws ConfigurationException {
    autoConfigure(muleContext, artifactAst);
  }

  protected void autoConfigure(MuleContext muleContext, ArtifactAst artifactAst) throws ConfigurationException {
    try {
      Properties props = loadProperties(getResource("configuration-builders.properties", this.getClass()).openStream());

      String extension = "xml";

      String className = (String) props.get(extension);

      if (className == null || !ClassUtils.isClassOnPath(className, this.getClass())) {
        throw new ConfigurationException(I18nMessageFactory.createStaticMessage("could not find class " + className));
      }

      ConfigurationBuilder cb = (ConfigurationBuilder) ClassUtils
          .instantiateClass(className, new Object[] {
              artifactAst, getArtifactProperties(),
              artifactType});
      if (parentContext != null && cb instanceof ParentMuleContextAwareConfigurationBuilder) {
        ((ParentMuleContextAwareConfigurationBuilder) cb).setParentContext(parentContext);
      } else if (parentContext != null) {
        throw new MuleRuntimeException(createStaticMessage(format("ConfigurationBuilder %s does not support domain context",
                                                                  cb.getClass().getCanonicalName())));
      }
      cb.configure(muleContext);
    } catch (ConfigurationException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigurationException(e);
    }
  }

  @Override
  public void setParentContext(MuleContext parentContext) {
    this.parentContext = parentContext;
  }
}
