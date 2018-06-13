/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry.guice;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.mule.runtime.core.api.config.i18n.CoreMessages.transformerNotImplementDiscoverable;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.DataTypeParamsBuilder;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.core.api.transformer.DiscoverableTransformer;
import org.mule.runtime.core.api.transformer.Transformer;
import org.mule.runtime.core.internal.config.bootstrap.AbstractRegistryBootstrap;
import org.mule.runtime.core.internal.config.bootstrap.ObjectBootstrapProperty;
import org.mule.runtime.core.internal.config.bootstrap.TransformerBootstrapProperty;
import org.mule.runtime.core.privileged.registry.RegistrationException;

import java.util.function.BiConsumer;

import org.slf4j.Logger;

/**
 *
 * @since 4.2.0
 */
public class DefaultRegistryBootstrap extends AbstractRegistryBootstrap implements Initialisable {

  private static final Logger LOGGER = getLogger(DefaultRegistryBootstrap.class);
  private BiConsumer<String, Object> beanDefinitionRegister;

  /**
   * @param artifactType type of artifact. Bootstrap entries may be associated to an specific type of artifact. If it's not
   *        associated to the related artifact it will be ignored.
   * @param muleContext the {@code MuleContext} of the artifact.
   * @param beanDefinitionRegister a {@link BiConsumer} on which the bean definitions are registered
   */
  public DefaultRegistryBootstrap(ArtifactType artifactType, MuleContext muleContext,
                                  BiConsumer<String, Object> beanDefinitionRegister) {
    super(artifactType, muleContext);
    this.beanDefinitionRegister = beanDefinitionRegister;
  }

  @Override
  protected void registerTransformers() throws MuleException {
    // TODO: Should this be done once after registry creation like with Spring?

    //MuleRegistryHelper registry = (MuleRegistryHelper) ((MuleContextWithRegistry) muleContext).getRegistry();
    //Map<String, Converter> converters = registry.lookupByType(Converter.class);
    //for (Converter converter : converters.values()) {
    //  registry.notifyTransformerResolvers(converter, TransformerResolver.RegistryAction.ADDED);
    //}
  }

  @Override
  protected void doRegisterTransformer(TransformerBootstrapProperty bootstrapProperty, Class<?> returnClass,
                                       Class<? extends Transformer> transformerClass)
      throws Exception {

    String name;
    Transformer trans = (Transformer) instantiate(bootstrapProperty, transformerClass.getName()).orElse(null);

    if (trans == null) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Skipping optional bootstrap entry " + bootstrapProperty.getName());
      }
    }

    if (!(trans instanceof DiscoverableTransformer)) {
      throw new RegistrationException(transformerNotImplementDiscoverable(trans));
    }
    if (returnClass != null) {
      DataTypeParamsBuilder builder = DataType.builder().type(returnClass);
      if (isNotEmpty(bootstrapProperty.getMimeType())) {
        builder = builder.mediaType(bootstrapProperty.getMimeType());
      }
      trans.setReturnDataType(builder.build());
    }
    if (bootstrapProperty.getName() != null) {
      name = bootstrapProperty.getName();
    } else {
      // Prefixes the generated default name to ensure there is less chance of conflict if the user registers
      // the transformer with the same name
      name = "_" + trans.getName();
    }

    trans.setName(name);
    doRegisterObject(name, trans);
  }

  @Override
  protected void doRegisterObject(ObjectBootstrapProperty bootstrapProperty) throws Exception {
    doRegisterObject(bootstrapProperty.getKey(), instantiate(bootstrapProperty, bootstrapProperty.getClassName()));
  }


  private void doRegisterObject(String key, Object value) {
    beanDefinitionRegister.accept(key, value);
  }
}
