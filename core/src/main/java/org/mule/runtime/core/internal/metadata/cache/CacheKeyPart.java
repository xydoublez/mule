/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.metadata.cache;

import java.util.Objects;
import java.util.Optional;

/**
 * //TODO
 */
public class CacheKeyPart {

  private final String hash;
  private final String location;

  public CacheKeyPart(String hash, String location) {
    this.hash = hash;
    this.location = location;
  }

  public String getHash() {
    return hash;
  }

  public Optional<String> getLocation() {
    return Optional.ofNullable(location);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CacheKeyPart that = (CacheKeyPart) o;
    return Objects.equals(hash, that.hash) &&
      Objects.equals(location, that.location);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hash, location);
  }

  @Override
  public String toString() {
    return '(' + hash + ',' + location + ')';
  }
}
