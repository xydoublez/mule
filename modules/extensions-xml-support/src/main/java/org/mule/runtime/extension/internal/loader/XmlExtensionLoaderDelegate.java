/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.extension.internal.loader;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.Thread.currentThread;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mule.metadata.api.model.MetadataFormat.JAVA;
import static org.mule.metadata.catalog.api.PrimitiveTypesTypeLoader.PRIMITIVE_TYPES;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.meta.model.display.LayoutModel.builder;
import static org.mule.runtime.api.meta.model.parameter.ParameterRole.BEHAVIOUR;
import static org.mule.runtime.config.internal.ArtifactAstHelper.getConnectionProviderAst;
import static org.mule.runtime.config.internal.dsl.model.extension.xml.MacroExpansionModuleModel.TNS_PREFIX;
import static org.mule.runtime.config.internal.dsl.model.extension.xml.MacroExpansionModulesModel.getUsedNamespaces;
import static org.mule.runtime.config.internal.model.ApplicationModel.DESCRIPTION_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.GLOBAL_PROPERTY;
import static org.mule.runtime.config.internal.model.ApplicationModel.NAME_ATTRIBUTE;
import static org.mule.runtime.core.api.exception.Errors.ComponentIdentifiers.Handleable.ANY;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULESOFT_VENDOR;
import static org.mule.runtime.core.internal.processor.chain.ModuleOperationMessageProcessorChainBuilder.MODULE_CONNECTION_GLOBAL_ELEMENT_NAME;
import static org.mule.runtime.extension.api.util.XmlModelUtils.createXmlLanguageModel;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_NAMESPACE;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.catalog.api.TypeResolver;
import org.mule.metadata.catalog.api.TypeResolverException;
import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComplexParameterValueAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.artifact.ast.ConnectionProviderAst;
import org.mule.runtime.api.artifact.ast.ConstructAst;
import org.mule.runtime.api.artifact.ast.HasParametersAst;
import org.mule.runtime.api.artifact.ast.ParameterAst;
import org.mule.runtime.api.artifact.ast.RouteAst;
import org.mule.runtime.api.artifact.ast.SimpleParameterValueAst;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.Category;
import org.mule.runtime.api.meta.NamedObject;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.connection.ConnectionManagementType;
import org.mule.runtime.api.meta.model.declaration.fluent.ConfigurationDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ConnectionProviderDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.HasOperationDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.OperationDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.OutputDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ParameterDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ParameterizedDeclarer;
import org.mule.runtime.api.meta.model.display.DisplayModel;
import org.mule.runtime.api.meta.model.display.LayoutModel;
import org.mule.runtime.api.meta.model.error.ErrorModelBuilder;
import org.mule.runtime.api.meta.model.parameter.ParameterRole;
import org.mule.runtime.config.api.dsl.model.ResourceProviderAdapter;
import org.mule.runtime.config.api.dsl.model.properties.ConfigurationPropertiesProvider;
import org.mule.runtime.config.api.dsl.model.properties.ConfigurationProperty;
import org.mule.runtime.config.api.dsl.processor.ConfigLine;
import org.mule.runtime.config.internal.ArtifactAstHelper;
import org.mule.runtime.config.internal.ComponentAstHolder;
import org.mule.runtime.config.internal.ParameterAstHolder;
import org.mule.runtime.config.internal.dsl.model.ComponentModelReader;
import org.mule.runtime.config.internal.dsl.model.config.ConfigurationPropertiesResolver;
import org.mule.runtime.config.internal.dsl.model.config.DefaultConfigurationProperty;
import org.mule.runtime.config.internal.dsl.model.config.GlobalPropertyConfigurationPropertiesProvider;
import org.mule.runtime.config.internal.dsl.model.config.PropertyNotFoundException;
import org.mule.runtime.config.internal.dsl.model.extension.xml.MacroExpansionModuleModel;
import org.mule.runtime.config.internal.dsl.model.extension.xml.MacroExpansionModulesModel;
import org.mule.runtime.config.internal.dsl.model.extension.xml.property.GlobalElementComponentModelModelProperty;
import org.mule.runtime.config.internal.dsl.model.extension.xml.property.OperationComponentModelModelProperty;
import org.mule.runtime.config.internal.dsl.model.extension.xml.property.PrivateOperationsModelProperty;
import org.mule.runtime.config.internal.dsl.model.extension.xml.property.TestConnectionGlobalElementModelProperty;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.internal.artifact.ast.ArtifactXmlBasedAstBuilder;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.dsl.syntax.resolver.DslSyntaxResolver;
import org.mule.runtime.extension.api.exception.IllegalModelDefinitionException;
import org.mule.runtime.extension.api.exception.IllegalParameterModelDefinitionException;
import org.mule.runtime.extension.api.loader.ExtensionLoadingContext;
import org.mule.runtime.extension.api.loader.xml.declaration.DeclarationOperation;
import org.mule.runtime.extension.api.property.XmlExtensionModelProperty;
import org.mule.runtime.extension.internal.loader.validator.ForbiddenConfigurationPropertiesValidator;
import org.mule.runtime.extension.internal.loader.validator.property.InvalidTestConnectionMarkerModelProperty;
import org.mule.runtime.extension.internal.property.NoReconnectionStrategyModelProperty;
import org.mule.runtime.internal.dsl.NullDslResolvingContext;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Describes an {@link ExtensionModel} by scanning an XML provided in the constructor
 *
 * @since 4.0
 */
public final class XmlExtensionLoaderDelegate {

  public static final String CYCLIC_OPERATIONS_ERROR = "Cyclic operations detected, offending ones: [%s]";

  private static final String PARAMETER_NAME = "name";
  private static final String PARAMETER_DEFAULT_VALUE = "defaultValue";
  private static final String TYPE_ATTRIBUTE = "type";
  private static final String MODULE_NAME = "name";
  private static final String MODULE_PREFIX_ATTRIBUTE = "prefix";
  private static final String MODULE_NAMESPACE_ATTRIBUTE = "namespace";
  private static final String MODULE_NAMESPACE_NAME = "module";
  protected static final String CONFIG_NAME = "config";

  private static final Map<String, ParameterRole> parameterRoleTypes = ImmutableMap.<String, ParameterRole>builder()
      .put("BEHAVIOUR", ParameterRole.BEHAVIOUR)
      .put("CONTENT", ParameterRole.CONTENT)
      .put("PRIMARY", ParameterRole.PRIMARY_CONTENT)
      .build();

  private static final String CATEGORY = "category";
  private static final String VENDOR = "vendor";
  private static final String DOC_DESCRIPTION = "doc:description";
  private static final String PASSWORD = "password";
  private static final String ORDER_ATTRIBUTE = "order";
  private static final String TAB_ATTRIBUTE = "tab";
  private static final String DISPLAY_NAME_ATTRIBUTE = "displayName";
  private static final String SUMMARY_ATTRIBUTE = "summary";
  private static final String EXAMPLE_ATTRIBUTE = "example";
  private static final String ERROR_TYPE_ATTRIBUTE = "type";
  private static final String ROLE = "role";
  private static final String ATTRIBUTE_USE = "use";
  private static final String ATTRIBUTE_VISIBILITY = "visibility";
  private static final String NAMESPACE_SEPARATOR = ":";

