/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.manager;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.module.extension.internal.util.IntrospectionUtils.getParameterClasses;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getClassLoader;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.registry.MuleRegistry;
import org.mule.runtime.core.transformer.simple.StringToEnum;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;
import org.mule.runtime.module.extension.internal.loader.java.property.ImplicitTransformerFactoryModelProperty;

import java.util.HashSet;
import java.util.Set;

final class ExtensionActivator {

  private final ExtensionErrorsRegistrant extensionErrorsRegistrant;
  private final MuleRegistry registry;
  private final Set<Class<? extends Enum>> enumTypes = new HashSet<>();

  ExtensionActivator(ExtensionErrorsRegistrant extensionErrorsRegistrant,
                     MuleRegistry registry) {
    this.extensionErrorsRegistrant = extensionErrorsRegistrant;
    this.registry = registry;
  }

  void activateExtension(ExtensionModel extensionModel) {
    extensionErrorsRegistrant.registerErrors(extensionModel);
    registerEnumTransformers(extensionModel);
  }

  private void registerImplicitTransformers(ExtensionModel extensionModel) {
    extensionModel.getModelProperty(ImplicitTransformerFactoryModelProperty.class).ifPresent(mp -> {
      mp.getImplicitTransformerFactory().forEach(factory -> {
        ImplicitTransformer transformer = factory.create();
        registry
      });
    });
  }

  private void registerEnumTransformers(ExtensionModel extensionModel) {
    getParameterClasses(extensionModel, getClassLoader(extensionModel)).stream()
        .filter(type -> Enum.class.isAssignableFrom(type))
        .forEach(type -> {
          final Class<Enum> enumClass = (Class<Enum>) type;
          if (enumTypes.add(enumClass)) {
            try {
              registry.registerTransformer(new StringToEnum(enumClass));
            } catch (MuleException e) {
              throw new MuleRuntimeException(createStaticMessage("Could not register transformer for enum "
                                                                     + enumClass.getName()), e);
            }
          }
        });
  }
}
