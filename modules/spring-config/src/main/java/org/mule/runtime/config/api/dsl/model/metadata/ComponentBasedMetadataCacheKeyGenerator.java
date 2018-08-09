/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.model.metadata;

import static org.mule.runtime.api.util.Preconditions.checkArgument;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.config.api.dsl.model.DslElementModelFactory;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.internal.metadata.cache.CacheKeyPart;
import org.mule.runtime.core.internal.metadata.cache.MetadataCacheKeyGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * //TODO
 */
public class ComponentBasedMetadataCacheKeyGenerator implements MetadataCacheKeyGenerator<ComponentModel> {

  private final DslElementModelFactory elementModelFactory;
  private final DslElementBasedMetadataCacheKeyGenerator delegate;

  ComponentBasedMetadataCacheKeyGenerator(DslResolvingContext context,
                                          Function<Location, Optional<ComponentModel>> locator) {
    this.elementModelFactory = DslElementModelFactory.getDefault(context);
    this.delegate = new DslElementBasedMetadataCacheKeyGenerator(location -> locator.apply(location)
        .map(c -> elementModelFactory.create(c.getConfiguration())
            .orElse(null)));
  }

  @Override
  public List<CacheKeyPart> generateKey(ComponentModel component) {
    checkArgument(component != null, "Cannot generate a Cache Key for a 'null' component");
    return elementModelFactory.create(component.getConfiguration())
        .map(delegate::generateKey)
        .orElse(Collections.emptyList());
  }

}