  private static final String TRANSFORMATION_FOR_TNS_RESOURCE = "META-INF/transform_for_tns.xsl";
  private static final String XMLNS_TNS = XMLNS_ATTRIBUTE + ":" + TNS_PREFIX;
  public static final String MODULE_CONNECTION_MARKER_ATTRIBUTE = "xmlns:connection";
  private static final String GLOBAL_ELEMENT_NAME_ATTRIBUTE = "name";

  /**
   * ENUM used to discriminate which type of {@link ParameterDeclarer} has to be created (required or not).
   *
   * @see #getParameterDeclarer(ParameterizedDeclarer, Map)
   */
  private enum UseEnum {
    REQUIRED, OPTIONAL, AUTO
  }

  /**
   * ENUM used to discriminate which visibility an <operation/> has.
   * 
   * @see {@link XmlExtensionLoaderDelegate#loadOperationsFrom(HasOperationDeclarer, ComponentModel, DirectedGraph, XmlDslModel, XmlExtensionLoaderDelegate.OperationVisibility)}
   */
  private enum OperationVisibility {
    PRIVATE, PUBLIC
  }

  private static ParameterRole getRole(final String role) {
    if (!parameterRoleTypes.containsKey(role)) {
      throw new IllegalParameterModelDefinitionException(format("The parametrized role [%s] doesn't match any of the expected types [%s]",
                                                                role, join(", ", parameterRoleTypes.keySet())));
    }
    return parameterRoleTypes.get(role);
  }

  private static final ComponentIdentifier OPERATION_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("operation").build();
  private static final ComponentIdentifier PROPERTY_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("property").build();
  private static final ComponentIdentifier NAME_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("name").build();
  private static final ComponentIdentifier TYPE_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(TYPE_ATTRIBUTE).build();
  private static final ComponentIdentifier VISIBILITY_ATTRIBUTE_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(ATTRIBUTE_VISIBILITY).build();
  private static final ComponentIdentifier PASSWORD_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(PASSWORD).build();
  private static final ComponentIdentifier ORDER_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(ORDER_ATTRIBUTE).build();
  private static final ComponentIdentifier TAB_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(TAB_ATTRIBUTE).build();
  private static final ComponentIdentifier DISPLAY_NAME_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(DISPLAY_NAME_ATTRIBUTE).build();
  private static final ComponentIdentifier SUMMARY_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(SUMMARY_ATTRIBUTE).build();
  private static final ComponentIdentifier EXAPLE_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(EXAMPLE_ATTRIBUTE).build();
  private static final ComponentIdentifier DEFAULT_VALUE_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(PARAMETER_DEFAULT_VALUE).build();
  private static final ComponentIdentifier ATTRIBUTE_USE_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(ATTRIBUTE_USE).build();
  private static final ComponentIdentifier MODULE_CONNECTION_MARKER_ATTRIBUTE_IDENTIFIER =
      ComponentIdentifier.builder().namespace("xmlns").name("connection").build();
  private static final ComponentIdentifier CONNECTION_PROPERTIES_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("connection").build();
  private static final ComponentIdentifier OPERATION_PARAMETERS_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("parameters").build();
  private static final ComponentIdentifier OPERATION_PARAMETER_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("parameter").build();
  private static final ComponentIdentifier OPERATION_BODY_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("body").build();
  private static final ComponentIdentifier OPERATION_OUTPUT_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("output").build();
  private static final ComponentIdentifier OPERATION_OUTPUT_ATTRIBUTES_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("outputAttributes").build();
  private static final ComponentIdentifier OPERATION_ERRORS_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("errors").build();
  private static final ComponentIdentifier OPERATION_ERROR_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name("error").build();
  private static final ComponentIdentifier MODULE_IDENTIFIER =
      ComponentIdentifier.builder().namespace(CORE_PREFIX).name(MODULE_NAMESPACE_NAME)
          .build();
  public static final String XSD_SUFFIX = ".xsd";
  private static final String XML_SUFFIX = ".xml";
  private static final String TYPES_XML_SUFFIX = "-catalog" + XML_SUFFIX;

  private final String modulePath;
  private final boolean validateXml;
  private final Optional<String> declarationPath;
  private final List<String> resourcesPaths;
  private TypeResolver typeResolver;
  private Map<String, DeclarationOperation> declarationMap;

  /**
   * @param modulePath relative path to a file that will be loaded from the current {@link ClassLoader}. Non null.
   * @param validateXml true if the XML of the Smart Connector must ve valid, false otherwise. It will be false at runtime,
   *                    as the packaging of a connector will previously validate it's XML.
   * @param declarationPath relative path to a file that contains the {@link MetadataType}s of all <operations/>.
   * @param resourcesPaths set of resources that will be exported in the {@link ExtensionModel}
   */
  public XmlExtensionLoaderDelegate(String modulePath, boolean validateXml, Optional<String> declarationPath,
                                    List<String> resourcesPaths) {
    checkArgument(!isEmpty(modulePath), "modulePath must not be empty");
    this.modulePath = modulePath;
    this.validateXml = validateXml;
    this.declarationPath = declarationPath;
    this.resourcesPaths = resourcesPaths;
  }

  public void declare(ExtensionLoadingContext context) {
    // We will assume the context classLoader of the current thread will be the one defined for the plugin (which is not filtered
    // and will allow us to access any resource in it
    URL resource = getResource(modulePath);
    if (resource == null) {
      throw new IllegalArgumentException(format("There's no reachable XML in the path '%s'", modulePath));
    }
    try {
      loadCustomTypes();
    } catch (Exception e) {
      throw new IllegalArgumentException(format("The custom type file [%s] for the module '%s' cannot be read properly",
                                                getCustomTypeFilename(), modulePath),
                                         e);
    }
    loadDeclaration();
    ArtifactAst artifactAst = getModuleAst(context, resource);
    loadModuleExtension(context.getExtensionDeclarer(), artifactAst,
                        context.getDslResolvingContext().getExtensions(), false);
  }

  private URL getResource(String resource) {
    return currentThread().getContextClassLoader().getResource(resource);
  }

  /**
   * Custom types might not exist for the current module, that's why it's handled with {@link #getEmptyTypeResolver()}.
   */
  private void loadCustomTypes() {
    final String customTypes = getCustomTypeFilename();
    final URL resourceCustomType = getResource(customTypes);
    if (resourceCustomType != null) {
      typeResolver = TypeResolver.createFrom(resourceCustomType, currentThread().getContextClassLoader());
    } else {
      typeResolver = getEmptyTypeResolver();
    }
  }

  private TypeResolver getEmptyTypeResolver() {
    return TypeResolver.create(currentThread().getContextClassLoader());
  }

  /**
   * Possible file with the custom types, works by convention.
   *
   * @return given a {@code modulePath} such as "module-custom-types.xml" returns "module-custom-types-types.xml". Not null
   */
  private String getCustomTypeFilename() {
    return modulePath.replace(XML_SUFFIX, TYPES_XML_SUFFIX);
  }

