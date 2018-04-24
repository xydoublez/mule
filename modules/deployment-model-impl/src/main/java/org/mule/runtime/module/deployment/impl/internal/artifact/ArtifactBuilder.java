/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.artifact;

import static org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader.noValidationDocumentLoader;
import static org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader.schemaValidatingDocumentLoader;

import java.io.IOException;
import java.util.Set;

import org.mule.runtime.api.artifact.semantic.Artifact;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.dsl.api.ResourceProvider;
import org.mule.runtime.dsl.api.config.ConfigResource;
import org.mule.runtime.dsl.internal.ClassLoaderResourceProvider;
import org.mule.runtime.dsl.internal.parser.ArtifactModelFactory;
import org.mule.runtime.dsl.internal.parser.xml.XmlArtifactParser;
import org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader;

public class ArtifactBuilder {

  private ConfigResource[] configResources;
  private Set<ExtensionModel> extensionModels;
  private ClassLoader artifactClassLoader;
  private boolean disableXmlValidations = false;

  public ArtifactBuilder setConfigResources(ConfigResource[] configResources) {
    this.configResources = configResources;
    return this;
  }

  public ArtifactBuilder setExtensionModels(Set<ExtensionModel> extensionModels) {
    this.extensionModels = extensionModels;
    return this;
  }

  public ArtifactBuilder setConfigFiles(Set<String> configFiles) {
    try {
      this.configResources = loadConfigResources(configFiles);
      return this;
    } catch (ConfigurationException e) {
      throw new MuleRuntimeException(e);
    }
  }

  private ConfigResource[] loadConfigResources(Set<String> configs) throws ConfigurationException {
    try {
      ConfigResource[] artifactConfigResources = new ConfigResource[configs.size()];
      int i = 0;
      for (String config : configs) {
        artifactConfigResources[i] = new ConfigResource(config);
        i++;
      }
      return artifactConfigResources;
    } catch (IOException e) {
      throw new ConfigurationException(e);
    }
  }

  public ArtifactBuilder setClassLoader(ClassLoader artifactClassLoader) {
    this.artifactClassLoader = artifactClassLoader;
    return this;
  }

  public ArtifactBuilder setDisableXmlValidations(boolean disableXmlValidations) {
    this.disableXmlValidations = disableXmlValidations;
    return this;
  }

  public Artifact build() {

    // TODO replace this thing
    // EnvironmentPropertiesConfigurationProvider environmentPropertiesConfigurationProvider = new
    // EnvironmentPropertiesConfigurationProvider();

    ResourceProvider externalResourceProvider = new ClassLoaderResourceProvider(artifactClassLoader);

    XmlConfigurationDocumentLoader xmlConfigurationDocumentLoader =
        disableXmlValidations ? noValidationDocumentLoader() : schemaValidatingDocumentLoader();


    // use proper property resolver
    XmlArtifactParser xmlArtifactParser =
        new XmlArtifactParser(configResources, xmlConfigurationDocumentLoader,
                              extensionModels, externalResourceProvider, value -> value);

    ArtifactDefinition artifactDefinition = xmlArtifactParser.parse();
    return new ArtifactModelFactory(extensionModels).createFrom(artifactDefinition);
  }

}
