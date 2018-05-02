/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model.extension.xml;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Optional.empty;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.custommonkey.xmlunit.XMLUnit.setIgnoreAttributeOrder;
import static org.custommonkey.xmlunit.XMLUnit.setIgnoreComments;
import static org.custommonkey.xmlunit.XMLUnit.setIgnoreWhitespace;
import static org.custommonkey.xmlunit.XMLUnit.setNormalizeWhitespace;
import static org.mule.runtime.config.api.XmlConfigurationDocumentLoader.noValidationDocumentLoader;
import static org.mule.runtime.config.internal.dsl.model.extension.xml.ComponentModelReaderHelper.PASSWORD_MASK;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.config.api.XmlConfigurationDocumentLoader;
import org.mule.runtime.config.api.dsl.model.ResourceProviderAdapter;
import org.mule.runtime.config.api.dsl.processor.ConfigLine;
import org.mule.runtime.config.api.dsl.processor.xml.XmlApplicationParser;
import org.mule.runtime.config.internal.dsl.model.ComponentModelReader;
import org.mule.runtime.config.internal.dsl.model.config.ConfigurationPropertiesResolver;
import org.mule.runtime.config.internal.dsl.model.config.PropertiesResolverConfigurationProperties;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.internal.artifact.ast.ArtifactXmlBasedAstBuilder;

import org.w3c.dom.Document;

import com.google.common.collect.ImmutableSet;


public class ComponentModelReaderHelperTestCase {

  @Test
  public void testSimpleApp() throws Exception {
    String applicationXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n" +
        "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "      xsi:schemaLocation=\"\n" +
        "       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\">\n" +
        "\n" +
        "    <flow name=\"test\">\n" +
        "        <logger category=\"SOMETHING\" level=\"WARN\" message=\"logging info\"/>\n" +
        "    </flow>\n" +
        "\n" +
        "</mule>";
    compareXML(applicationXml, applicationXml);
  }

  @Test
  public void testAppWithCData() throws Exception {
    String applicationXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n" +
        "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "      xmlns:ee=\"http://www.mulesoft.org/schema/mule/ee/core\"\n" +
        "      xsi:schemaLocation=\"\n" +
        "       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n" +
        "       http://www.mulesoft.org/schema/mule/ee/core http://www.mulesoft.org/schema/mule/ee/core/current/mule-ee.xsd\">\n"
        +
        "\n" +
        "    <flow name=\"test\">\n" +
        "            <ee:transform>\n" +
        "                <ee:message>\n" +
        "                    <ee:set-payload><![CDATA[\n" +
        "                    %dw 2.0\n" +
        "                    output application/json encoding='UTF-8'\n" +
        "                    ---\n" +
        "                    {\n" +
        "                        'name' : 'Rick',\n" +
        "                        'lastname' : 'Sanchez'\n" +
        "                    }\n" +
        "                    ]]></ee:set-payload>\n" +
        "                </ee:message>\n" +
        "            </ee:transform>" +
        "    </flow>\n" +
        "\n" +
        "</mule>";

    compareXML(applicationXml, applicationXml);
  }

  @Test
  public void testAppWithPasswordInOperation() throws Exception {
    String format = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n" +
        "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "      xmlns:echo=\"http://www.mulesoft.org/schema/mule/module-echo\"\n" +
        "      xsi:schemaLocation=\"\n" +
        "       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n" +
        "       http://www.mulesoft.org/schema/mule/module-echo http://www.mulesoft.org/schema/mule/module-echo/current/mule-module-echo.xsd\">\n"
        +
        "\n" +
        "    <flow name=\"test\">\n" +
        "        <echo:data-type value=\"#[payload]\" password=\"%s\" />\n" +
        "    </flow>\n" +
        "\n" +
        "</mule>";
    String applicationXml = String.format(format, "THIS IS THE PASSWORD ATTRIBUTE");
    String expectedXml = String.format(format, PASSWORD_MASK);
    compareXML(applicationXml, expectedXml);
  }

  @Test
  public void testAppWithPasswordInGlobalElement() throws Exception {
    String format = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\"\n" +
        "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
        "      xmlns:echo=\"http://www.mulesoft.org/schema/mule/module-echo\"\n" +
        "      xsi:schemaLocation=\"\n" +
        "       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n" +
        "       http://www.mulesoft.org/schema/mule/module-echo http://www.mulesoft.org/schema/mule/module-echo/current/mule-module-echo.xsd\">\n"
        +
        "\n" +
        "    <echo:config name=\"echoConfig\" username=\"Rick\" password=\"%s\">\n" +
        "        <echo:connection anotherUsername=\"Morty\" password=\"%s\"/>\n" +
        "    </echo:config>\n" +
        "    <flow name=\"test\">\n" +
        "        <echo:data-type config-ref=\"echoConfig\" value=\"#[payload]\"/>\n" +
        "    </flow>" +
        "\n" +
        "</mule>";
    String applicationXml = String.format(format, "Sanchez", "Smith");
    String expectedXml = String.format(format, PASSWORD_MASK, PASSWORD_MASK);
    compareXML(applicationXml, expectedXml);
  }

  private void compareXML(String inputXml, String expectedXml) throws Exception {
    ApplicationModel applicationModel = getComponentModel(inputXml);
    //String actualXml = ComponentModelReaderHelper.toXml(componentModel); //TODO fix
    //
    //setNormalizeWhitespace(true);
    //setIgnoreWhitespace(true);
    //setIgnoreComments(false);
    //setIgnoreAttributeOrder(false);
    //
    //Diff diff = XMLUnit.compareXML(expectedXml, actualXml);
    //if (!(diff.similar() && diff.identical())) {
    //  System.out.println(actualXml);
    //  DetailedDiff detDiff = new DetailedDiff(diff);
    //  @SuppressWarnings("rawtypes")
    //  List differences = detDiff.getAllDifferences();
    //  StringBuilder diffLines = new StringBuilder();
    //  for (Object object : differences) {
    //    Difference difference = (Difference) object;
    //    diffLines.append(difference.toString() + '\n');
    //  }
    //  throw new IllegalArgumentException("Actual XML differs from expected: \n" + diffLines.toString());
    //}
  }

  private ApplicationModel getComponentModel(String applicationXml) throws Exception {
    ArtifactAst artifactAst = ArtifactXmlBasedAstBuilder.builder()
        .setConfigFiles(ImmutableSet.of(applicationXml))
        .setClassLoader(Thread.currentThread().getContextClassLoader())
        .setDisableXmlValidations(true)
        .build();
    ApplicationModel applicationModel =
        new ApplicationModel(artifactAst, null, emptySet(), emptyMap(), empty(), empty(), false,
                             new ResourceProviderAdapter(Thread.currentThread().getContextClassLoader()));
    return applicationModel;
  }
}
