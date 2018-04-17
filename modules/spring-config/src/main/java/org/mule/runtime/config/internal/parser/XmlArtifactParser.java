/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.parser;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.collection.Collectors.toImmutableList;
import static org.mule.runtime.app.declaration.internal.utils.Preconditions.checkNotNull;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.IMPORT_ELEMENT;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_NAMESPACE;
import static org.mule.runtime.internal.dsl.DslConstants.CORE_PREFIX;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mule.apache.xerces.dom.DeferredAttrNSImpl;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.artifact.sintax.ComponentDefinition;
import org.mule.runtime.api.artifact.sintax.ParameterDefinition;
import org.mule.runtime.api.artifact.sintax.ParameterIdentifierDefinition;
import org.mule.runtime.api.artifact.sintax.ParameterValueDefinition;
import org.mule.runtime.api.artifact.sintax.SourceCodeLocation;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.config.api.XmlConfigurationDocumentLoader;
import org.mule.runtime.config.api.dsl.model.ResourceProvider;
import org.mule.runtime.config.api.dsl.processor.xml.XmlApplicationParser;
import org.mule.runtime.config.api.dsl.xml.StaticXmlNamespaceInfo;
import org.mule.runtime.config.api.dsl.xml.StaticXmlNamespaceInfoProvider;
import org.mule.runtime.config.internal.dsl.model.config.RuntimeConfigurationException;
import org.mule.runtime.config.internal.parsers.XmlMetadataAnnotations;
import org.mule.runtime.core.api.config.ConfigResource;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.dsl.api.xml.XmlNamespaceInfo;
import org.mule.runtime.dsl.api.xml.XmlNamespaceInfoProvider;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;

public class XmlArtifactParser {

  private static final String COLON = ":";
  private static final Map<String, String> predefinedNamespace = new HashMap<>();
  private static final String UNDEFINED_NAMESPACE = "undefined";
  private final Cache<String, String> namespaceCache;

  private final ConfigResource[] configurationFiles;
  private final XmlConfigurationDocumentLoader xmlConfigurationDocumentLoader;
  private final Set<ExtensionModel> extensionModels;
  private final ResourceProvider resourceProvider;
  private final Function<String, String> propertyResolver;
  private final List<XmlNamespaceInfoProvider> namespaceInfoProviders;

  private ArtifactDefinition artifactDefinition;

  public XmlArtifactParser(ConfigResource[] configurationFiles,
                           XmlConfigurationDocumentLoader xmlConfigurationDocumentLoader,
                           Set<ExtensionModel> extensionModels,
                           ResourceProvider resourceProvider,
                           Function<String, String> propertyResolver) {
    checkNotNull(xmlConfigurationDocumentLoader, "array of configuration files cannot be null");
    checkNotNull(configurationFiles, "xmlConfigurationDocumentLoader cannot be null");
    this.configurationFiles = configurationFiles;
    this.xmlConfigurationDocumentLoader = xmlConfigurationDocumentLoader;
    this.extensionModels = extensionModels;
    this.resourceProvider = resourceProvider;
    this.propertyResolver = propertyResolver;
    this.namespaceCache = CacheBuilder.newBuilder().build();
    this.namespaceInfoProviders = createXmlNamespaceInfoProviders(extensionModels);
  }

  public ArtifactDefinition parse() throws IOException {
    if (artifactDefinition == null) {
      List<Pair<String, InputStream>> initialConfigFiles = new ArrayList<>();
      for (ConfigResource artifactConfigResource : configurationFiles) {
        initialConfigFiles.add(new Pair<>(artifactConfigResource.getResourceName(), artifactConfigResource.getInputStream()));
      }

      List<ComponentDefinition> globalDefinitions = recursivelyResolveConfigFiles(initialConfigFiles, emptyList(), emptyList());
      artifactDefinition = ArtifactDefinition.builder()
          .withGlobalDefinitions(globalDefinitions)
          .build();
    }
    return artifactDefinition;
  }

