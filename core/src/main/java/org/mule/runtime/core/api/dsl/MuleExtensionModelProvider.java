/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.dsl;

import static com.google.common.collect.ImmutableSet.copyOf;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.dsl.api.ExtensionModelProvider;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class MuleExtensionModelProvider implements ExtensionModelProvider {

  private Set<ExtensionModel> extensionModels;
  private Cache<String, Optional<ExtensionModel>> extensionModelPerNamespaceCache = CacheBuilder.newBuilder().build();

  public MuleExtensionModelProvider(Set<ExtensionModel> extensionModels) {
    this.extensionModels = extensionModels;
  }

  @Override
  public Optional<ExtensionModel> getExtensionModel(String extensionNamespace) {
    try {
      return extensionModelPerNamespaceCache.get(extensionNamespace, () -> {
        for (ExtensionModel extensionModel : extensionModels) {
          if (extensionModel.getName().equals(extensionNamespace)) {
            return of(extensionModel);
          }
        }
        return empty();
      });
    } catch (ExecutionException e) {
      throw new MuleRuntimeException(e);
    }
  }

  @Override
  public Set<ExtensionModel> getExtensionModels() {
    return copyOf(extensionModels);
  }
}
