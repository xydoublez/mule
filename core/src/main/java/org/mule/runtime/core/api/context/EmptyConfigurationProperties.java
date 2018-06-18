/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.context;

import static java.util.Optional.of;
import org.mule.runtime.api.component.ConfigurationProperties;

import java.util.Optional;

public class EmptyConfigurationProperties implements ConfigurationProperties {

  @Override
  public <T> Optional<T> resolveProperty(String s) {
    return of((T) "");
  }

  @Override
  public Optional<Boolean> resolveBooleanProperty(String s) {
    return of(false);
  }

  @Override
  public Optional<String> resolveStringProperty(String s) {
    return of("");
  }

}
