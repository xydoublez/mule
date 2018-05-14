/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.artifact.dsl.xml;

import static java.util.Collections.emptySet;
import static org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader.noValidationDocumentLoader;
import static org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader.schemaValidatingDocumentLoader;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.dsl.api.ResourceProvider;
import org.mule.runtime.dsl.api.config.ConfigResource;
import org.mule.runtime.dsl.internal.ClassLoaderResourceProvider;
import org.mule.runtime.dsl.internal.parser.XmlArtifactModelFactory;
import org.mule.runtime.dsl.internal.parser.xml.XmlArtifactParser;
import org.mule.runtime.dsl.internal.parser.xml.XmlConfigurationDocumentLoader;

public class ArtifactXmlBasedAstBuilder {

  private Set<URL> configFiles = new HashSet<>();
  private ConfigResource[] configResources;
  private ConfigResource[] applicationModelConfigResources;
  private Set<ExtensionModel> extensionModels = emptySet();
  private ClassLoader artifactClassLoader;
  private boolean disableXmlValidations = false;

  private ArtifactXmlBasedAstBuilder() {
    artifactClassLoader = Thread.currentThread().getContextClassLoader();
  }

  public static ArtifactXmlBasedAstBuilder builder() {
    return new ArtifactXmlBasedAstBuilder();
  }

  public ArtifactXmlBasedAstBuilder setConfigResources(ConfigResource[] configResources) {
    this.configResources = configResources;
    return this;
  }

  public ArtifactXmlBasedAstBuilder setExtensionModels(Set<ExtensionModel> extensionModels) {
    this.extensionModels = extensionModels;
    return this;
  }

  // TODO review this name
  public ArtifactXmlBasedAstBuilder setConfigUrls(Set<URL> configFiles) {
    this.configFiles = configFiles;
    this.configResources = loadConfigResourcesFromUrls(configFiles);
    this.applicationModelConfigResources = loadConfigResourcesFromUrls(configFiles);
    return this;
  }

  public ArtifactXmlBasedAstBuilder setConfigFiles(Set<String> configFiles) {
    try {
      this.configResources = loadConfigResources(configFiles);
      this.applicationModelConfigResources = loadConfigResources(configFiles);
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

  // TODO revie wthis name and loading from both URLs and Strings
  private ConfigResource[] loadConfigResourcesFromUrls(Set<URL> configs) {
    ConfigResource[] artifactConfigResources = new ConfigResource[configs.size()];
    int i = 0;
    for (URL config : configs) {
      artifactConfigResources[i] = new ConfigResource(config);
      i++;
    }
    return artifactConfigResources;
  }

  public ArtifactXmlBasedAstBuilder setClassLoader(ClassLoader artifactClassLoader) {
    this.artifactClassLoader = artifactClassLoader;
    return this;
  }

  public ArtifactXmlBasedAstBuilder setDisableXmlValidations(boolean disableXmlValidations) {
    this.disableXmlValidations = disableXmlValidations;
    return this;
  }

  public ArtifactAst build() {

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
    return new XmlArtifactModelFactory(extensionModels).createFrom(artifactDefinition, configFiles, disableXmlValidations,
                                                                   applicationModelConfigResources);
  }

}
