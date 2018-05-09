/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.extension;

import static org.mule.metadata.api.model.MetadataFormat.JAVA;
import static org.mule.runtime.api.meta.Category.COMMUNITY;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULESOFT_VENDOR;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULE_VERSION;
import static org.mule.runtime.core.api.extension.MuleModuleExtensionModelProvider.MULE_MODULE_NAME;
import static org.mule.runtime.extension.api.stereotype.MuleStereotypes.OPERATION;
import static org.mule.runtime.internal.dsl.DslConstants.MODULE_NAMESPACE;
import static org.mule.runtime.internal.dsl.DslConstants.MODULE_PREFIX;
import static org.mule.runtime.internal.dsl.DslConstants.MODULE_SCHEMA_LOCATION;
import static org.mule.runtime.internal.dsl.DslConstants.OPERATION_ELEMENT_IDENTIFIER;

import org.mule.metadata.api.builder.ArrayTypeBuilder;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.impl.DefaultStringType;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.declaration.fluent.ConstructDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.NestedRouteDeclarer;
import org.mule.runtime.core.internal.extension.CustomBuildingDefinitionProviderModelProperty;
import org.mule.runtime.extension.api.declaration.type.annotation.TypeDslAnnotation;

/**
 * An {@link ExtensionDeclarer} for Mule's Core Runtime
 *
 * @since 4.0
 */
class MuleModuleExtensionModelDeclarer {

  ExtensionDeclarer createExtensionModel() {

    ExtensionDeclarer extensionDeclarer = new ExtensionDeclarer()
        .named(MULE_MODULE_NAME)
        .describedAs("Mule Runtime and Integration Platform: Module components")
        .onVersion(MULE_VERSION)
        .fromVendor(MULESOFT_VENDOR)
        .withCategory(COMMUNITY)
        .withModelProperty(new CustomBuildingDefinitionProviderModelProperty())
        .withXmlDsl(XmlDslModel.builder()
            .setPrefix(MODULE_PREFIX)
            .setNamespace(MODULE_NAMESPACE)
            .setSchemaVersion(MULE_VERSION)
            .setXsdFileName("mule-module.xsd")
            .setSchemaLocation(MODULE_SCHEMA_LOCATION)
            .build());

    // constructs
    declareOperation(extensionDeclarer);

    // module root elements
    declareModuleConnection(extensionDeclarer);
    declareModuleProperties(extensionDeclarer);

    return extensionDeclarer;
  }

