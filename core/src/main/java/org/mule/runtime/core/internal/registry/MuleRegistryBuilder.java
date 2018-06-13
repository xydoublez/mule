package org.mule.runtime.core.internal.registry;

import javax.inject.Provider;

public interface MuleRegistryBuilder<T extends InternalRegistry> {


  MuleRegistryBuilder registerObject(String key, Object value);

  default MuleRegistryBuilder registerType(String key, Class<?> objectType) {
    return registerType(key, objectType, true);
  }

  MuleRegistryBuilder registerType(String key, Class<?> objectType, boolean singleton);

  default <T> MuleRegistryBuilder registerProvider(String key,
                                           Class<T> objectType,
                                           Class<? extends Provider<? extends T>> providerType) {
    
    return registerProvider(key, objectType, providerType, true);
  }

  <T> MuleRegistryBuilder registerProvider(String key,
                                           Class<T> objectType,
                                           Class<? extends Provider<? extends T>> providerType,
                                           boolean singleton);

  T build();

}
