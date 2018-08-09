/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.metadata.cache;

import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.dsl.DslResolvingContext;

import java.util.Optional;
import java.util.function.Function;

/**
 * //TODO
 */
public interface MetadataCacheKeyGeneratorFactory<T> {

  MetadataCacheKeyGenerator<T> create(DslResolvingContext context, Function<Location, Optional<T>> locator);

}