  private void declareOperation(ExtensionDeclarer extensionDeclarer) {
    ConstructDeclarer operation = extensionDeclarer.withConstruct(OPERATION_ELEMENT_IDENTIFIER)
        .allowingTopLevelDefinition()
        .withStereotype(OPERATION);

    operation.onDefaultParameterGroup().withRequiredParameter("name")
        .ofType(stringType())
        .withExpressionSupport(NOT_SUPPORTED)
        .describedAs("The operation name used in the configuration to refer to the operation within a mule application.");
    operation.onDefaultParameterGroup().withOptionalParameter("displayName")
        .describedAs("Display name of the operation. It can be any string. When empty, it will default to an auto generated one from the name attribute.")
        .withExpressionSupport(NOT_SUPPORTED)
        .ofType(stringType());
    operation.onDefaultParameterGroup().withOptionalParameter("example")
        .describedAs("An example about the content of this operation.")
        .withExpressionSupport(NOT_SUPPORTED)
        .ofType(stringType());
    operation.onDefaultParameterGroup().withOptionalParameter("visibility")
        .describedAs("Describes weather the operation can be accessible outside the module or not.")
        .withExpressionSupport(NOT_SUPPORTED)
        .ofType(BaseTypeBuilder.create(JAVA).stringType().enumOf("PUBLIC", "PRIVATE").defaultValue("PUBLIC").build());


    ObjectTypeBuilder parameterType =
        createPropertyType("A parameter element defines an input value for the operation in which it is define. Such parameter must be defined with a meaningful name, a type which defines the kind of content the parameter must have and optionally a default value that will be used if the invocation to the operation does not defines a value for the parameter. The parameter can be accessed within the body definition of the operation using an expression such as #[vars.paramName]",
                           "org.mule.runtime.module.operation.Parameter");

    parameterType.addField()
        .key("role")
        .description("Set of defined roles for a given parameter.\n" +
            "  BEHAVIOUR will render attributes;\n" +
            "  CONTENT implies support for DataWeave in place;\n" +
            "  PRIMARY works similarly to CONTENT although it also implies it will map to the payload")
        .value(BaseTypeBuilder.create(JAVA).stringType().enumOf("BEHAVIOUR", "CONTENT", "PRIMARY").defaultValue("BEHAVIOUR")
            .build())
        .required(false)
        .build();

    ArrayType parametersType = BaseTypeBuilder.create(JAVA).arrayType().of(parameterType).build();

    operation.onParameterGroup("parameters")
        .withDslInlineRepresentation(true)
        .withOptionalParameter("parameters")
        .ofType(parametersType);

    operation.onParameterGroup("output") // TODO this would case the writer of the DSL to put the output parameters after
        // parameters and not after the body
        .withDslInlineRepresentation(true)
        .withRequiredParameter("type")
        .describedAs("The output of the operation.")
        .ofType(stringType())
        .withExpressionSupport(NOT_SUPPORTED);


    operation.onParameterGroup("outputAttributes") // TODO this would case the writer of the DSL to put the output parameters
        // after parameters and not after the body
        .withDslInlineRepresentation(true)
        .withRequiredParameter("type")
        .describedAs("The output of the operation.")
        .ofType(stringType())
        .withExpressionSupport(NOT_SUPPORTED);

    ObjectTypeBuilder errorType = BaseTypeBuilder.create(JAVA).objectType()
        .description("A parameter element defines an input value for the operation in which it is define. Such parameter must be defined with a meaningful name, a type which defines the kind of content the parameter must have and optionally a default value that will be used if the invocation to the operation does not defines a value for the parameter. The parameter can be accessed within the body definition of the operation using an expression such as #[vars.paramName]")
        .id("org.mule.runtime.module.operation.Error");

    errorType.addField()
        .key("type")
        .description("Defined error for the current operation.")
        .value(BaseTypeBuilder.create(JAVA).stringType().build())
        .required(true)
        .build();

    ArrayType errorsType = BaseTypeBuilder.create(JAVA).arrayType().of(errorType).build();

    operation.onParameterGroup("errors") // TODO this would case the writer of the DSL to put the output parameters after
        // parameters and not after the body
        .withDslInlineRepresentation(true)
        .withRequiredParameter("type")
        .describedAs("The output of the operation.")
        .ofType(errorsType)
        .withExpressionSupport(NOT_SUPPORTED);

    NestedRouteDeclarer bodyRoute = operation.withRoute("body");
    bodyRoute.withMinOccurs(1).withMaxOccurs(1)
        .describedAs("The logic of the operation.")
        .withChain();
  }

