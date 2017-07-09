/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.spring.dsl.model.internal;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.config.spring.dsl.model.internal.DeclarationXmlSerializerUtils.addIfPresent;
import static org.mule.runtime.config.spring.dsl.model.internal.DeclarationXmlSerializerUtils.appendChildFromDeclaration;
import static org.mule.runtime.config.spring.dsl.model.internal.DeclarationXmlSerializerUtils.writeToString;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import static org.mule.runtime.internal.dsl.DslConstants.NAME_ATTRIBUTE_NAME;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.config.spring.dsl.api.ModuleDeclarationXmlSerializer;
import org.mule.runtime.config.spring.dsl.model.DslElementModelFactory;
import org.mule.runtime.config.spring.dsl.model.XmlDslElementModelConverter;
import org.mule.runtime.extension.api.module.ModuleConfigurationElementDeclaration;
import org.mule.runtime.extension.api.module.ModuleDeclaration;
import org.mule.runtime.extension.api.module.ModuleGlobalElementDeclarationVisitor;
import org.mule.runtime.extension.api.module.ModuleOperationElementDeclaration;
import org.mule.runtime.extension.api.module.ModuleParameterElementDeclaration;
import org.mule.runtime.extension.api.module.ModulePropertyElementDeclaration;
import org.mule.runtime.extension.api.module.ModuleTopLevelParameterElementDeclaration;

import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Default implementation of {@link ModuleDeclarationXmlSerializer}
 *
 * @since 4.0
 */
public class DefaultModuleDeclarationXmlSerializer implements ModuleDeclarationXmlSerializer {

  private static final String XMLNS_W3_URL = "http://www.w3.org/2000/xmlns/";
  private static final String XSI_W3_URL = "http://www.w3.org/2001/XMLSchema-instance";
  private static final String XSI_SCHEMA_LOCATION = "xsi:schemaLocation";
  private static final String XMLNS = "xmlns";
  public static final String MODULE_NAMESPACE = "http://www.mulesoft.org/schema/mule/module";
  public static final String MODULE_TAG = "module";
  private final DslResolvingContext context;

  public DefaultModuleDeclarationXmlSerializer(DslResolvingContext context) {
    this.context = context;
  }

  @Override
  public String serialize(ModuleDeclaration declaration) {
    return serializeModule(declaration);
  }

  @Override
  public ModuleDeclaration deserialize(InputStream configResource) {
    // checkArgument(configResource != null, "The artifact to deserialize cannot be null");
    // return XmlModuleDeclarationLoader.getDefault(context).load(configResource);
    return null;
  }

