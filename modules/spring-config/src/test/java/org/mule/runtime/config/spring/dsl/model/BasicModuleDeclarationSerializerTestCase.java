/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.model;

import static java.util.Collections.singleton;
import static org.mule.runtime.api.app.declaration.fluent.ElementDeclarer.newParameterGroup;
import static org.mule.runtime.extension.api.module.fluent.ModuleDeclarer.newOperation;
import static org.mule.runtime.extension.api.module.fluent.ModuleDeclarer.newProperty;
import static org.mule.runtime.extension.api.module.fluent.ModuleOperationElementDeclarer.newParameter;
import org.mule.runtime.api.app.declaration.fluent.ElementDeclarer;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.config.spring.dsl.model.internal.DefaultModuleDeclarationXmlSerializer;
import org.mule.runtime.extension.api.module.ModuleDeclaration;
import org.mule.runtime.extension.api.module.fluent.ModuleDeclarer;
import org.mule.runtime.module.extension.internal.resources.MuleExtensionModelProvider;
import org.mule.tck.size.SmallTest;

import java.util.List;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class BasicModuleDeclarationSerializerTestCase {

  private String expectedSimpleModule;
  private DslResolvingContext resolvingContext;

  @Before
  public void setUp() throws Exception {
    expectedSimpleModule =
        IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("simple-module.xml"));
    resolvingContext = DslResolvingContext.getDefault(singleton(MuleExtensionModelProvider.getExtensionModel()));
  }

  @Test
  public void serializeModule() throws Exception {
    final ElementDeclarer core = ElementDeclarer.forExtension("Mule Core");

    ModuleDeclaration module = ModuleDeclarer.newModule("module-properties")
        .withMinMuleVersion("4.0.0")
        .withGlobalElement(newProperty("configParam").ofType("string").getDeclaration())
        .withGlobalElement(newProperty("defaultConfigParam")
            .ofType("string")
            .withDefaultValue("some default-config-value-parameter")
            .getDeclaration())
        .withGlobalElement(newProperty("optionalProperty")
            .ofType("string")
            .withUseType("OPTIONAL")
            .getDeclaration())
        .withGlobalElement(newProperty("defaultConfigPropertyUseOptional")
            .ofType("string")
            .withUseType("OPTIONAL")
            .withDefaultValue("some default-config-value-parameter")
            .getDeclaration())
        .withGlobalElement(newOperation("set-payload-hardcoded-value")
            .withComponent(core.newOperation("setPayload")
                .withParameterGroup(newParameterGroup()
                    .withParameter("value", "hardcoded value from module")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .withGlobalElement(newOperation("set-payload-param-value")
            .withParameter(newParameter("value")
                .ofType("string")
                .getDeclaration())
            .withComponent(core.newOperation("setPayload")
                .withParameterGroup(newParameterGroup()
                    .withParameter("value", "#[parameters.value ++ ' from module']")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .getDeclaration();

    DefaultModuleDeclarationXmlSerializer serializer = new DefaultModuleDeclarationXmlSerializer(resolvingContext);

    String serialized = serializer.serialize(module);
    compareXML(expectedSimpleModule, serialized);
  }

  public static void compareXML(String expected, String actual) throws Exception {
    XMLUnit.setNormalizeWhitespace(true);
    XMLUnit.setIgnoreWhitespace(true);
    XMLUnit.setIgnoreComments(true);
    XMLUnit.setIgnoreAttributeOrder(false);

    Diff diff = XMLUnit.compareXML(expected, actual);
    if (!(diff.similar() && diff.identical())) {
      System.out.println(actual);
      DetailedDiff detDiff = new DetailedDiff(diff);
      @SuppressWarnings("rawtypes")
      List differences = detDiff.getAllDifferences();
      StringBuilder diffLines = new StringBuilder();
      for (Object object : differences) {
        Difference difference = (Difference) object;
        diffLines.append(difference.toString() + '\n');
      }

      throw new IllegalArgumentException("Actual XML differs from expected: \n" + diffLines.toString());
    }
  }
}
