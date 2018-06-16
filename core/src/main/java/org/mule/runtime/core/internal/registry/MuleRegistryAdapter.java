/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import static java.util.Collections.sort;
import static org.mule.runtime.api.metadata.DataType.builder;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import static org.mule.runtime.core.api.config.i18n.CoreMessages.noTransformerFoundForMessage;
import static org.mule.runtime.core.internal.util.ConcurrencyUtils.withLock;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.LifecycleException;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.transformer.Converter;
import org.mule.runtime.core.api.transformer.Transformer;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.internal.transformer.ResolverException;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Adds lookup/register/unregister methods for Mule-specific entities to the standard Registry interface.
 */
public class MuleRegistryAdapter implements MuleRegistry {

  /**
   * We cache transformer searches so that we only search once
   */
  protected ConcurrentHashMap<String, Transformer> exactTransformerCache = new ConcurrentHashMap<>(8);
  protected ConcurrentHashMap<String, List<Transformer>> transformerListCache = new ConcurrentHashMap<>(8);

  private final MuleContext muleContext;
  private final InternalRegistry registry;

  /**
   * Transformer transformerResolvers are registered on context start, then they are not unregistered.
   */
  private List<TransformerResolver> transformerResolvers = new ArrayList<>();
  private final ReadWriteLock transformerResolversLock = new ReentrantReadWriteLock();


  /**
   * Transformers are registered on context start, then they are usually not unregistered
   */
  private Collection<Transformer> transformers = new LinkedList<>();
  private final ReadWriteLock transformersLock = new ReentrantReadWriteLock();

