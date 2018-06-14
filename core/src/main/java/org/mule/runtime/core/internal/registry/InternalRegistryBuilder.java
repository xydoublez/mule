package org.mule.runtime.core.internal.registry;

import org.mule.runtime.core.api.MuleContext;

import javax.inject.Provider;

public interface InternalRegistryBuilder {

  InternalRegistryBuilder registerObject(String key, Object value);

  default InternalRegistryBuilder registerType(String key, Class<?> objectType) {
    return registerType(key, objectType, true);
  }

  InternalRegistryBuilder registerType(String key, Class<?> objectType, boolean singleton);

  default <T> InternalRegistryBuilder registerProvider(String key,
                                                       Class<T> objectType,
                                                       Class<? extends Provider<? extends T>> providerType) {
    
    return registerProvider(key, objectType, providerType, true);
  }

  <T> InternalRegistryBuilder registerProvider(String key,
                                               Class<T> objectType,
                                               Class<? extends Provider<? extends T>> providerType,
                                               boolean singleton);

  InternalRegistry build(MuleContext muleContext);

}
