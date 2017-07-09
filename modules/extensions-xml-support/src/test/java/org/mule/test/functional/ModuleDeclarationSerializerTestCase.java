/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.functional;

import static java.util.Arrays.asList;
import static org.mule.runtime.api.app.declaration.fluent.ElementDeclarer.newObjectValue;
import static org.mule.runtime.api.app.declaration.fluent.ElementDeclarer.newParameterGroup;
import static org.mule.runtime.extension.api.module.fluent.ModuleDeclarer.newOperation;
import static org.mule.runtime.extension.api.module.fluent.ModuleDeclarer.newProperty;
import static org.mule.runtime.extension.api.module.fluent.ModuleOperationElementDeclarer.newParameter;
import org.mule.extensions.jms.api.connection.caching.NoCachingConfiguration;
import org.mule.runtime.api.app.declaration.fluent.ElementDeclarer;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.config.spring.dsl.api.ModuleDeclarationXmlSerializer;
import org.mule.runtime.extension.api.module.ModuleDeclaration;
import org.mule.runtime.extension.api.module.fluent.ModuleDeclarer;
import org.mule.runtime.module.extension.internal.resources.MuleExtensionModelProvider;
import org.mule.test.runner.RunnerDelegateTo;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

@RunnerDelegateTo(Parameterized.class)
public class ModuleDeclarationSerializerTestCase extends AbstractXmlExtensionMuleArtifactFunctionalTestCase {

  private String expectedModule;
  private DslResolvingContext resolvingContext;

  @Parameterized.Parameter(0)
  public String configFile;

  @Parameterized.Parameter(1)
  public ModuleDeclaration moduleDeclaration;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {"declaration/module-proxy-declaration.xml", declareSimpleProxyModule()},
        {"declaration/module-using-jms.xml", declareJmsProxyModule()},
    });
  }

  @Override
  protected String[] getModulePaths() {
    return new String[] {"modules/module-global-element.xml", "modules/module-simple.xml"};
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {};
  }

  @Before
  public void createResolvingContext() throws Exception {
    expectedModule = IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream(configFile));
    ImmutableSet<ExtensionModel> extensions = ImmutableSet.<ExtensionModel>builder()
        .addAll(muleContext.getExtensionManager().getExtensions())
        .add(MuleExtensionModelProvider.getExtensionModel()).build();
    resolvingContext = DslResolvingContext.getDefault(extensions);
  }

  @Test
  public void serializeModule() throws Exception {
    ModuleDeclarationXmlSerializer serializer = ModuleDeclarationXmlSerializer.getDefault(resolvingContext);

    String serialized = serializer.serialize(moduleDeclaration);
    compareXML(expectedModule, serialized);
  }

  private static ModuleDeclaration declareJmsProxyModule() {
    final ElementDeclarer jms = ElementDeclarer.forExtension("JMS");

    return ModuleDeclarer.newModule("module-using-jms")
        .withMinMuleVersion("4.0.0")
        .withGlobalElement(jms.newConfiguration("config")
            .withRefName("jmsConfig")
            .withConnection(jms.newConnection("active-mq")
                .withParameterGroup(newParameterGroup()
                    .withParameter("cachingStrategy",
                                   newObjectValue()
                                       .ofType(
                                               NoCachingConfiguration.class.getName())
                                       .build())
                    .getDeclaration())
                .getDeclaration())
            .withParameterGroup(newParameterGroup("Producer Config")
                .withParameter("disableMessageId", "true")
                .getDeclaration())
            .getDeclaration())
        .withGlobalElement(newOperation("do-publish")
            .withParameter(newParameter("destination").ofType("string").getDeclaration())
            .withParameter(newParameter("content").ofType("string").getDeclaration())
            .withComponent(jms.newOperation("publish")
                .withConfig("jmsConfig")
                .withParameterGroup(newParameterGroup()
                    .withParameter("destination", "#[parameters.destination]")
                    .getDeclaration())
                .withParameterGroup(newParameterGroup("Message")
                    .withParameter("body", "#[parameters.content]")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("void")
            .getDeclaration())
        .withGlobalElement(newOperation("do-consume")
            .withParameter(newParameter("destination").ofType("string").getDeclaration())
            .withComponent(jms.newOperation("consume")
                .withConfig("jmsConfig")
                .withParameterGroup(newParameterGroup()
                    .withParameter("destination", "#[parameters.destination]")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .getDeclaration();
  }


  private static ModuleDeclaration declareSimpleProxyModule() {
    final ElementDeclarer globalModule = ElementDeclarer.forExtension("module-global-element");
    final ElementDeclarer simpleModule = ElementDeclarer.forExtension("module-simple");

    return ModuleDeclarer.newModule("module-simple-proxy")
        .withMinMuleVersion("4.0.0")
        .withGlobalElement(newProperty("someUserConfig").ofType("string").withDefaultValue("some-username").getDeclaration())
        .withGlobalElement(newProperty("somePassConfig").ofType("string").withDefaultValue("some-password").getDeclaration())
        .withGlobalElement(newProperty("port").ofType("string").getDeclaration())

        .withGlobalElement(globalModule.newConfiguration("config")
            .withRefName("proxy-config")
            .withParameterGroup(newParameterGroup()
                .withParameter("someUserConfig", "#[properties.someUserConfig]")
                .withParameter("somePassConfig", "#[properties.somePassConfig]")
                .withParameter("port", "#[properties.port]")
                .getDeclaration())
            .getDeclaration())

        .withGlobalElement(newOperation("do-login")
            .withParameter(newParameter("someUser")
                .ofType("string")
                .withDefaultValue("usernameX")
                .getDeclaration())
            .withParameter(newParameter("somePass")
                .ofType("string")
                .withDefaultValue("passwordX")
                .getDeclaration())
            .withComponent(globalModule.newOperation("do-login")
                .withConfig("proxy-config")
                .withParameterGroup(newParameterGroup()
                    .withParameter("someUser", "#[parameters.someUser]")
                    .withParameter("somePass", "#[parameters.somePass]")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("string")
            .getDeclaration())

        .withGlobalElement(newOperation("set-payload-hardcoded-value")
            .withComponent(simpleModule.newOperation("set-payload-hardcoded-value").getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .withGlobalElement(newOperation("set-payload-param-default-value")
            .withParameter(newParameter("value")
                .ofType("string")
                .withDefaultValue("15")
                .getDeclaration())
            .withComponent(simpleModule.newOperation("set-payload-param-default-value").getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .withGlobalElement(newOperation("set-payload-concat-params-values")
            .withParameter(newParameter("value1").ofType("string").getDeclaration())
            .withParameter(newParameter("value2").ofType("string").getDeclaration())
            .withComponent(simpleModule.newOperation("set-payload-concat-params-values")
                .withParameterGroup(newParameterGroup()
                    .withParameter("value1", "#[parameters.value1]")
                    .withParameter("value2", "#[parameters.value2]")
                    .getDeclaration())
                .getDeclaration())
            .withOutputType("string")
            .getDeclaration())
        .getDeclaration();
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