  /**
   * If a declaration file does exists, then it reads into a map to use it later on when describing the {@link ExtensionModel} of
   * the current <module/> for ever <operation/>s output and output attributes.
   *
   */
  private void loadDeclaration() {
    declarationMap = new HashMap<>();
    declarationPath.ifPresent(operationsOutputPathValue -> {
      final URL operationsOutputResource = getResource(operationsOutputPathValue);
      if (operationsOutputResource != null) {
        try {
          declarationMap = DeclarationOperation.fromString(IOUtils.toString(operationsOutputResource));
        } catch (IOException e) {
          throw new IllegalArgumentException(format("The declarations file [%s] for the module '%s' cannot be read properly",
                                                    operationsOutputPathValue, modulePath),
                                             e);
        }
      }
    });
  }

  private ArtifactAst getModuleAst(ExtensionLoadingContext context, URL resource) {
    try {
      final Set<ExtensionModel> extensions = new HashSet<>(context.getDslResolvingContext().getExtensions());
      return ArtifactXmlBasedAstBuilder.builder().setExtensionModels(extensions)
          .setDisableXmlValidations(!validateXml)
          .setClassLoader(Thread.currentThread().getContextClassLoader())
          .setConfigFiles(ImmutableSet.of(FileUtils.toFile(resource).getAbsolutePath())) // TODO this is weird
          .build();
      // createTnsExtensionModel(resource, extensions).ifPresent(extensions::add); //TODO missing
      // return xmlConfigurationDocumentLoader.loadDocument(extensions, resource.getFile(), resource.openStream());
    } catch (Exception e) {
      throw new MuleRuntimeException(
                                     createStaticMessage(format("There was an issue reading the stream for the resource %s",
                                                                resource.getFile())),
                                     e);
    }
  }

  /**
   * Transforms the current <module/> by stripping out the <body/>'s content, so that there are not parsing errors, to generate a
   * simpler {@link ExtensionModel} if there are references to the TNS prefix defined by the {@link #XMLNS_TNS}.
   *
   * @param resource <module/>'s resource
   * @param extensions complete list of extensions the current module depends on
   * @return an {@link ExtensionModel} if there's a {@link #XMLNS_TNS} defined, {@link Optional#empty()} otherwise
   * @throws IOException if it fails reading the resource
   */
  private Optional<ExtensionModel> createTnsExtensionModel(URL resource, Set<ExtensionModel> extensions) throws IOException {
    // TODO review this code
    // ExtensionModel result = null;
    // final ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
    // try (InputStream in = getClass().getClassLoader().getResourceAsStream(TRANSFORMATION_FOR_TNS_RESOURCE)) {
    // final Source xslt = new StreamSource(in);
    // final Source moduleToTransform = new StreamSource(resource.openStream());
    // TransformerFactory.newInstance()
    // .newTransformer(xslt)
    // .transform(moduleToTransform, new StreamResult(resultStream));
    // } catch (TransformerException e) {
    // throw new MuleRuntimeException(
    // createStaticMessage(format("There was an issue transforming the stream for the resource %s while trying to remove the
    // content of the <body> element to generate an XSD",
    // resource.getFile())),
    // e);
    // }
    // final Document transformedModuleDocument = schemaValidatingDocumentLoader(NoOpXmlErrorHandler::new)
    // .loadDocument(extensions, resource.getFile(), new ByteArrayInputStream(resultStream.toByteArray()));
    ArtifactAst artifactAst = ArtifactXmlBasedAstBuilder.builder()
        .setClassLoader(Thread.currentThread().getContextClassLoader())
        .setExtensionModels(extensions)
        .setConfigFiles(ImmutableSet.of(FileUtils.toFile(resource).getAbsolutePath()))
        .build();
    // if (StringUtils.isNotBlank(transformedModuleDocument.getDocumentElement().getAttribute(XMLNS_TNS))) {
    final ExtensionDeclarer extensionDeclarer = new ExtensionDeclarer();
    loadModuleExtension(extensionDeclarer, artifactAst, extensions, true);
    ExtensionModel result = createExtensionModel(extensionDeclarer);
    // }
    return Optional.ofNullable(result);
  }

  private ApplicationModel getModuleComponentModel(ArtifactAst artifactAst, Set<ExtensionModel> extensions) {
    try {
      return new ApplicationModel(artifactAst, null, extensions, Collections.emptyMap(), empty(), empty(), true,
                                  new ResourceProviderAdapter(Thread.currentThread().getContextClassLoader()));
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    }
  }

  private ConfigurationPropertiesProvider createProviderFromGlobalProperties(ConfigLine moduleLine, String modulePath) {
    final Map<String, ConfigurationProperty> globalProperties = new HashMap<>();
    moduleLine.getChildren().stream()
        .filter(configLine -> GLOBAL_PROPERTY.equals(configLine.getIdentifier()))
        .forEach(configLine -> {
          final String key = configLine.getConfigAttributes().get("name").getValue();
          final String rawValue = configLine.getConfigAttributes().get("value").getValue();
          globalProperties.put(key,
                               new DefaultConfigurationProperty(format("global-property - file: %s - lineNumber %s",
                                                                       modulePath, configLine.getLineNumber()),
                                                                key, rawValue));

        });
    return new GlobalPropertyConfigurationPropertiesProvider(globalProperties);
  }

