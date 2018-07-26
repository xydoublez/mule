/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl.xml;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.core.internal.dsl.ClassLoaderResourceProvider;
import org.mule.runtime.core.internal.dsl.MuleExtensionModelProvider;
import org.mule.runtime.dsl.xml.api.ArtifactXmlBasedAstBuilder;
import org.mule.runtime.dsl.xml.internal.parser.MuleExtensionSchemaProvider;
import org.mule.runtime.dsl.xml.internal.parser.MuleXmlNamespaceInfoProvider;
import org.mule.runtime.extension.api.dsl.syntax.resources.spi.ExtensionSchemaGenerator;

public class MuleArtifactXmlBasedAstBuilder {

  private MuleArtifactXmlBasedAstBuilder() {}

  public static ArtifactXmlBasedAstBuilder builder() {
    return builder(Collections.emptySet());
  }

  public static ArtifactXmlBasedAstBuilder builder(Set<ExtensionModel> extensionModels) {
    return ArtifactXmlBasedAstBuilder.builder()
        .setDisableXmlValidations(false)
        .setExtensionModelProvider(new MuleExtensionModelProvider(extensionModels))
        .setExtensionSchemaProvider(new MuleExtensionSchemaProvider(extensionModels, getExtensionSchemaGenerator()))
        .setNamespaceInfoProvider(new MuleXmlNamespaceInfoProvider(extensionModels))
        .setResourceProvider(new ClassLoaderResourceProvider(Thread.currentThread().getContextClassLoader()));
  }

  private static Optional<ExtensionSchemaGenerator> getExtensionSchemaGenerator() {
    SpiServiceRegistry spiServiceRegistry = new SpiServiceRegistry();
    final Collection<ExtensionSchemaGenerator> extensionSchemaGenerators =
        spiServiceRegistry.lookupProviders(ExtensionSchemaGenerator.class, MuleArtifactXmlBasedAstBuilder.class.getClassLoader());
    if (extensionSchemaGenerators.isEmpty()) {
      return empty();
    } else if (extensionSchemaGenerators.size() == 1) {
      return of(extensionSchemaGenerators.iterator().next());
    } else {
      throw new IllegalArgumentException(format("There are '%s' providers for '%s' when there must be 1 or zero.",
                                                extensionSchemaGenerators.size(), ExtensionSchemaGenerator.class.getName()));
    }
  }
}
