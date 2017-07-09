/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.api;

import org.mule.runtime.api.app.declaration.ArtifactDeclaration;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.config.spring.dsl.model.internal.DefaultModuleDeclarationXmlSerializer;
import org.mule.runtime.extension.api.module.ModuleDeclaration;

import java.io.InputStream;

import org.w3c.dom.Document;

/**
 *
 * Serializer that can convert an {@link ArtifactDeclaration} into a readable and processable XML representation
 * and from a mule XML configuration file into an {@link ArtifactDeclaration} representation.
 *
 * @since 4.0
 */
public interface ModuleDeclarationXmlSerializer {

  /**
   * Provides an instance of the default implementation of the {@link ModuleDeclarationXmlSerializer}.
   *
   * @param context a {@link DslResolvingContext} that provides access to all the {@link ExtensionModel extensions}
   *                required for loading a given {@code artifact config} to an {@link ModuleDeclaration}
   * @return an instance of the default implementation of the {@link ModuleDeclarationXmlSerializer}
   */
  static ModuleDeclarationXmlSerializer getDefault(DslResolvingContext context) {
    return new DefaultModuleDeclarationXmlSerializer(context);
  }

  /**
   * Serializes an {@link ModuleDeclaration} into an XML {@link Document}
   *
   * @param declaration {@link ModuleDeclaration} to be serialized
   * @return an XML representation of the {@link ModuleDeclaration}
   */
  String serialize(ModuleDeclaration declaration);

  /**
   * Creates an {@link ModuleDeclaration} from a given mule artifact XML configuration file.
   *
   * @param configResource the input stream with the XML configuration content.
   * @return an {@link ModuleDeclaration} that represents the given mule configuration.
   */
  ModuleDeclaration deserialize(InputStream configResource);

}