  private List<ComponentDefinition> recursivelyResolveConfigFiles(List<Pair<String, InputStream>> configFilesToResolve,
                                                                  List<String> alreadyResolvedConfigFiles,
                                                                  List<ComponentDefinition> otherGlobalCompoentns) {

    ImmutableList.Builder<String> resolvedConfigFilesBuilder =
        ImmutableList.<String>builder().addAll(alreadyResolvedConfigFiles);

    List<ComponentDefinition> allComponentsDefinitions = new ArrayList<>();

    configFilesToResolve.stream()
        .filter(fileNameInputStreamPair -> !alreadyResolvedConfigFiles.stream()
            .anyMatch(configFile -> configFile.equals(fileNameInputStreamPair.getFirst())))
        .forEach(fileNameInputStreamPair -> {
          Document document =
              xmlConfigurationDocumentLoader.loadDocument(extensionModels,
                                                          fileNameInputStreamPair.getFirst(),
                                                          fileNameInputStreamPair.getSecond());
          allComponentsDefinitions.addAll(parseGlobalDefinitions(document.getDocumentElement()));

          resolvedConfigFilesBuilder.add(fileNameInputStreamPair.getFirst());
          try {
            fileNameInputStreamPair.getSecond().close();
          } catch (IOException e) {
            throw new MuleRuntimeException(e);
          }
        });

    List<String> importedFiles = allComponentsDefinitions.stream()
        .filter(componentDefinition -> componentDefinition.getIdentifier().equals(ComponentIdentifier.builder()
            .namespace(CORE_NAMESPACE)
            .name(IMPORT_ELEMENT)
            .build()))
        .map(componentDefinition -> componentDefinition.getParameterDefinitions().stream()
            .filter(parameterDefinition -> parameterDefinition
                .getParameterIdentifierDefinition()
                .getComponentIdentifier()
                .equals(ComponentIdentifier.builder()
                    .name("name")
                    .namespace(CORE_NAMESPACE)
                    .build()))
            .findAny()
            .map(parameterDefinition -> {
              String rawValue = parameterDefinition.getParameterValueDefinition().getRawValue();
              return propertyResolver.apply(rawValue);
            }).orElse(null))
        .filter(importedFileValue -> importedFileValue != null)
        .filter(importedFileValue -> !alreadyResolvedConfigFiles.stream()
            .anyMatch(solvedConfigFile -> solvedConfigFile.equals(importedFileValue)))
        .collect(Collectors.toList());

    if (importedFiles.isEmpty()) {
      return allComponentsDefinitions;
    }

    List<Pair<String, InputStream>> newConfigFilesToResolved = importedFiles.stream()
        .map(importedFileName -> {
          InputStream resourceAsStream = resourceProvider.getResourceAsStream(importedFileName);
          if (resourceAsStream == null) {
            throw new RuntimeConfigurationException(createStaticMessage(format("Could not find imported resource '%s'",
                                                                               importedFileName)));
          }
          return (Pair<String, InputStream>) new Pair(importedFileName, resourceAsStream);
        }).collect(toList());

    return recursivelyResolveConfigFiles(newConfigFilesToResolved, resolvedConfigFilesBuilder.build(), ImmutableList
        .<ComponentDefinition>builder().addAll(otherGlobalCompoentns).addAll(allComponentsDefinitions).build());
  }

  /**
   * Creates an {@link XmlApplicationParser} based on the list of {@link ExtensionModel}s used by the artifact.
   * <p/>
   * The {@link XmlNamespaceInfoProvider} will be discovered based on those extensions and the one discovered using by SPI.
   *
   * @param extensionModels the {@link ExtensionModel}s of the artifact that contains the configuration.
   * @return a new {@link XmlApplicationParser}.
   */
  public static List<XmlNamespaceInfoProvider> createXmlNamespaceInfoProviders(Set<ExtensionModel> extensionModels) {
    List<XmlNamespaceInfoProvider> xmlNamespaceInfoProviders =
        ImmutableList.<XmlNamespaceInfoProvider>builder()
            .add(createStaticNamespaceInfoProviders(extensionModels))
            .addAll(discoverRuntimeXmlNamespaceInfoProvider())
            .build();
    return xmlNamespaceInfoProviders;
  }

  private static List<XmlNamespaceInfoProvider> discoverRuntimeXmlNamespaceInfoProvider() {
    ImmutableList.Builder namespaceInfoProvidersBuilder = ImmutableList.builder();
    namespaceInfoProvidersBuilder
        .addAll(new SpiServiceRegistry().lookupProviders(XmlNamespaceInfoProvider.class,
                                                         Thread.currentThread().getContextClassLoader()));
    return namespaceInfoProvidersBuilder.build();
  }

  private static XmlNamespaceInfoProvider createStaticNamespaceInfoProviders(Set<ExtensionModel> extensionModels) {
    List<XmlNamespaceInfo> extensionNamespaces = extensionModels.stream()
        .map(ext -> new StaticXmlNamespaceInfo(ext.getXmlDslModel().getNamespace(), ext.getXmlDslModel().getPrefix()))
        .collect(toImmutableList());

    return new StaticXmlNamespaceInfoProvider(extensionNamespaces);
  }

  public String getNormalizedNamespace(String namespaceUri, String namespacePrefix) {
    try {
      return namespaceCache.get(namespaceUri, () -> {
        String namespace = loadNamespaceFromProviders(namespaceUri);
        if (namespace == null) {
          namespace = namespacePrefix;
        }
        return namespace;
      });
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    }
  }

  private String loadNamespaceFromProviders(String namespaceUri) {
    if (predefinedNamespace.containsKey(namespaceUri)) {
      return predefinedNamespace.get(namespaceUri);
    }
    for (XmlNamespaceInfoProvider namespaceInfoProvider : namespaceInfoProviders) {
      Optional<XmlNamespaceInfo> matchingXmlNamespaceInfo = namespaceInfoProvider.getXmlNamespacesInfo().stream()
          .filter(xmlNamespaceInfo -> namespaceUri.equals(xmlNamespaceInfo.getNamespaceUriPrefix())).findFirst();
      if (matchingXmlNamespaceInfo.isPresent()) {
        return matchingXmlNamespaceInfo.get().getNamespace();
      }
    }
    // TODO MULE-9638 for now since just return a fake value since guava cache does not support null values. When done right throw
    // a configuration exception with a meaningful message if there's no info provider defined
    return UNDEFINED_NAMESPACE;
  }

