/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model.metadata;

import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.internal.metadata.cache.MetadataCacheKeyGenerator;
import org.mule.runtime.core.internal.metadata.cache.MetadataCacheKeyGeneratorFactory;

import java.util.Optional;
import java.util.function.Function;

/**
 * //TODO
 */
public class ModelBasedMetadataCacheKeyGeneratorFactory implements MetadataCacheKeyGeneratorFactory<ComponentModel> {

  @Override
  public MetadataCacheKeyGenerator<ComponentModel> create(DslResolvingContext context,
                                                          Function<Location, Optional<ComponentModel>> locator) {
    return new ComponentBasedMetadataCacheKeyGenerator(context, locator);
  }
}