  private ObjectTypeBuilder createBasePropertyParameterType() {
    ObjectTypeBuilder basePropertyType = BaseTypeBuilder.create(JAVA).objectType();

    basePropertyType.addField()
        .key("name")
        .description("The parameter name to be used for defining the parameter value when invoking the operation")
        .value(this::stringType)
        .required()
        .build();

    basePropertyType.addField()
        .key("defaultValue")
        .description("The parameter default value to be used if it's not defined explicitly by the operation invocation.")
        .value(this::stringType)
        .required(false)
        .build();

    basePropertyType.addField()
        .key("use")
        .description("Set of defined uses for a given property/parameter.\n" +
            "                REQUIRED implies the property/parameter must be present. It can not be REQUIRED if the parameter/property has a defaultValue;\n"
            +
            "                OPTIONAL implies the property/parameter could be absent.\n" +
            "                AUTO will default at runtime to REQUIRED if defaultValue is absent, otherwise it will be marked as OPTIONAL.")
        .value(BaseTypeBuilder.create(JAVA).stringType().enumOf("AUTO, REQUIRED, OPTIONAL").defaultValue("AUTO").build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("type")
        .description("The type of the parameter.")
        .value(this::stringType)
        .required(true)
        .build();

    basePropertyType.addField()
        .key("password")
        .description("Indicates if the parameter is a password.")
        .value(BaseTypeBuilder.create(JAVA).booleanType().defaultValue("false").build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("summary")
        .description("A very brief overview about this parameter.")
        .value(BaseTypeBuilder.create(JAVA).stringType().build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("example")
        .description("An example about the content of this parameter.")
        .value(BaseTypeBuilder.create(JAVA).stringType().build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("displayName")
        .description("The name to use in the IDE for this parameter.")
        .value(BaseTypeBuilder.create(JAVA).stringType().build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("order")
        .description("The order in which this parameter is going to be displayed in the IDE side.")
        .value(BaseTypeBuilder.create(JAVA).numberType().build())
        .required(false)
        .build();

    basePropertyType.addField()
        .key("tab")
        .description("The tab in which this parameter is going to be displayed in the IDE side.")
        .value(BaseTypeBuilder.create(JAVA).numberType().build())
        .required(false)
        .build();
    return basePropertyType;
  }

  private void declareModuleConnection(ExtensionDeclarer extensionDeclarer) {

    ArrayTypeBuilder connectionType = BaseTypeBuilder.create(JAVA).arrayType();

    ObjectTypeBuilder propertyType =
        createPropertyType("A property element defines an input value for the operation in which it is define. Such property must be defined with a meaningful name, a type which defines the kind of content the property must have and optionally a default value that will be used if the invocation to the operation does not defines a value for the property. The property can be accessed within the body definition of the operation using an expression such as #[mel: property.paramName]",
                           "org.mule.runtime.module.global.Property");

    connectionType.of(propertyType);

    ObjectTypeBuilder connectionObjectType = BaseTypeBuilder.create(JAVA).objectType()
        .description("A connection defines a set of properties that will be tight to the connection provider mechanism rather than the configuration (default behaviour).")
        .id("org.mule.runtime.module.global.Connection");

    connectionObjectType
        .addField()
        .key("connection")
        .required(true)
        .value(connectionType.build().getType())
        .with(new TypeDslAnnotation(false, true, "", ""))
        .build();

    extensionDeclarer.getDeclaration().addType(connectionObjectType.build());
  }

  private void declareModuleProperties(ExtensionDeclarer extensionDeclarer) {
    ConstructDeclarer constructDeclarer = extensionDeclarer.withConstruct("property")
        .allowingTopLevelDefinition();

    constructDeclarer
        .onDefaultParameterGroup()
        .withRequiredParameter("name")
        .describedAs("The parameter name to be used for defining the parameter value when invoking the operation")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("defaultValue")
        .describedAs("The parameter default value to be used if it's not defined explicitly by the operation invocation.")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("use")
        .describedAs("Set of defined uses for a given property/parameter.\n" +
            "                REQUIRED implies the property/parameter must be present. It can not be REQUIRED if the parameter/property has a defaultValue;\n"
            +
            "                OPTIONAL implies the property/parameter could be absent.\n" +
            "                AUTO will default at runtime to REQUIRED if defaultValue is absent, otherwise it will be marked as OPTIONAL.")
        .ofType(BaseTypeBuilder.create(JAVA).stringType().enumOf("AUTO, REQUIRED, OPTIONAL").defaultValue("AUTO").build());

    constructDeclarer
        .onDefaultParameterGroup()
        .withRequiredParameter("type")
        .describedAs("The type of the parameter.")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("password")
        .describedAs("Indicates if the parameter is a password.")
        .ofType(BaseTypeBuilder.create(JAVA).booleanType().defaultValue("false").build());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("summary")
        .describedAs("A very brief overview about this parameter.")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("example")
        .describedAs("An example about the content of this parameter.")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("displayName")
        .describedAs("The name to use in the IDE for this parameter.")
        .ofType(stringType());

    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("order")
        .describedAs("The order in which this parameter is going to be displayed in the IDE side.")
        .ofType(BaseTypeBuilder.create(JAVA).numberType().build());


    constructDeclarer
        .onDefaultParameterGroup()
        .withOptionalParameter("tab")
        .describedAs("The tab in which this parameter is going to be displayed in the IDE side.")
        .ofType(stringType());
  }

  private ObjectTypeBuilder createPropertyType(String description, String id) {
    return createBasePropertyParameterType()
        .description(description)
        .id(id);
  }

  private DefaultStringType stringType() {
    return BaseTypeBuilder.create(JAVA).stringType().build();
  }


}