  private String serializeModule(ModuleDeclaration module) {
    checkArgument(module != null, "The module to serialize cannot be null");

    try {
      Document doc = createAppDocument(module);

      XmlDslElementModelConverter toXmlConverter = new DefaultXmlDslElementModelConverter(doc, MODULE_NAMESPACE, "module");
      DslElementModelFactory modelResolver = DslElementModelFactory.getDefault(context);

      doc.getDocumentElement().appendChild(doc.createTextNode("\n\n\t"));

      final ModuleGlobalElementDeclarationVisitor declarationVisitor =
          new ModuleGlobalElementSerializingVisitor(doc, toXmlConverter, modelResolver);

      module.getGlobalElements().forEach(declaration -> declaration.accept(declarationVisitor));
      doc.getDocumentElement().appendChild(doc.createTextNode("\n\n"));

      // write the content into xml file
      return writeToString(doc);

    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage("Failed to serialize the declaration for the module ["
          + module.getName() + "]: " + e.getMessage()), e);
    }
  }

  private Document createAppDocument(ModuleDeclaration declaration) throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder docBuilder = factory.newDocumentBuilder();

    Document doc = docBuilder.newDocument();
    Element module = doc.createElement(MODULE_TAG);
    doc.appendChild(module);

    module.setAttribute(NAME_ATTRIBUTE_NAME, declaration.getName());
    module.setAttribute("minMuleVersion", declaration.getMinMuleVersion());

    addIfPresent("namespace", declaration::getNamespace, module);
    addIfPresent("prefix", declaration::getPrefix, module);
    addIfPresent("vendor", declaration::getVendor, module);

    declaration.getCustomConfigurationParameters()
        .forEach(p -> module.setAttribute(p.getName(), p.getValue().toString()));

    if (isBlank(module.getAttribute(XSI_SCHEMA_LOCATION))) {
      module.setAttributeNS(XMLNS_W3_URL, XMLNS, MODULE_NAMESPACE);
      module.setAttributeNS(XSI_W3_URL, XSI_SCHEMA_LOCATION,
                            "http://www.mulesoft.org/schema/mule/module http://www.mulesoft.org/schema/mule/module/current/mule-module.xsd");
    }

    return doc;
  }

  private static class ModuleGlobalElementSerializingVisitor implements ModuleGlobalElementDeclarationVisitor {

    private final Document doc;
    private final XmlDslElementModelConverter toXmlConverter;
    private final DslElementModelFactory modelResolver;

    public ModuleGlobalElementSerializingVisitor(Document doc,
                                                 XmlDslElementModelConverter toXmlConverter,
                                                 DslElementModelFactory modelResolver) {
      this.doc = doc;
      this.toXmlConverter = toXmlConverter;
      this.modelResolver = modelResolver;
    }

    @Override
    public void visit(ModulePropertyElementDeclaration declaration) {
      Element property = doc.createElement("property");

      property.setAttribute(NAME_ATTRIBUTE_NAME, declaration.getName());
      property.setAttribute("type", declaration.getType());

      addIfPresent("defaultValue", declaration::getDefaultValue, property);
      addIfPresent("use", declaration::getUse, property);

      if (declaration.isPassword()) {
        property.setAttribute("password", "true");
      }

      doc.getDocumentElement().appendChild(property);
    }

    @Override
    public void visit(ModuleOperationElementDeclaration operationDeclaration) {
      doc.getDocumentElement().appendChild(doc.createTextNode("\n\n\t"));
      Element operation = doc.createElement("operation");
      operation.setAttribute(NAME_ATTRIBUTE_NAME, operationDeclaration.getName());

      if (!operationDeclaration.getParameters().isEmpty()) {
        Element parameters = doc.createElement("parameters");

        operationDeclaration.getParameters().forEach(param -> addParameter(parameters, param));
        operation.appendChild(parameters);
      }

      Element body = doc.createElement("body");
      operationDeclaration.getComponents()
          .forEach(component -> appendChildFromDeclaration(toXmlConverter, body, modelResolver, component));

      operation.appendChild(body);

      Element output = doc.createElement("output");
      output.setAttribute("type", operationDeclaration.getOutputType());
      operation.appendChild(output);

      doc.getDocumentElement().appendChild(operation);
    }

    @Override
    public void visit(ModuleConfigurationElementDeclaration config) {
      doc.getDocumentElement().appendChild(doc.createTextNode("\n\n\t"));
      appendChildFromDeclaration(toXmlConverter, doc.getDocumentElement(), modelResolver, config.getDeclaration());
    }

    @Override
    public void visit(ModuleTopLevelParameterElementDeclaration param) {
      doc.getDocumentElement().appendChild(doc.createTextNode("\n\n\t"));
      appendChildFromDeclaration(toXmlConverter, doc.getDocumentElement(), modelResolver, param.getDeclaration());
    }

    private void addParameter(Element parameters, ModuleParameterElementDeclaration paramDeclaration) {
      Element parameter = doc.createElement("parameter");
      parameter.setAttribute(NAME_ATTRIBUTE_NAME, paramDeclaration.getName());
      parameter.setAttribute("type", paramDeclaration.getType());

      addIfPresent("defaultValue", paramDeclaration::getDefaultValue, parameter);
      addIfPresent("role", paramDeclaration::getRole, parameter);
      addIfPresent("use", paramDeclaration::getUse, parameter);

      if (paramDeclaration.isPassword()) {
        parameter.setAttribute("password", "true");
      }

      parameters.appendChild(parameter);
    }
  }

}