  private void loadModuleExtension(ExtensionDeclarer declarer, ArtifactAst artifactAst,
                                   Set<ExtensionModel> extensions, boolean comesFromTNS) {
    if (!artifactAst.getArtifactType().equals(MODULE_NAMESPACE_NAME)) { // TODO add data to do validation
      throw new MuleRuntimeException(createStaticMessage(format("The root element of a module must be '%s', but found '%s'",
                                                                MODULE_IDENTIFIER.toString(),
                                                                artifactAst.getArtifactType())));
    }
    ArtifactAstHelper artifactAstHelper = new ArtifactAstHelper(artifactAst);
    final String name =
        artifactAstHelper.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX)
            .name(NAME_ATTRIBUTE).build()).get().getSimpleParameterValueAst().getRawValue();
    final String version = "4.0.0"; // TODO(fernandezlautaro): MULE-11010 remove version from ExtensionModel
    final String category =
        artifactAstHelper.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX)
            .name(CATEGORY).build()).map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue())
            .orElse(Category.COMMUNITY.name());
    final String vendor =
        artifactAstHelper.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX)
            .name(VENDOR).build()).map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue())
            .orElse(MULESOFT_VENDOR);
    final XmlDslModel xmlDslModel = getXmlDslModel(artifactAstHelper, name, version);
    final String description = getDescription(artifactAst);
    final String xmlnsTnsValue = artifactAstHelper.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX)
        .name(XMLNS_TNS).build()).map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue())
        .orElse(null);
    if (xmlnsTnsValue != null && !xmlDslModel.getNamespace().equals(xmlnsTnsValue)) {
      throw new MuleRuntimeException(createStaticMessage(format("The %s attribute value of the module must be '%s', but found '%s'",
                                                                XMLNS_TNS,
                                                                xmlDslModel.getNamespace(),
                                                                xmlnsTnsValue)));
    }
    resourcesPaths.stream().forEach(declarer::withResource);

    fillDeclarer(declarer, name, version, category, vendor, xmlDslModel, description);
    declarer.withModelProperty(getXmlExtensionModelProperty(artifactAstHelper, xmlDslModel));

    DirectedGraph<String, DefaultEdge> directedGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
    // loading public operations
    final List<ComponentAst> globalElementsComponentModel = extractGlobalElementsFrom(artifactAstHelper);
    addGlobalElementModelProperty(declarer, globalElementsComponentModel);
    final Optional<ConfigurationDeclarer> configurationDeclarer =
        loadPropertiesFrom(declarer, artifactAstHelper, extensions);

    final HasOperationDeclarer hasOperationDeclarer = configurationDeclarer.isPresent() ? configurationDeclarer.get() : declarer;
    // loading private operations
    if (comesFromTNS) {
      // when parsing for the TNS, we need the <operation/>s to be part of the extension model to validate the XML properly
      loadOperationsFrom(hasOperationDeclarer, artifactAstHelper, directedGraph, xmlDslModel, OperationVisibility.PRIVATE);
    } else {
      // when parsing for the macro expansion, the <operation/>s will be left in the PrivateOperationsModelProperty model property
      final ExtensionDeclarer temporalDeclarer = new ExtensionDeclarer();
      fillDeclarer(temporalDeclarer, name, version, category, vendor, xmlDslModel, description);
      loadOperationsFrom(temporalDeclarer, artifactAstHelper, directedGraph, xmlDslModel, OperationVisibility.PRIVATE);
      final ExtensionModel result = createExtensionModel(temporalDeclarer);
      declarer.withModelProperty(new PrivateOperationsModelProperty(result.getOperationModels()));
    }

    final CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(directedGraph);
    final Set<String> cycles = cycleDetector.findCycles();
    if (!cycles.isEmpty()) {
      throw new MuleRuntimeException(createStaticMessage(format(CYCLIC_OPERATIONS_ERROR, new TreeSet(cycles))));
    }
  }

  private ExtensionModel createExtensionModel(ExtensionDeclarer declarer) {
    return new ExtensionModelFactory()
        .create(new DefaultExtensionLoadingContext(declarer, currentThread().getContextClassLoader(),
                                                   new NullDslResolvingContext()));
  }

  private void fillDeclarer(ExtensionDeclarer declarer, String name, String version, String category, String vendor,
                            XmlDslModel xmlDslModel, String description) {
    declarer.named(name)
        .describedAs(description)
        .fromVendor(vendor)
        .onVersion(version)
        .withCategory(Category.valueOf(category.toUpperCase()))
        .withXmlDsl(xmlDslModel);
  }

  /**
   * Calculates all the used namespaces of the given <module/> leaving behind the (possible) cyclic reference if there are
   * {@link MacroExpansionModuleModel#TNS_PREFIX} references by removing the current namespace generation.
   *
   * @param moduleModel XML of the <module/>
   * @param xmlDslModel the {@link XmlDslModel} for the current {@link ExtensionModel} generation
   * @return a {@link XmlExtensionModelProperty} which contains all the namespaces dependencies. Among them could be dependencies
   *         that must be macro expanded and others which might not, but that job is left for the
   *         {@link MacroExpansionModulesModel#getDirectExpandableNamespaceDependencies(ComponentModel, Set)}
   */
  private XmlExtensionModelProperty getXmlExtensionModelProperty(ArtifactAstHelper artifactAstHelper,
                                                                 XmlDslModel xmlDslModel) {
    final Set<String> namespaceDependencies = getUsedNamespaces(artifactAstHelper).stream()
        .filter(namespace -> !xmlDslModel.getNamespace().equals(namespace))
        .collect(Collectors.toSet());
    return new XmlExtensionModelProperty(namespaceDependencies);
  }

  private XmlDslModel getXmlDslModel(ArtifactAstHelper artifactAstHelper, String name, String version) {
    final Optional<String> prefix = artifactAstHelper
        .getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(MODULE_PREFIX_ATTRIBUTE).build())
        .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue());
    final Optional<String> namespace = artifactAstHelper.getParameterAstHolder(ComponentIdentifier.builder()
        .namespace(CORE_PREFIX).name(MODULE_NAMESPACE_ATTRIBUTE).build())
        .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue());
    return createXmlLanguageModel(prefix, namespace, name, version);
  }

  private String getDescription(HasParametersAst hasParametersAst) {
    return hasParametersAst
        .getParameter(ComponentIdentifier.builder().namespace(CORE_PREFIX).name(DOC_DESCRIPTION).build())
        .map(parameterAst -> new ParameterAstHolder(parameterAst).getSimpleParameterValueAst().getRawValue()).orElse("");
  }

  private List<ComponentAst> extractGlobalElementsFrom(ArtifactAstHelper artifactAstHelper) {
    final Set<ComponentIdentifier> NOT_GLOBAL_ELEMENT_IDENTIFIERS = Sets
        .newHashSet(PROPERTY_IDENTIFIER, CONNECTION_PROPERTIES_IDENTIFIER, OPERATION_IDENTIFIER);
    return artifactAstHelper.getArtifactAst().getGlobalComponents()
        .stream()
        .filter(globalComponent -> !NOT_GLOBAL_ELEMENT_IDENTIFIERS.contains(globalComponent.getComponentIdentifier()))
        .collect(Collectors.toList());
  }

  private Optional<ConfigurationDeclarer> loadPropertiesFrom(ExtensionDeclarer declarer, ArtifactAstHelper artifactAstHelper,
                                                             Set<ExtensionModel> extensions) {
    List<ComponentAst> globalElementsComponentModel = extractGlobalElementsFrom(artifactAstHelper);
    List<ComponentAst> configurationProperties = extractModuleProperties(artifactAstHelper);
    List<ComponentAst> connectionProperties = extractConnectionProperties(artifactAstHelper);
    validateProperties(configurationProperties, connectionProperties);

    if (!configurationProperties.isEmpty() || !connectionProperties.isEmpty()) {
      declarer.withModelProperty(new NoReconnectionStrategyModelProperty());
      ConfigurationDeclarer configurationDeclarer = declarer.withConfig(CONFIG_NAME);
      configurationProperties.forEach(param -> extractProperty(configurationDeclarer, param));
      addConnectionProvider(configurationDeclarer, connectionProperties, globalElementsComponentModel);
      return of(configurationDeclarer);
    }
    return empty();
  }

  private void addGlobalElementModelProperty(ExtensionDeclarer declarer, List<ComponentAst> globalComponentsAst) {
    if (!globalComponentsAst.isEmpty()) {
      declarer.withModelProperty(new GlobalElementComponentModelModelProperty(globalComponentsAst));
    }
  }

  private List<ComponentAst> extractModuleProperties(ArtifactAstHelper artifactAstHelper) {
    List<ComponentAst> moduleProperties = new ArrayList<>();
    artifactAstHelper.executeOnGlobalComponents(componentAstHolder -> {
      if (componentAstHolder.getComponentAst().getComponentIdentifier().equals(PROPERTY_IDENTIFIER)) {
        moduleProperties.add(componentAstHolder.getComponentAst());
      }
    });
    return moduleProperties;
  }

  private List<ComponentAst> extractModuleProperties(ComponentAstHolder componentAstHolder) {
    return componentAstHolder.getParameters().stream()
        .filter(parameter -> parameter.isComplexParameter()
            || parameter.getParameterAst().getParameterIdentifier().getIdentifier().equals(PROPERTY_IDENTIFIER))
        .map(parameter -> parameter.getComplexParameterValueAst().getComponent())
        .collect(Collectors.toList());
  }

  private List<ComponentAst> extractConnectionProperties(ArtifactAstHelper artifactAstHelper) {
    final List<ComponentAst> connectionsComponentAst = artifactAstHelper.getArtifactAst().getGlobalComponents().stream()
        .filter(componentAst -> componentAst.getComponentIdentifier().equals(CONNECTION_PROPERTIES_IDENTIFIER))
        .collect(Collectors.toList());
    if (connectionsComponentAst.size() > 1) {
      throw new MuleRuntimeException(createStaticMessage(format("There cannot be more than 1 child [%s] element per [%s], found [%d]",
                                                                CONNECTION_PROPERTIES_IDENTIFIER.getName(),
                                                                MODULE_IDENTIFIER.getName(),
                                                                connectionsComponentAst.size())));
    }
    return connectionsComponentAst.isEmpty() ? Collections.EMPTY_LIST
        : extractModuleProperties(new ComponentAstHolder(connectionsComponentAst.get(0))); // TODO review this new
  }

  /**
   * Throws exception if a <property/> for a configuration or connection have the same name.
   *
   * @param configurationProperties properties that will go in the configuration
   * @param connectionProperties properties that will go in the connection
   */
  private void validateProperties(List<ComponentAst> configurationProperties, List<ComponentAst> connectionProperties) {
    final List<String> connectionPropertiesNames =
        connectionProperties.stream().map(componentAst -> componentAst.getParameter(NAME_ATTRIBUTE_IDENTIFIER)
            .map(parameterAst -> new ParameterAstHolder(parameterAst).getSimpleParameterValueAst().getRawValue())
            .orElse(null)).collect(Collectors.toList()); // TODO review of new ParameterAsTholder
    List<String> intersectedProperties = configurationProperties.stream()
        .map(componentAst -> componentAst.getParameter(NAME_ATTRIBUTE_IDENTIFIER)
            .map(parameterAst -> new ParameterAstHolder(parameterAst).getSimpleParameterValueAst().getRawValue())
            .filter(connectionPropertiesNames::contains)
            .orElse(null))
        .collect(Collectors.toList()); // TODO review of new ParameterAsTholder
    if (!intersectedProperties.isEmpty()) {
      throw new MuleRuntimeException(createStaticMessage(format("There cannot be properties with the same name even if they are within a <connection>, repeated properties are: [%s]",
                                                                intersectedProperties.stream()
                                                                    .collect(Collectors.joining(", ")))));
    }
  }

  /**
   * Adds a connection provider if (a) there's at least one global element that has test connection or (b) there's at least one
   * <property/> that has been placed within a <connection/> wrapper in the <module/> element.
   *
   * @param configurationDeclarer declarer to add the {@link ConnectionProviderDeclarer} if applies.
   * @param connectionProperties collection of <property/>s that should be added to the {@link ConnectionProviderDeclarer}.
   * @param globalComponentsAst collection of global elements where through {@link #getTestConnectionGlobalElement(List, Set)}
   *        will look for one that supports test connectivity.
   * @param extensions used also in {@link #getTestConnectionGlobalElement(List, Set)}, through the
   *        {@link #findTestConnectionGlobalElementFrom}, as the XML of the extensions might change of the values that the
   *        {@link ExtensionModel} has (heavily relies on {@link DslSyntaxResolver#resolve(NamedObject)}).
   */
  private void addConnectionProvider(ConfigurationDeclarer configurationDeclarer,
                                     List<ComponentAst> connectionProperties,
                                     List<ComponentAst> globalComponentsAst) {
    final Optional<ComponentAst> testConnectionGlobalElementOptional =
        getTestConnectionGlobalElement(globalComponentsAst);

    if (testConnectionGlobalElementOptional.isPresent() || !connectionProperties.isEmpty()) {
      final ConnectionProviderDeclarer connectionProviderDeclarer =
          configurationDeclarer.withConnectionProvider(MODULE_CONNECTION_GLOBAL_ELEMENT_NAME);
      connectionProviderDeclarer
          .withConnectionManagementType(ConnectionManagementType.NONE);
      connectionProperties.stream().forEach(param -> extractProperty(connectionProviderDeclarer, param));

      testConnectionGlobalElementOptional.ifPresent(
                                                    testConnectionComponentAst -> {
                                                      final String testConnectionGlobalElementName =
                                                          new ComponentAstHolder(testConnectionComponentAst)
                                                              .getNameParameter().get()
                                                              .getSimpleParameterValueAst().getRawValue();
                                                      connectionProviderDeclarer
                                                          .withModelProperty(new TestConnectionGlobalElementModelProperty(testConnectionGlobalElementName));
                                                    });
    }
  }


  private Optional<ComponentAst> getTestConnectionGlobalElement(ConfigurationDeclarer configurationDeclarer, List<ComponentAst> globalComponentsAst, Set<ExtensionModel> extensions) {
    final List<ComponentAst> markedAsTestConnectionGlobalElements =
        globalComponentsAst.stream()
            .filter(globalComponentAst -> Boolean
                .parseBoolean(new ComponentAstHolder(globalComponentAst)
                    .getParameterAstHolder(MODULE_CONNECTION_MARKER_ATTRIBUTE_IDENTIFIER)
                    .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue()).orElse("false")))
            .collect(Collectors.toList());

    if (markedAsTestConnectionGlobalElements.size() > 1) {
      throw new MuleRuntimeException(createStaticMessage(format("It can only be one global element marked as test connectivity [%s] but found [%d], offended global elements are: [%s]",
                                                                MODULE_CONNECTION_MARKER_ATTRIBUTE,
                                                                markedAsTestConnectionGlobalElements.size(),
                                                                markedAsTestConnectionGlobalElements.stream().map(
                                                                                                                  componentAst -> new ParameterAstHolder(componentAst
                                                                                                                      .getParameter(NAME_ATTRIBUTE_IDENTIFIER)
                                                                                                                      .get())
                                                                                                                          .getSimpleParameterValueAst()
                                                                                                                          .getRawValue()) // TODO
                                                                    // remove
                                                                    // new
                                                                    // ComponentAstHolder
                                                                    .collect(Collectors.joining(", ")))));
    }

    Optional<ComponentAst> testConnectionGlobalElement = markedAsTestConnectionGlobalElements.stream().findFirst();
    if (!testConnectionGlobalElement.isPresent()) {
      testConnectionGlobalElement = findTestConnectionGlobalElementFrom(globalComponentsAst);
    } else {
      //validates that the MODULE_CONNECTION_MARKER_ATTRIBUTE is on a correct XML element that supports test connection
      Optional<ComponentAst> temporalTestConnectionGlobalElement =
              findTestConnectionGlobalElementFrom(Collections.singletonList(testConnectionGlobalElement.get()));
      if ((!temporalTestConnectionGlobalElement.isPresent())
          || (!temporalTestConnectionGlobalElement.get().equals(testConnectionGlobalElement.get()))) {
        ComponentAstHolder componentAstHolder = new ComponentAstHolder(testConnectionGlobalElement.get());
        configurationDeclarer.withModelProperty(new InvalidTestConnectionMarkerModelProperty(componentAstHolder.getNameParameter().get().getSimpleParameterValueAst().getRawValue()
            , componentAstHolder.getComponentAst().getComponentIdentifier().toString()));
      }
    }
    return testConnectionGlobalElement;
  }

  /**
   * Goes over all {@code globalElementsComponentModel} looking for the configuration and connection elements (parent and child),
   * where if present looks for the {@link ExtensionModel}s validating if the element is in fact a {@link ConnectionProvider}. It
   * heavily relies on the {@link DslSyntaxResolver}, as many elements in the XML do not match to the names of the model.
   *
   * @param globalComponentsAst global elements of the smart connector
   * @return a {@link ComponentModel} of the global element to do test connection, empty otherwise.
   */
  private Optional<ComponentAst> findTestConnectionGlobalElementFrom(List<ComponentAst> globalComponentsAst) {


    Optional<ComponentAst> testConnectionGlobalElement;
    final Set<ComponentAst> testConnectionComponentModels = new HashSet<>();

    for (ComponentAst globalComponentAst : globalComponentsAst) {
      Optional<ConnectionProviderAst> connectionProviderAst = getConnectionProviderAst(globalComponentAst);
      if (connectionProviderAst.isPresent() && connectionProviderAst.get().getModel().supportsConnectivityTesting()) {
        testConnectionComponentModels.add(globalComponentAst);
      }
    }
    if (testConnectionComponentModels.size() > 1) {
      throw new MuleRuntimeException(createStaticMessage(format("There are [%d] global elements that can be potentially used for test connection when it should be just one. Mark any of them with the attribute [%s=\"true\"], offended global elements are: [%s]",
                                                                testConnectionComponentModels.size(),
                                                                MODULE_CONNECTION_MARKER_ATTRIBUTE,
                                                                testConnectionComponentModels.stream()
                                                                    .map(componentAst -> new ComponentAstHolder(componentAst)
                                                                        .getParameterAstHolder(NAME_ATTRIBUTE_IDENTIFIER).get()
                                                                        .getSimpleParameterValueAst().getRawValue())
                                                                    .sorted()
                                                                    .collect(Collectors.joining(", ")))));
    }
    testConnectionGlobalElement = testConnectionComponentModels.stream().findFirst();
    return testConnectionGlobalElement;
  }

  private void loadOperationsFrom(HasOperationDeclarer declarer, ArtifactAstHelper artifactAstHelper,
                                  DirectedGraph<String, DefaultEdge> directedGraph, XmlDslModel xmlDslModel,
                                  final OperationVisibility visibility) {

    // TODO Seems Operation may have it own AST component - think about it.

    artifactAstHelper.getArtifactAst().getGlobalComponents().stream()
        .filter(globalComponent -> globalComponent.getComponentIdentifier().equals(OPERATION_IDENTIFIER))
        .filter(operationComponentAst -> OperationVisibility
            .valueOf(operationComponentAst.getParameter(VISIBILITY_ATTRIBUTE_ATTRIBUTE_IDENTIFIER)
                .map(parameterAst -> ((SimpleParameterValueAst) parameterAst.getValue()).getRawValue())
                .orElse(OperationVisibility.PUBLIC.name())) == visibility)
        .forEach(operationAst -> extractOperationExtension(declarer, (ConstructAst) operationAst, directedGraph, xmlDslModel));
  }

  private void extractOperationExtension(HasOperationDeclarer declarer, ConstructAst constructAst,
                                         DirectedGraph<String, DefaultEdge> directedGraph, XmlDslModel xmlDslModel) {
    ComponentAstHolder constructAstHolder = new ComponentAstHolder(constructAst);
    String operationName =
        constructAstHolder.getParameterAstHolder(NAME_ATTRIBUTE_IDENTIFIER).get().getSimpleParameterValueAst().getRawValue();
    OperationDeclarer operationDeclarer = declarer.withOperation(operationName);
    RouteAst bodyComponentAst = constructAst.getProcessorComponents()
        .stream()
        .filter(componentAst -> componentAst.getComponentIdentifier().equals(OPERATION_BODY_IDENTIFIER)).findFirst()
        .map(componentAst -> (RouteAst) componentAst)
        .orElseThrow(() -> new IllegalArgumentException(format("The operation '%s' is missing the <body> statement",
                                                               operationName)));

    directedGraph.addVertex(operationName);
    fillGraphWithTnsReferences(directedGraph, operationName, bodyComponentAst.getProcessorComponents());

    operationDeclarer.withModelProperty(new OperationComponentModelModelProperty(constructAst, bodyComponentAst));
    operationDeclarer.describedAs(getDescription(constructAst));
    operationDeclarer.getDeclaration().setDisplayModel(getDisplayModel(constructAstHolder));
    extractOperationParameters(operationDeclarer, constructAstHolder.getComponentAst());
    extractOperationOutputType(constructAstHolder, operationDeclarer, getDeclarationOutputFor(operationName),
                               getDeclarationOutputAttributesFor(operationName));
    declareErrorModels(operationDeclarer, xmlDslModel, operationName, constructAstHolder);
  }

  private void extractOperationOutputType(ComponentAstHolder operationComponentAstHolder, OperationDeclarer operationDeclarer,
                                          Optional<MetadataType> calculatedOutputMetadataTypeOptional,
                                          Optional<MetadataType> calculatedOutputAttributesMetadataTypeOptional) {
    Optional<ParameterAstHolder> outputParameterAstHolderOptional =
        operationComponentAstHolder.getParameterAstHolder(OPERATION_OUTPUT_IDENTIFIER);
    Optional<ParameterAstHolder> outputAttributeParameterAstHolderOptional =
        operationComponentAstHolder.getParameterAstHolder(OPERATION_OUTPUT_ATTRIBUTES_IDENTIFIER);

    extractOutputType(operationDeclarer.withOutput(), calculatedOutputMetadataTypeOptional, outputParameterAstHolderOptional);
    extractOutputType(operationDeclarer.withOutputAttributes(), calculatedOutputAttributesMetadataTypeOptional,
                      outputAttributeParameterAstHolderOptional);
  }

  private void extractOutputType(OutputDeclarer outputDeclarer, Optional<MetadataType> calculatedOutputMetadataTypeOptional,
                                 Optional<ParameterAstHolder> outputParameterAstHolderOptional) {
    outputParameterAstHolderOptional.ifPresent(outputParameterAstHolder -> {
      ComplexParameterValueAst outputComplexParameterValueAst = outputParameterAstHolder.getComplexParameterValueAst();
      ComponentAstHolder componentAstHolder = new ComponentAstHolder(outputComplexParameterValueAst.getComponent());

      Optional<ParameterAstHolder> typeParameterHolderOptional =
          componentAstHolder.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("type").build());
      Optional<ParameterAstHolder> descriptionParameterHolderOptional =
          componentAstHolder.getParameterAstHolder(DESCRIPTION_IDENTIFIER);
      typeParameterHolderOptional.ifPresent(parameterAstHolder -> {
        outputDeclarer.ofType(getMetadataType(typeParameterHolderOptional, calculatedOutputMetadataTypeOptional));
        descriptionParameterHolderOptional.ifPresent(descriptionParameter -> outputDeclarer
            .describedAs(descriptionParameter.getSimpleParameterValueAst().getRawValue()));
      });
    });
  }

  private Optional<MetadataType> getDeclarationOutputFor(String operationName) {
    Optional<MetadataType> result = Optional.empty();
    if (declarationMap.containsKey(operationName)) {
      result = Optional.of(declarationMap.get(operationName).getOutput());
    }
    return result;
  }

  private Optional<MetadataType> getDeclarationOutputAttributesFor(String operationName) {
    Optional<MetadataType> result = Optional.empty();
    if (declarationMap.containsKey(operationName)) {
      result = Optional.of(declarationMap.get(operationName).getOutputAttributes());
    }
    return result;
  }

  /**
   * Goes over the {@code innerComponents} collection checking if any reference is a {@link MacroExpansionModuleModel#TNS_PREFIX},
   * in which case it adds an edge to the current vertex {@code sourceOperationVertex}
   *
   * @param directedGraph graph to contain all the vertex operations and linkage with other operations
   * @param sourceOperationVertex current vertex we are working on
   * @param innerComponentsAst collection of elements to introspect and assembly the graph with
   */
  private void fillGraphWithTnsReferences(DirectedGraph<String, DefaultEdge> directedGraph, String sourceOperationVertex,
                                          final List<ComponentAst> innerComponentsAst) {
    innerComponentsAst.forEach(innerComponentAst -> {
      if (TNS_PREFIX.equals(innerComponentAst.getComponentIdentifier().getNamespace())) {
        // we will take the current component model name, as any child of it are actually TNS child references (aka: parameters)
        final String targetOperationVertex = innerComponentAst.getComponentIdentifier().getName();
        if (!directedGraph.containsVertex(targetOperationVertex)) {
          directedGraph.addVertex(targetOperationVertex);
        }
        directedGraph.addEdge(sourceOperationVertex, targetOperationVertex);
      } else {
        // scenario for nested scopes that might be having cyclic references to operations
        if (innerComponentAst instanceof ConstructAst) {
          ConstructAst innerConstructAst = (ConstructAst) innerComponentAst;
          innerConstructAst.getProcessorComponents().stream()
              .forEach(innerConstructInnerComponent -> fillGraphWithTnsReferences(directedGraph, sourceOperationVertex,
                                                                                  innerConstructAst.getProcessorComponents()));
        }
      }
    });
  }

  private void extractOperationParameters(OperationDeclarer operationDeclarer, ComponentAst componentAst) {
    Optional<ParameterAst> optionalParametersAst = componentAst.getParameters()
        .stream()
        .filter(parameter -> parameter.getParameterIdentifier().equals(OPERATION_PARAMETERS_IDENTIFIER)).findAny();
    if (optionalParametersAst.isPresent()) {
      ComponentAst parametersComponentAst = new ParameterAstHolder(optionalParametersAst.get()).getComplexParameterValueAst()
          .getComponent();
      // parametersComponentAst //TODO see how this would work based on the extension model of the module.
      // .stream()
      // .filter(child -> child.getIdentifier().equals(OPERATION_PARAMETER_IDENTIFIER))
      // .forEach(param -> {
      // final String role = param.getParameters().get(ROLE);
      // extractParameter(operationDeclarer, param, getRole(role));
      // });
    }
  }

  private void extractProperty(ParameterizedDeclarer parameterizedDeclarer, ComponentAst param) {
    extractParameter(parameterizedDeclarer, param, BEHAVIOUR);
  }

  private void extractParameter(ParameterizedDeclarer parameterizedDeclarer, ComponentAst parameterAst, ParameterRole role) {
    ComponentAstHolder parameterAstHolder = new ComponentAstHolder(parameterAst); // TODO remove new of ComponentAstHolder
    ParameterAstHolder receivedInputTypeParameter = parameterAstHolder.getParameterAstHolder(TYPE_ATTRIBUTE_IDENTIFIER).get(); // TODO
                                                                                                                               // there
                                                                                                                               // are
                                                                                                                               // many
                                                                                                                               // toods
                                                                                                                               // about
                                                                                                                               // being
                                                                                                                               // careful
                                                                                                                               // about
                                                                                                                               // things
                                                                                                                               // but
                                                                                                                               // at
                                                                                                                               // this
                                                                                                                               // point
                                                                                                                               // the
                                                                                                                               // application
                                                                                                                               // should
                                                                                                                               // be
                                                                                                                               // well
                                                                                                                               // formed.
    final LayoutModel.LayoutModelBuilder layoutModelBuilder = builder();
    if (parseBoolean(parameterAstHolder.getParameterAstHolder(PASSWORD_ATTRIBUTE_IDENTIFIER).get().getSimpleParameterValueAst()
        .getRawValue())) {
      layoutModelBuilder.asPassword();
    }
    layoutModelBuilder.order(getOrder(parameterAstHolder.getParameterAstHolder(ORDER_ATTRIBUTE_IDENTIFIER).get()
        .getSimpleParameterValueAst().getRawValue()));
    layoutModelBuilder.tabName(getTab(parameterAstHolder.getParameterAstHolder(TAB_ATTRIBUTE_IDENTIFIER).get()
        .getSimpleParameterValueAst().getRawValue()));

    final DisplayModel displayModel = getDisplayModel(parameterAstHolder);
    MetadataType parameterType = extractType(receivedInputTypeParameter.getSimpleParameterValueAst().getRawValue());

    ParameterDeclarer parameterDeclarer = getParameterDeclarer(parameterizedDeclarer, parameterAstHolder);
    parameterDeclarer.describedAs(getDescription(parameterAst))
        .withLayout(layoutModelBuilder.build())
        .withDisplayModel(displayModel)
        .withRole(role)
        .ofType(parameterType);
  }

  private DisplayModel getDisplayModel(ComponentAstHolder componentAstHolder) {
    final DisplayModel.DisplayModelBuilder displayModelBuilder = DisplayModel.builder();
    displayModelBuilder.displayName(componentAstHolder.getParameterAstHolder(DISPLAY_NAME_ATTRIBUTE_IDENTIFIER)
        .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue()).orElse(null));
    displayModelBuilder.summary(componentAstHolder.getParameterAstHolder(SUMMARY_ATTRIBUTE_IDENTIFIER)
        .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue()).orElse(null));
    displayModelBuilder
        .example(componentAstHolder.getParameterAstHolder(EXAPLE_ATTRIBUTE_IDENTIFIER)
            .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue()).orElse(null));
    return displayModelBuilder.build();
  }

  private String getTab(String tab) {
    return StringUtils.isBlank(tab) ? Placement.DEFAULT_TAB : tab;
  }

  private int getOrder(final String order) {
    try {
      return Integer.parseInt(order);
    } catch (NumberFormatException e) {
      return Placement.DEFAULT_ORDER;
    }
  }

  /**
   * Giving a {@link ParameterDeclarer} for the parameter and the attributes in the {@code parameters}, this method will verify
   * the rules for the {@link #ATTRIBUTE_USE} where:
   * <ul>
   * <li>{@link UseEnum#REQUIRED} marks the attribute as required in the XSD, failing if leaved empty when consuming the
   * parameter/property. It can not be {@link UseEnum#REQUIRED} if the parameter/property has a {@link #PARAMETER_DEFAULT_VALUE}
   * attribute</li>
   * <li>{@link UseEnum#OPTIONAL} marks the attribute as optional in the XSD. Can be {@link UseEnum#OPTIONAL} if the
   * parameter/property has a {@link #PARAMETER_DEFAULT_VALUE} attribute</li>
   * <li>{@link UseEnum#AUTO} will default at runtime to {@link UseEnum#REQUIRED} if {@link #PARAMETER_DEFAULT_VALUE} attribute is
   * absent, otherwise it will be marked as {@link UseEnum#OPTIONAL}</li>
   * </ul>
   *
   * @param parameterizedDeclarer builder to declare the {@link ParameterDeclarer}
   * @param parameters attributes to consume the values from
   * @return the {@link ParameterDeclarer}, being created as required or optional with a default value if applies.
   */
  private ParameterDeclarer getParameterDeclarer(ParameterizedDeclarer parameterizedDeclarer,
                                                 ComponentAstHolder parameterAstHolder) {
    final String parameterName =
        parameterAstHolder.getParameterAstHolder(NAME_ATTRIBUTE_IDENTIFIER).get().getSimpleParameterValueAst().getRawValue();
    final String parameterDefaultValue = parameterAstHolder.getParameterAstHolder(DEFAULT_VALUE_ATTRIBUTE_IDENTIFIER).get()
        .getSimpleParameterValueAst().getRawValue();
    final UseEnum use = UseEnum.valueOf(parameterAstHolder.getParameterAstHolder(ATTRIBUTE_USE_ATTRIBUTE_IDENTIFIER).get()
        .getSimpleParameterValueAst().getRawValue());
    if (UseEnum.REQUIRED.equals(use) && isNotBlank(parameterDefaultValue)) {
      throw new IllegalParameterModelDefinitionException(format("The parameter [%s] cannot have the %s attribute set to %s when it has a default value",
                                                                parameterName, ATTRIBUTE_USE, UseEnum.REQUIRED));
    }
    // Is required if either is marked as REQUIRED or it's marked as AUTO an doesn't have a default value
    boolean parameterRequired = UseEnum.REQUIRED.equals(use) || (UseEnum.AUTO.equals(use) && isBlank(parameterDefaultValue));
    return parameterRequired ? parameterizedDeclarer.onDefaultParameterGroup().withRequiredParameter(parameterName)
        : parameterizedDeclarer.onDefaultParameterGroup().withOptionalParameter(parameterName)
            .defaultingTo(parameterDefaultValue);
  }

  private MetadataType getMetadataType(Optional<ParameterAstHolder> outputTypeParameterAstHolder,
                                       Optional<MetadataType> declarationMetadataType) {
    MetadataType metadataType;
    // the calculated metadata has precedence over the one configured in the xml
    if (declarationMetadataType.isPresent()) {
      metadataType = declarationMetadataType.get();
    } else {
      // if tye element is absent, it will default to the VOID type
      if (outputTypeParameterAstHolder.isPresent()) {
        String receivedOutputAttributeType = outputTypeParameterAstHolder.get().getSimpleParameterValueAst().getRawValue();
        metadataType = extractType(receivedOutputAttributeType);
      } else {
        metadataType = BaseTypeBuilder.create(JAVA).voidType().build();
      }
    }
    return metadataType;
  }

  private MetadataType extractType(String receivedType) {
    Optional<MetadataType> metadataType = empty();
    try {
      metadataType = typeResolver.resolveType(receivedType);
    } catch (TypeResolverException e) {
      if (!metadataType.isPresent()) {
        throw new IllegalParameterModelDefinitionException(format("The type obtained [%s] cannot be resolved", receivedType), e);
      }
    }
    if (!metadataType.isPresent()) {
      String errorMessage = format(
                                   "should not have reach here. Type obtained [%s] when supported default types are [%s].",
                                   receivedType,
                                   join(", ", PRIMITIVE_TYPES.keySet()));
      throw new IllegalParameterModelDefinitionException(errorMessage);
    }
    return metadataType.get();
  }

  private void declareErrorModels(OperationDeclarer operationDeclarer, XmlDslModel xmlDslModel, String operationName,
                                  ComponentAstHolder operationAstHolder) {
    Optional<ParameterAstHolder> errorsParameterAstHolderOptional =
        operationAstHolder.getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX).name("errors").build());

    errorsParameterAstHolderOptional.ifPresent(errorsParameterAstHolder -> {
      ComplexParameterValueAst complexParameterValueAst = errorsParameterAstHolder.getComplexParameterValueAst();
      ComponentAst errorComponentAst = complexParameterValueAst.getComponent();
      errorComponentAst.getParameters()
          .stream()
          .forEach(errorParameter -> {
            ComponentAstHolder errorAstHolder = new ComponentAstHolder(errorComponentAst);
            final String namespace = xmlDslModel.getPrefix().toUpperCase();
            final String typeName = errorAstHolder
                .getParameterAstHolder(ComponentIdentifier.builder().namespace(CORE_PREFIX).namespace(ERROR_TYPE_ATTRIBUTE) // TODO
                    // review
                    // namespace
                    .build())
                .map(parameterAstHolder -> parameterAstHolder.getSimpleParameterValueAst().getRawValue()).orElse(null);
            if (StringUtils.isBlank(typeName)) {
              throw new IllegalModelDefinitionException(format("The operation [%s] cannot have an <error> with an empty 'type' attribute",
                                                               operationName));
            }
            if (typeName.contains(NAMESPACE_SEPARATOR)) {
              throw new IllegalModelDefinitionException(format("The operation [%s] cannot have an <error> [%s] that contains a reserved character [%s]",
                                                               operationName, typeName,
                                                               NAMESPACE_SEPARATOR));
            }
            operationDeclarer.withErrorModel(ErrorModelBuilder.newError(typeName, namespace)
                .withParent(ErrorModelBuilder.newError(ANY).build())
                .build());
          });
    });
  }
}
