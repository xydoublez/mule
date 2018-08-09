/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.metadata.cache;

import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_STORE_MANAGER;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lock.LockFactory;
import org.mule.runtime.api.store.ObjectDoesNotExistException;
import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.api.store.ObjectStoreSettings;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.core.internal.metadata.DefaultMetadataCache;

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * //TODO
 */
public class MetadataCacheManager implements Startable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataCache.class);

  @Inject
  @Named(OBJECT_STORE_MANAGER)
  private ObjectStoreManager objectStoreManager;

  @Inject
  private LockFactory lockFactory;

  private LazyValue<ObjectStore<MetadataCache>> metadataStore;

  @Override
  public void start() {
    metadataStore = new LazyValue<>(
                                    () -> objectStoreManager.getOrCreateObjectStore("_mulePersistentMetadataService",
                                                                                    ObjectStoreSettings.builder()
                                                                                        .persistent(true)
                                                                                        .build()));
  }

  public MetadataCache getCache(String keyHash) {
    return withKeyLock(keyHash, key -> {
      try {
        if (metadataStore.get().contains(key)) {
          return metadataStore.get().retrieve(key);
        }

        DefaultMetadataCache metadataCache = new DefaultMetadataCache();
        metadataStore.get().store(key, metadataCache);
        return metadataCache;

      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
  }

  public void dispose(String keyHash) {
    withKeyLock(keyHash, key -> {
      try {
        metadataStore.get().remove(key);
      } catch (ObjectDoesNotExistException e) {
        LOGGER.debug("Key not found " + key);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
  }

  private <T> T withKeyLock(String key, Function<String, T> producer) {
    Lock lock = lockFactory.createLock(key);
    lock.lock();
    try {
      return producer.apply(key);
    } finally {
      lock.unlock();
    }
  }

  private void withKeyLock(String key, Consumer<String> consumer) {
    Lock lock = lockFactory.createLock(key);
    lock.lock();
    try {
      consumer.accept(key);
    } finally {
      lock.unlock();
    }
  }

  public interface MetadataCache extends org.mule.runtime.api.metadata.MetadataCache, Serializable {
  }

}
