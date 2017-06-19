/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.loader.java;

import static org.mule.metadata.api.model.MetadataFormat.wkiForMimeType;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;
import org.mule.runtime.module.extension.internal.loader.java.property.ImplicitTransformerFactoryModelProperty;
import org.mule.runtime.module.extension.internal.loader.java.type.ExtensionElement;
import org.mule.runtime.module.extension.internal.runtime.transformer.DefaultImplicitTransformerFactory;
import org.mule.runtime.module.extension.internal.runtime.transformer.ImplicitTransformerFactory;

import java.util.List;

final class TransformerModelLoaderDelegate extends AbstractModelLoaderDelegate {

  public TransformerModelLoaderDelegate(DefaultJavaModelLoaderDelegate loader) {
    super(loader);
  }

  void declareTransformers(ExtensionDeclarer declarer, ExtensionElement extensionElement) {
    extensionElement.getTransformers().forEach(transformerElement -> {
      ImplicitTransformerFactory factory = new DefaultImplicitTransformerFactory(transformerElement.getDeclaringClass());

      ImplicitTransformer transformer = factory.create();

      declarer.withTransformer(transformer.getName())
          .withSourceType(toMetadataType(transformer.getSourceTypes()))
          .withOutput(toMetadataType(transformer.getOutputType()))
          .withModelProperty(new ImplicitTransformerFactoryModelProperty(factory));
    });
  }

  private MetadataType[] toMetadataType(List<DataType> dataTypes) {
    return dataTypes.stream().map(this::toMetadataType).toArray(MetadataType[]::new);
  }

  private MetadataType toMetadataType(DataType dataType) {
    final Class<?> type = dataType.getType();
    if (dataType.getMediaType() == ANY) {
      return loader.getTypeLoader().load(type);
    }

    final String mimeType = dataType.getMediaType().toString();
    return wkiForMimeType(mimeType)
        .map(format -> loader.getTypeLoader().load(type, format))
        .orElseGet(() -> {
          String label = String.format("%s-%s", type.getName(), mimeType);
          MetadataFormat format = new MetadataFormat(label, label.toLowerCase(), mimeType);
          return loader.getTypeLoader().load(type, format);
        });
  }
}
