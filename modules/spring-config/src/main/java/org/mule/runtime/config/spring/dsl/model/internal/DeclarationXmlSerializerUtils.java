/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.model.internal;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.join;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import org.mule.runtime.api.app.declaration.ElementDeclaration;
import org.mule.runtime.config.spring.dsl.model.DslElementModelFactory;
import org.mule.runtime.config.spring.dsl.model.XmlDslElementModelConverter;
import org.mule.runtime.core.api.util.xmlsecurity.XMLSecureFactories;

import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class DeclarationXmlSerializerUtils {

  private DeclarationXmlSerializerUtils() {}

  static String writeToString(Document doc) throws TransformerException {

    List<String> cDataElements = getCDataElements(doc.getDocumentElement());
    TransformerFactory transformerFactory = XMLSecureFactories.createDefault().getTransformerFactory();

    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, join(cDataElements, " "));
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

    DOMSource source = new DOMSource(doc);
    StringWriter writer = new StringWriter();
    transformer.transform(source, new StreamResult(writer));
    return writer.getBuffer().toString();
  }

  private static List<String> getCDataElements(Node element) {

    if (element.getChildNodes().getLength() == 1 && element.getFirstChild().getNodeType() == Node.CDATA_SECTION_NODE) {
      return singletonList(format("{%s}%s", element.getNamespaceURI(), element.getLocalName()));
    } else {
      List<String> identifiers = new LinkedList<>();
      NodeList childs = element.getChildNodes();
      IntStream.range(0, childs.getLength()).mapToObj(childs::item)
          .forEach(c -> identifiers.addAll(getCDataElements(c)));
      return identifiers;
    }
  }

  static void addIfPresent(String name, Supplier<String> supplier, Element element) {
    String value = supplier.get();
    if (!isBlank(value)) {
      element.setAttribute(name, value);
    }
  }

  static void appendChildFromDeclaration(XmlDslElementModelConverter converter, Element parent,
                                         DslElementModelFactory modelResolver,
                                         ElementDeclaration declaration) {
    modelResolver.create(declaration)
        .ifPresent(e -> parent.appendChild(converter.asXml(e)));
  }

}