  public MuleRegistryAdapter(InternalRegistry registry, MuleContext muleContext) {
    this.registry = registry;
    this.muleContext = muleContext;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initialise() throws InitialisationException {
    transformerResolvers = (List<TransformerResolver>) registry.lookupObjects(TransformerResolver.class);
    sortTransformerResolvers();

    transformers.addAll(registry.lookupObjects(Converter.class));
    transformers.forEach(this::notifyTransformerResolvers);
  }

  private void sortTransformerResolvers() {
    sort(transformerResolvers, new TransformerResolverComparator());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void registerTransformer(Transformer transformer) throws MuleException {
    withLock(transformerResolversLock.writeLock(), () -> {
      if (transformer instanceof TransformerResolver) {
        transformerResolvers.add(((TransformerResolver) transformer));
        sortTransformerResolvers();
      }
    });

    notifyTransformerResolvers(transformer);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void dispose() {
    transformerListCache.clear();
    exactTransformerCache.clear();
    registry.dispose();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void fireLifecycle(String phase) throws LifecycleException {
    if (Initialisable.PHASE_NAME.equals(phase)) {
      registry.initialise();
    } else if (Disposable.PHASE_NAME.equals(phase)) {
      registry.dispose();
    } else {
      registry.fireLifecycle(phase);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T inject(T object) throws MuleException {
    return registry.inject(object);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MuleContext getMuleContext() {
    return muleContext;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Transformer lookupTransformer(String name) {
    return (Transformer) registry.lookupObject(name);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Transformer lookupTransformer(DataType source, DataType result) throws TransformerException {
    //To maintain the previous behaviour, we don't want to consider the result mimeType when resolving a transformer
    //and only find transformers with a targetType the same as or a super class of the expected one.
    //The same could be done for the source but since if the source expected by the transformer is more generic that
    //the provided, it will be found.
    result = builder(result).mediaType(ANY).charset((Charset) null).build();

    final String dataTypePairHash = getDataTypeSourceResultPairHash(source, result);
    Transformer cachedTransformer = exactTransformerCache.get(dataTypePairHash);
    if (cachedTransformer != null) {
      return cachedTransformer;
    }

    Transformer trans = resolveTransformer(source, result);

    if (trans != null) {
      Transformer concurrentlyAddedTransformer = exactTransformerCache.putIfAbsent(dataTypePairHash, trans);
      if (concurrentlyAddedTransformer != null) {
        return concurrentlyAddedTransformer;
      } else {
        return trans;
      }
    } else {
      throw new TransformerException(noTransformerFoundForMessage(source, result));
    }
  }

  protected Transformer resolveTransformer(DataType source, DataType result) throws TransformerException {
    Lock readLock = transformerResolversLock.readLock();
    readLock.lock();

    try {
      for (TransformerResolver resolver : transformerResolvers) {
        try {
          Transformer trans = resolver.resolve(source, result);
          if (trans != null) {
            return trans;
          }
        } catch (ResolverException e) {
          throw new TransformerException(noTransformerFoundForMessage(source, result), e);
        }
      }
    } finally {
      readLock.unlock();
    }

    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Transformer> lookupTransformers(DataType source, DataType result) {
    //To maintain the previous behaviour, we don't want to consider the result mimeType when resolving a transformer
    //and only find transformers with a targetType the same as or a super class of the expected one.
    //The same could be done for the source but since if the source expected by the transformer is more generic that
    //the provided, it will be found.
    result = builder(result).mediaType(ANY).charset((Charset) null).build();

    final String dataTypePairHash = getDataTypeSourceResultPairHash(source, result);

    List<Transformer> results = transformerListCache.get(dataTypePairHash);
    if (results != null) {
      return results;
    }

    results = new ArrayList<>(2);

    Lock readLock = transformersLock.readLock();
    readLock.lock();
    try {
      for (Transformer transformer : transformers) {
        // The transformer must have the DiscoveryTransformer interface if we are
        // going to find it here
        if (!(transformer instanceof Converter)) {
          continue;
        }
        if (result.isCompatibleWith(transformer.getReturnDataType()) && transformer.isSourceDataTypeSupported(source)) {
          results.add(transformer);
        }
      }
    } finally {
      readLock.unlock();
    }

    List<Transformer> concurrentlyAddedTransformers = transformerListCache.putIfAbsent(dataTypePairHash, results);
    if (concurrentlyAddedTransformers != null) {
      return concurrentlyAddedTransformers;
    } else {
      return results;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FlowConstruct lookupFlowConstruct(String name) {
    return (FlowConstruct) registry.lookupObject(name);
  }

  @Override
  public boolean isSingleton(String key) {
    return registry.isSingleton(key);
  }

  private void notifyTransformerResolvers(Transformer t) {
    if (t instanceof Converter) {
      withLock(transformerResolversLock.readLock(), () ->
          transformerResolvers.forEach(resolver -> resolver.transformerChange(t)));

      transformerListCache.clear();
      exactTransformerCache.clear();

      withLock(transformersLock.writeLock(), () -> transformers.add(t));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyProcessorsAndLifecycle(Object object) throws MuleException {
    object = applyProcessors(object);
    object = applyLifecycle(object);
    return object;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyProcessors(Object object) throws MuleException {
    return muleContext.getInjector().inject(object);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyLifecycle(Object object) throws MuleException {
    return applyLifecycle(object, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object applyLifecycle(Object object, String phase) throws MuleException {
    return registry.applyLifecycle(object, phase);
  }

  @Override
  public void applyLifecycle(Object object, String startPhase, String toPhase) throws MuleException {
    registry.applyLifecycle(object, startPhase, toPhase);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T lookupObject(Class<T> type) throws RegistrationException {
    return registry.lookupObject(type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T lookupObject(String key) {
    return (T) registry.lookupObject(key);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T lookupObject(String key, boolean applyLifecycle) {
    return (T) registry.lookupObject(key, applyLifecycle);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Collection<T> lookupObjects(Class<T> type) {
    return registry.lookupObjects(type);
  }

  @Override
  public <T> Collection<T> lookupLocalObjects(Class<T> type) {
    return registry.lookupLocalObjects(type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Collection<T> lookupObjectsForLifecycle(Class<T> type) {
    return registry.lookupObjectsForLifecycle(type);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) registry.get(key);
  }

  @Override
  public <T> Map<String, T> lookupByType(Class<T> type) {
    return registry.lookupByType(type);
  }


  private String getDataTypeSourceResultPairHash(DataType source, DataType result) {
    return source.getClass().getName() + source.hashCode() + ":" + result.getClass().getName() + result.hashCode();
  }

  private class TransformerResolverComparator implements Comparator<TransformerResolver> {

    @Override
    public int compare(TransformerResolver transformerResolver, TransformerResolver transformerResolver1) {
      if (transformerResolver.getClass().equals(TypeBasedTransformerResolver.class)) {
        return 1;
      }

      if (transformerResolver1.getClass().equals(TypeBasedTransformerResolver.class)) {
        return -1;
      }
      return 0;
    }
  }
}


