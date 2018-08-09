/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.dsl.model;

import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.meta.model.parameter.ParameterRole.BEHAVIOUR;
import static org.mule.runtime.app.declaration.api.fluent.ElementDeclarer.newListValue;
import static org.mule.runtime.app.declaration.api.fluent.ElementDeclarer.newParameterGroup;
import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.MULE_NAME;
import static org.mule.runtime.internal.dsl.DslConstants.FLOW_ELEMENT_IDENTIFIER;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.ParameterDslConfiguration;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.parameter.ParameterGroupModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.app.declaration.api.ArtifactDeclaration;
import org.mule.runtime.app.declaration.api.ComponentElementDeclaration;
import org.mule.runtime.app.declaration.api.ConfigurationElementDeclaration;
import org.mule.runtime.app.declaration.api.ConstructElementDeclaration;
import org.mule.runtime.app.declaration.api.ParameterElementDeclaration;
import org.mule.runtime.app.declaration.api.ParameterGroupElementDeclaration;
import org.mule.runtime.app.declaration.api.fluent.ElementDeclarer;
import org.mule.runtime.app.declaration.api.fluent.ParameterSimpleValue;
import org.mule.runtime.config.api.dsl.model.metadata.ModelBasedMetadataCacheKeyGeneratorFactory;
import org.mule.runtime.config.api.dsl.processor.ArtifactConfig;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.core.api.extension.MuleExtensionModelProvider;
import org.mule.runtime.core.internal.metadata.cache.CacheKeyPart;
import org.mule.runtime.core.internal.metadata.cache.MetadataCacheKeyGenerator;
import org.mule.runtime.extension.api.property.MetadataKeyIdModelProperty;
import org.mule.runtime.extension.api.property.MetadataKeyPartModelProperty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ModelBasedMetadataCacheKeyGeneratorTestCase extends AbstractDslModelTestCase {

  private static final String MY_FLOW = "myFlow";
  private static final String MY_CONFIG = "myConfig";
  private static final String METADATA_KEY_PART_1 = "partOne";
  private static final String METADATA_KEY_PART_2 = "partTwo";
  private static final String METADATA_KEY_PART_3 = "partThree";
  private static final String METADATA_KEY_GROUP = "Key Group";
  public static final String CATEGORY_NAME = "category";
  private static final String OPERATION_LOCATION = MY_FLOW + "/processors/0";

  private Set<ExtensionModel> extensions;
  private DslResolvingContext dslResolvingContext;
  private ElementDeclarer declarer;

  @Before
  public void setUp() throws Exception {
    extensions = ImmutableSet.<ExtensionModel>builder()
        .add(MuleExtensionModelProvider.getExtensionModel())
        .add(mockExtension)
        .build();

    dslResolvingContext = DslResolvingContext.getDefault(extensions);
    declarer = ElementDeclarer.forExtension(EXTENSION_NAME);
  }

  @Test
  public void idempotentHashCalculation() throws Exception {
    ApplicationModel applicationModel = loadApplicationModel(getBaseApp());
    Map<String, List<CacheKeyPart>> hashByLocation = new HashMap<>();

    MetadataCacheKeyGenerator<ComponentModel> generator = createGenerator(applicationModel);

    applicationModel.getRootComponentModel().getInnerComponents()
        .forEach(component -> hashByLocation.put(component.getComponentLocation().getLocation(),
                                                 generator.generateKey(component)));

    System.out.println(hashByLocation);

    ApplicationModel reload = loadApplicationModel(getBaseApp());
    MetadataCacheKeyGenerator<ComponentModel> otherGenerator = createGenerator(reload);

    reload.getRootComponentModel().getInnerComponents()
        .forEach(component -> {
          String location = component.getComponentLocation().getLocation();
          List<CacheKeyPart> previousHash = hashByLocation.get(location);
          assertThat(previousHash, is(otherGenerator.generateKey(component)));
        });
  }

  @Test
  public void valueChangeModifiesHash() throws Exception {
    ArtifactDeclaration declaration = getBaseApp();
    List<CacheKeyPart> keyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(keyParts);

    ((ConfigurationElementDeclaration) declaration.getGlobalElements().get(0)).getParameterGroups().get(0)
        .getParameter(BEHAVIOUR_NAME).get().setValue(ParameterSimpleValue.of("otherText"));

    List<CacheKeyPart> otherKeyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(otherKeyParts);
    assertThat(keyParts, not(otherKeyParts));
  }

  @Test
  public void operationParameterDoesNotModifyHash() throws Exception {
    ArtifactDeclaration declaration = getBaseApp();
    List<CacheKeyPart> keyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(keyParts);

    ComponentElementDeclaration operationDeclaration = ((ConstructElementDeclaration) declaration.getGlobalElements().get(1))
        .getComponents().get(0);
    operationDeclaration.getParameterGroups().get(0)
        .getParameter(CONTENT_NAME).get().setValue(ParameterSimpleValue.of("otherValue"));

    operationDeclaration.getParameterGroups().get(0)
        .addParameter(newParam(BEHAVIOUR_NAME, "notKey"));

    List<CacheKeyPart> otherKeyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(otherKeyParts);
    assertThat(keyParts, is(otherKeyParts));
  }

  @Test
  public void metadataKeyModifiesHash() throws Exception {
    List<CacheKeyPart> keyParts = getHash(getBaseApp(), OPERATION_LOCATION);
    System.out.println(keyParts);

    mockSimpleMetadataKeyId(operation);

    ArtifactDeclaration declaration = getBaseApp();
    ComponentElementDeclaration operationDeclaration = ((ConstructElementDeclaration) declaration.getGlobalElements().get(1))
        .getComponents().get(0);

    ParameterElementDeclaration metadataKeyPartParam = newParam(METADATA_KEY_PART_1, "User");
    operationDeclaration.getParameterGroups().get(0).addParameter(metadataKeyPartParam);

    List<CacheKeyPart> otherKeyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(otherKeyParts);

    metadataKeyPartParam.setValue(ParameterSimpleValue.of("Document"));

    List<CacheKeyPart> finalKeyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(finalKeyParts);

    assertThat(otherKeyParts, not(keyParts));
    assertThat(finalKeyParts, not(keyParts));
    assertThat(finalKeyParts, not(otherKeyParts));
  }

  @Test
  public void multiLevelMetadataKeyModifiesHash() throws Exception {
    mockMultiLevelMetadataKeyId(operation);

    ArtifactDeclaration declaration = getBaseApp();
    ComponentElementDeclaration operationDeclaration = ((ConstructElementDeclaration) declaration.getGlobalElements().get(1))
        .getComponents().get(0);

    ParameterGroupElementDeclaration keyGroup = new ParameterGroupElementDeclaration(METADATA_KEY_GROUP);
    operationDeclaration.addParameterGroup(keyGroup);
    keyGroup.addParameter(newParam(METADATA_KEY_PART_1, "localhost"));
    keyGroup.addParameter(newParam(METADATA_KEY_PART_2, "8080"));

    List<CacheKeyPart> twoLevelParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(twoLevelParts);

    keyGroup.addParameter(newParam(METADATA_KEY_PART_3, "/api"));

    List<CacheKeyPart> otherKeyParts = getHash(declaration, OPERATION_LOCATION);
    System.out.println(otherKeyParts);

    assertThat(otherKeyParts, not(twoLevelParts));
  }

  @Test
  public void multiLevelPartValueModifiesHash() throws Exception {
    mockMultiLevelMetadataKeyId(operation);

    ArtifactDeclaration declaration = getBaseApp();
    ComponentElementDeclaration operationDeclaration = ((ConstructElementDeclaration) declaration.getGlobalElements().get(1))
        .getComponents().get(0);

    ParameterGroupElementDeclaration keyGroup = new ParameterGroupElementDeclaration(METADATA_KEY_GROUP);
    operationDeclaration.addParameterGroup(keyGroup);

    keyGroup.addParameter(newParam(METADATA_KEY_PART_1, "localhost"));

    ParameterElementDeclaration partTwo = newParam(METADATA_KEY_PART_2, "8080");
    keyGroup.addParameter(partTwo);

    keyGroup.addParameter(newParam(METADATA_KEY_PART_3, "/api"));

    List<CacheKeyPart> original = getHash(declaration, OPERATION_LOCATION);
    System.out.println(original);

    partTwo.setValue(ParameterSimpleValue.of("6666"));
    List<CacheKeyPart> newHash = getHash(declaration, OPERATION_LOCATION);
    System.out.println(newHash);

    assertThat(original, not(newHash));
  }

  private List<CacheKeyPart> getHash(ArtifactDeclaration declaration, String location) throws Exception {
    ApplicationModel app = loadApplicationModel(declaration);
    ComponentModel component = new Locator(app)
        .apply(Location.builderFromStringRepresentation(location).build())
        .get();
    return createGenerator(app).generateKey(component);
  }

  private ParameterElementDeclaration newParam(String name, String value) {
    ParameterElementDeclaration param = new ParameterElementDeclaration(name);
    param.setValue(ParameterSimpleValue.of(value));
    return param;
  }

  private ArtifactDeclaration getBaseApp() {
    return ElementDeclarer.newArtifact()
        .withGlobalElement(declarer.newConfiguration(CONFIGURATION_NAME)
            .withRefName(MY_CONFIG)
            .withParameterGroup(newParameterGroup()
                .withParameter(CONTENT_NAME, CONTENT_VALUE)
                .withParameter(BEHAVIOUR_NAME, BEHAVIOUR_VALUE)
                .withParameter(LIST_NAME, newListValue().withValue(ITEM_VALUE).build())
                .getDeclaration())
            .withConnection(declarer.newConnection(CONNECTION_PROVIDER_NAME)
                .withParameterGroup(newParameterGroup()
                    .withParameter(CONTENT_NAME, CONTENT_VALUE)
                    .withParameter(BEHAVIOUR_NAME, BEHAVIOUR_VALUE)
                    .withParameter(LIST_NAME,
                                   newListValue().withValue(ITEM_VALUE).build())
                    .getDeclaration())
                .getDeclaration())
            .getDeclaration())
        .withGlobalElement(ElementDeclarer.forExtension(MULE_NAME)
            .newConstruct(FLOW_ELEMENT_IDENTIFIER)
            .withRefName(MY_FLOW)
            .withComponent(
                           declarer.newOperation(OPERATION_NAME)
                               .withConfig(MY_CONFIG)
                               .withParameterGroup(g -> g
                                   .withParameter(CONTENT_NAME, "nonKey"))
                               .getDeclaration())
            .getDeclaration())
        .getDeclaration();
  }

  protected ApplicationModel loadApplicationModel(ArtifactDeclaration declaration) throws Exception {
    return new ApplicationModel(new ArtifactConfig.Builder().build(),
                                declaration, extensions, Collections.emptyMap(), Optional.empty(), Optional.empty(),
                                false, uri -> getClass().getResourceAsStream(uri));
  }

  private void mockSimpleMetadataKeyId(OperationModel model) {

    ParameterModel metadataKeyId = mockKeyPart(METADATA_KEY_PART_1, 1);

    List<ParameterModel> parameterModels = asList(contentParameter, behaviourParameter, listParameter, metadataKeyId);
    when(parameterGroupModel.getParameterModels()).thenReturn(parameterModels);
    when(parameterGroupModel.getParameter(anyString()))
        .then(invocation -> {
          String paramName = invocation.getArgumentAt(0, String.class);
          switch (paramName) {
            case CONTENT_NAME:
              return Optional.of(contentParameter);
            case LIST_NAME:
              return Optional.of(listParameter);
            case BEHAVIOUR_NAME:
              return Optional.of(behaviourParameter);
            case METADATA_KEY_PART_1:
              return Optional.of(metadataKeyId);
          }
          return Optional.empty();
        });

    when(model.getModelProperty(MetadataKeyIdModelProperty.class))
        .thenReturn(Optional.of(new MetadataKeyIdModelProperty(TYPE_LOADER.load(String.class),
                                                               METADATA_KEY_PART_1,
                                                               CATEGORY_NAME)));

    when(model.getAllParameterModels()).thenReturn(parameterModels);
  }

  private void mockMultiLevelMetadataKeyId(OperationModel operationModel) {
    ParameterModel partOne = mockKeyPart(METADATA_KEY_PART_1, 1);
    ParameterModel partTwo = mockKeyPart(METADATA_KEY_PART_2, 2);
    ParameterModel partThree = mockKeyPart(METADATA_KEY_PART_3, 3);
    List<ParameterModel> partParameterModels = asList(partOne, partTwo, partThree);

    ParameterGroupModel metadataKeyIdGroup = mock(ParameterGroupModel.class);
    when(metadataKeyIdGroup.getName()).thenReturn(METADATA_KEY_GROUP);
    when(metadataKeyIdGroup.isShowInDsl()).thenReturn(false);
    when(metadataKeyIdGroup.getParameterModels()).thenReturn(partParameterModels);
    when(metadataKeyIdGroup.getParameter(anyString()))
        .then(invocation -> {
          String paramName = invocation.getArgumentAt(0, String.class);
          switch (paramName) {
            case METADATA_KEY_PART_1:
              return Optional.of(partOne);
            case METADATA_KEY_PART_2:
              return Optional.of(partTwo);
            case METADATA_KEY_PART_3:
              return Optional.of(partThree);
          }
          return Optional.empty();
        });

    ObjectTypeBuilder groupType = BaseTypeBuilder.create(MetadataFormat.JAVA).objectType();
    groupType.addField().key(METADATA_KEY_PART_1).value(TYPE_LOADER.load(String.class));
    groupType.addField().key(METADATA_KEY_PART_2).value(TYPE_LOADER.load(String.class));
    groupType.addField().key(METADATA_KEY_PART_3).value(TYPE_LOADER.load(String.class));

    when(operationModel.getModelProperty(MetadataKeyIdModelProperty.class))
        .thenReturn(Optional.of(new MetadataKeyIdModelProperty(groupType.build(), METADATA_KEY_GROUP, CATEGORY_NAME)));

    when(operationModel.getParameterGroupModels()).thenReturn(Arrays.asList(parameterGroupModel, metadataKeyIdGroup));
    when(operationModel.getAllParameterModels()).thenReturn(ImmutableList.<ParameterModel>builder()
        .addAll(defaultGroupParameterModels)
        .addAll(partParameterModels)
        .build());
  }

  private ParameterModel mockKeyPart(String name, int order) {
    ParameterModel metadataKeyId = mock(ParameterModel.class);
    when(metadataKeyId.getName()).thenReturn(name);
    when(metadataKeyId.getExpressionSupport()).thenReturn(ExpressionSupport.NOT_SUPPORTED);

    when(metadataKeyId.getModelProperty(any())).then(invocation -> {
      if (invocation.getArguments()[0].equals(MetadataKeyPartModelProperty.class)) {
        return Optional.of(new MetadataKeyPartModelProperty(order));
      }
      return Optional.empty();
    });

    when(metadataKeyId.getDslConfiguration()).thenReturn(ParameterDslConfiguration.getDefaultInstance());
    when(metadataKeyId.getLayoutModel()).thenReturn(empty());
    when(metadataKeyId.getRole()).thenReturn(BEHAVIOUR);
    when(metadataKeyId.getType()).thenReturn(TYPE_LOADER.load(String.class));

    return metadataKeyId;
  }

  private MetadataCacheKeyGenerator<ComponentModel> createGenerator(ApplicationModel app) {
    return new ModelBasedMetadataCacheKeyGeneratorFactory().create(dslResolvingContext, new Locator(app));
  }

  private static class Locator implements Function<Location, Optional<ComponentModel>> {

    private Map<Location, ComponentModel> components = new HashMap<>();

    Locator(ApplicationModel app) {
      app.getRootComponentModel().getInnerComponents().forEach(this::addComponent);
    }

    @Override
    public Optional<ComponentModel> apply(Location location) {
      return Optional.ofNullable(components.get(location));
    }

    private Location getLocation(ComponentModel component) {
      return Location.builderFromStringRepresentation(component.getComponentLocation().getLocation()).build();
    }

    private void addComponent(ComponentModel component) {
      components.put(getLocation(component), component);
      component.getInnerComponents().forEach(this::addComponent);
    }

  }

}
