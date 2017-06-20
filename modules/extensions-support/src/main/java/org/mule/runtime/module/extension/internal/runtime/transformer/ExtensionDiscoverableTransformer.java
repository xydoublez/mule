/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.transformer;

import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getClassLoader;
import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Lifecycle;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.transformer.TransformerModel;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.transformer.AbstractDiscoverableTransformer;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;

import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtensionDiscoverableTransformer extends AbstractDiscoverableTransformer implements Lifecycle {

  private final static Logger LOGGER = LoggerFactory.getLogger(ExtensionDiscoverableTransformer.class);

  private final ExtensionModel extensionModel;
  private final TransformerModel transformerModel;
  private final ClassLoader extensionClassLoader;
  private final ImplicitTransformer delegate;

  public ExtensionDiscoverableTransformer(ExtensionModel extensionModel, TransformerModel transformerModel,
                                          ImplicitTransformer delegate) {
    this.extensionModel = extensionModel;
    this.transformerModel = transformerModel;
    this.delegate = delegate;
    extensionClassLoader = getClassLoader(extensionModel);
    delegate.getSourceTypes().forEach(this::registerSourceType);
    setReturnDataType(delegate.getOutputType());
  }

  @Override
  protected Object doTransform(Object src, Charset enc) throws TransformerException {
    return withContextClassLoader(extensionClassLoader, () -> delegate.transform(src));
  }

  @Override
  protected String generateTransformerName() {
    return extensionModel.getName() + ":" + transformerModel.getName();
  }

  @Override
  public void initialise() throws InitialisationException {
    super.initialise();
    initialiseIfNeeded(delegate, true, muleContext);
  }

  @Override
  public void start() throws MuleException {
    startIfNeeded(delegate);
  }

  @Override
  public void stop() throws MuleException {
    stopIfNeeded(delegate);
  }

  @Override
  public void dispose() {
    super.dispose();
    disposeIfNeeded(delegate, LOGGER);
  }

  private String getFirstMediaType(MetadataType metadataType) {
    return metadataType.getMetadataFormat().getValidMimeTypes().iterator().next();
  }
}