  private List<ComponentDefinition> parseGlobalDefinitions(Element configElement) {
    List<ComponentDefinition> globalDefinitions = new ArrayList<>();
    NodeList childNodes = configElement.getChildNodes();
    if (childNodes != null) {
      for (int i = 0; i < childNodes.getLength(); i++) {
        createComponentDefinitionFromNode(childNodes.item(i)).ifPresent(globalDefinitions::add);
      }
    }
    return globalDefinitions;
  }

  private Optional<ComponentDefinition> createComponentDefinitionFromNode(Node node) {
    if (!isValidType(node)) {
      return Optional.empty();
    }

    ComponentIdentifier componentIdentifier = getComponentIdentifierFromNode(node);

    ComponentDefinition.ComponentDefinitionBuilder componentDefinitionBuilder = ComponentDefinition.builder()
        .withSourceCodeLocation(getSourceCodeLocationFromNode(node))
        .withIdentifier(componentIdentifier);


    Element element = (Element) node;
    NamedNodeMap attributes = element.getAttributes();
    if (element.hasAttributes()) {
      for (int i = 0; i < attributes.getLength(); i++) {
        Node attribute = attributes.item(i);
        Attr attributeNode = element.getAttributeNode(attribute.getNodeName());
        // TODO see what to do with this, should not be necessary. Should come form ExtModel.
        boolean isFromXsd = !attributeNode.getSpecified();
        // TODO see where to get the source code location for the attribute parts
        componentDefinitionBuilder.withParameterDefinition(ParameterDefinition.builder()
            .withSourceCodeLocation(getSourceCodeLocationFromNode(attributeNode))
            .withParameterIdentifierDefinition(ParameterIdentifierDefinition.builder()
                .withComponentIdentifier(getComponentIdentifierFromNode(attributeNode))
                .build())
            .withParameterValueDefinition(ParameterValueDefinition.builder()
                .withRawValue(attribute.getNodeValue())
                .build())
            .build());
      }
    }

    if (node.hasChildNodes()) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        if (isTextContent(child)) {
          componentDefinitionBuilder.withParameterValueDefinition(ParameterValueDefinition
              .builder()
              .withRawValue(child.getNodeValue())
              .withSourceCodeLocation(getSourceCodeLocationFromNode(child))
              .build());
          // TODO see why CDATA is relevant
          // if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
          // builder.addCustomAttribute(IS_CDATA, Boolean.TRUE);
          // break;
          // }
        } else {
          createComponentDefinitionFromNode(child).ifPresent(componentDefinitionBuilder::withChildComponentDefinition);

        }
      }
    }
    return Optional.of(componentDefinitionBuilder.build());
  }

  private ComponentIdentifier getComponentIdentifierFromNode(Node node) {
    String name = parseIdentifier(node);
    String namespace = parseNamespace(node);
    return ComponentIdentifier.builder()
        .name(name)
        .namespace(namespace)
        .build();
  }

  private SourceCodeLocation getSourceCodeLocationFromNode(Node node) {
    XmlMetadataAnnotations userData = (XmlMetadataAnnotations) node.getUserData(XmlMetadataAnnotations.METADATA_ANNOTATIONS_KEY);
    if (userData != null) {
      //TODO add support for userData when the node is an XML attributes
      SourceCodeLocation sourceCodeLocation = SourceCodeLocation.builder()
              .withStartLine(userData.getLineNumber())
              .withEndLine(userData.getEndLineNumber())
              .withStartColumn(userData.getColumnNumber())
              .withEndColumn(userData.getEndColumnNumber())
              .build();
      return sourceCodeLocation;
    }
    return null;
  }

  private String parseNamespace(Node node) {
    String namespace = CORE_PREFIX;
    if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
      //TODO remove duplicate code with below
      String namespaceURI = node.getNamespaceURI();
      if (namespaceURI == null) {
        namespaceURI = ((Attr) node).getOwnerElement().getNamespaceURI();
      }
      namespace = getNormalizedNamespace(namespaceURI, node.getPrefix());
      if (namespace.equals(UNDEFINED_NAMESPACE)) {
        namespace = node.getPrefix();
      }
    } else if (node.getNodeType() != Node.CDATA_SECTION_NODE) {
      namespace = getNormalizedNamespace(node.getNamespaceURI(), node.getPrefix());
      if (namespace.equals(UNDEFINED_NAMESPACE)) {
        namespace = node.getPrefix();
      }
    }
    return namespace;
  }

  private String parseIdentifier(Node node) {
    String identifier = node.getNodeName();
    String[] nameParts = identifier.split(COLON);
    if (nameParts.length > 1) {
      identifier = nameParts[1];
    }
    return identifier;
  }

  private boolean isValidType(Node node) {
    return node.getNodeType() != Node.TEXT_NODE && node.getNodeType() != Node.COMMENT_NODE;
  }

  private boolean isTextContent(Node node) {
    return node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE;
  }


}
