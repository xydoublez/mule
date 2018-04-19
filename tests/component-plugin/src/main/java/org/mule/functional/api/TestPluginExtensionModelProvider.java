/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.functional.api;

import static org.mule.runtime.api.meta.Category.COMMUNITY;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULESOFT_VENDOR;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULE_NAME;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULE_VERSION;

import org.mule.metadata.api.ClassTypeLoader;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.OperationDeclarer;
import org.mule.runtime.core.internal.extension.CustomBuildingDefinitionProviderModelProperty;
import org.mule.runtime.extension.api.declaration.type.ExtensionsTypeLoaderFactory;
import org.mule.runtime.extension.api.loader.ExtensionLoadingContext;
import org.mule.runtime.extension.api.loader.ExtensionLoadingDelegate;

/**
 * Utility class to access the {@link ExtensionModel} definition for Mule's Runtime
 *
 * @since 4.0
 */
public final class TestPluginExtensionModelProvider implements ExtensionLoadingDelegate {

  private static final String PREFIX = "test";
  private static final String NAMESPACE = "http://www.mulesoft.org/schema/mule/test";

  @Override
  public void accept(ExtensionDeclarer extensionDeclarer, ExtensionLoadingContext extensionLoadingContext) {
    final ClassTypeLoader typeLoader = ExtensionsTypeLoaderFactory.getDefault()
        .createTypeLoader(TestPluginExtensionModelProvider.class.getClassLoader());

    extensionDeclarer = extensionDeclarer
        .named(PREFIX)
        .describedAs("Mule TEST components")
        .onVersion(MULE_VERSION)
        .fromVendor(MULESOFT_VENDOR)
        .withCategory(COMMUNITY)
        .withXmlDsl(XmlDslModel.builder()
            .setPrefix(PREFIX)
            .setNamespace(NAMESPACE)
            .setSchemaVersion(MULE_VERSION)
            .setXsdFileName("mule-tests.xsd")
            .setSchemaLocation("http://www.mulesoft.org/schema/mule/test/current/mule-test.xsd")
            .build());

    declareThrow(extensionDeclarer, typeLoader);
  }

  private void declareThrow(ExtensionDeclarer extensionDeclarer, ClassTypeLoader typeLoader) {
    OperationDeclarer setPayload = extensionDeclarer.withOperation("throw")
        .describedAs("Mock message source that links to a shared-config.");

    setPayload.withOutput().ofType(typeLoader.load(void.class));
    setPayload.withOutputAttributes().ofType(typeLoader.load(void.class));

    setPayload.onDefaultParameterGroup()
        .withRequiredParameter("exception")
        .ofType(typeLoader.load(String.class))
        .withExpressionSupport(NOT_SUPPORTED)
        .describedAs("A fully qualified classname of the exception object to throw. Must be a TypedException unless an error is provided as well.");

    setPayload.onDefaultParameterGroup()
        .withRequiredParameter("error")
        .ofType(typeLoader.load(String.class))
        .withExpressionSupport(NOT_SUPPORTED)
        .describedAs("The error to throw. If provided, the exception will be used as cause for a TypedException.");

    setPayload.onDefaultParameterGroup()
        .withOptionalParameter("count")
        .ofType(typeLoader.load(Integer.class))
        .withExpressionSupport(NOT_SUPPORTED)
        .describedAs("The number of times error should be thrown.");

  }
}
