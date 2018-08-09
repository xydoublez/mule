/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
// /*
//  * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
//  * The software in this package is published under the terms of the CPAL v1.0
//  * license, a copy of which has been included with this distribution in the
//  * LICENSE.txt file.
//  */
// package org.mule.runtime.module.extension.internal.metadata;
//
// import static java.util.Optional.empty;
// import static org.apache.commons.lang3.StringUtils.isNotBlank;
// import static org.mule.runtime.api.component.ComponentIdentifier.builder;
// import org.mule.metadata.api.model.ArrayType;
// import org.mule.metadata.api.model.ObjectType;
// import org.mule.metadata.api.visitor.MetadataTypeVisitor;
// import org.mule.runtime.api.component.ComponentIdentifier;
// import org.mule.runtime.api.component.location.Location;
// import org.mule.runtime.api.dsl.DslResolvingContext;
// import org.mule.runtime.api.meta.model.config.ConfigurationModel;
// import org.mule.runtime.api.meta.model.connection.ConnectionProviderModel;
// import org.mule.runtime.api.meta.model.parameter.ParameterModel;
// import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
// import org.mule.runtime.api.util.Reference;
// import org.mule.runtime.dsl.api.component.config.ComponentConfiguration;
// import org.mule.runtime.extension.api.dsl.syntax.DslElementSyntax;
// import org.mule.runtime.extension.api.dsl.syntax.resolver.DslSyntaxResolver;
// import org.mule.runtime.extension.api.util.ExtensionMetadataTypeUtils;
//
// import java.util.List;
// import java.util.Map;
// import java.util.Optional;
// import java.util.function.Function;
//
// /**
//  * //TODO
//  */
// public class CacheKeyResolver {
//
//   private final DslSyntaxResolver dslResolver;
//   private final Function<Location, ComponentConfiguration> componentResolver;
//
//   public CacheKeyResolver(DslSyntaxResolver dslResolver, Function<Location, ComponentConfiguration> componentResolver){
//     this.dslResolver = dslResolver;
//     this.componentResolver = componentResolver;
//   }
//
//
//
//   public String resolveKey(ConfigurationModel configModel, ComponentConfiguration configuration,
//                                   DslElementSyntax dsl) {
//
//     final StringBuilder keybuilder = new StringBuilder();
//
//     keybuilder.append(resolveParameterizedKey(configModel, configuration, dsl));
//
//     List<ComponentConfiguration> nestedComponents = configuration.getNestedComponents();
//     for (ConnectionProviderModel connectionModel : configModel.getConnectionProviders()) {
//       Optional<DslElementSyntax> connectionDsl = dsl.getContainedElement(connectionModel.getName());
//       if (connectionDsl.isPresent()) {
//         Optional<ComponentConfiguration> connectionValue = resolveChildActualValue(nestedComponents, connectionDsl.get());
//         if (connectionValue.isPresent()) {
//           keybuilder.append(resolveParameterizedKey(connectionModel, connectionValue.get(),
//                                                     connectionDsl.get()));
//           break;
//         }
//       }
//     }
//
//     return keybuilder.toString();
//   }
//
//   private String resolveParameterizedKey(ParameterizedModel parameterizedModel, ComponentConfiguration configuration,
//                                                 DslElementSyntax dsl) {
//
//     final StringBuilder keybuilder = new StringBuilder();
//     final Map<String, String> actualParameters = configuration.getParameters();
//     final List<ComponentConfiguration> nestedComponents = configuration.getNestedComponents();
//
//     parameterizedModel.getAllParameterModels()
//       .forEach(parameterModel -> {
//         Optional<DslElementSyntax> attribute = dsl.getAttribute(parameterModel.getName());
//         if (attribute.isPresent()) {
//           resolveKeyFromAttribute(parameterModel, actualParameters, attribute.get())
//             .ifPresent(keybuilder::append);
//         } else {
//           resolveChildKey(dsl, parameterModel, nestedComponents)
//             .ifPresent(keybuilder::append);
//         }
//       });
//
//     return keybuilder.toString();
//   }
//
//   private String resolveKeyFromChild(ParameterModel parametermodel,
//                                      ComponentConfiguration configuration,
//                                      Optional<DslElementSyntax> dsl) {
//     parametermodel.getType()
//       .accept(new MetadataTypeVisitor() {
//
//         @Override
//         public void visitArrayType(ArrayType arrayType) {
//           super.visitArrayType(arrayType);
//         }
//
//         @Override
//         public void visitObject(ObjectType objectType) {
//           super.visitObject(objectType);
//         }
//       });
//   }
//
//   private Optional<String> resolveKeyFromAttribute(ParameterModel parametermodel, Map<String, String> actualParameters,
//                                                           DslElementSyntax dsl) {
//     final String actualValue = actualParameters.get(dsl.getAttributeName());
//     if (actualValue == null) {
//       return empty();
//     }
//
//     Reference<String> value = new Reference<>(String.valueOf(actualValue.hashCode()));
//
//     // This may be a reference to a top level element
//     parametermodel.getType().accept(new MetadataTypeVisitor() {
//
//       @Override
//       public void visitArrayType(ArrayType arrayType) {
//         ComponentConfiguration refComponent = componentResolver.apply(Location.builder().globalName(actualValue).build());
//         if (refComponent != null) {
//           // TODO for each child of the ref component obtain the hash of the value
//         }
//       }
//
//       @Override
//       public void visitObject(ObjectType objectType) {
//         ComponentConfiguration refComponent = componentResolver.apply(Location.builder().globalName(actualValue).build());
//         if (refComponent != null) {
//           if (ExtensionMetadataTypeUtils.isMap(objectType)) {
//             // TODO for each child of the ref component obtain the hash of each key-value pair
//           } else {
//             // TODO go through the fields of the type and obtain the parameters of the ref component
//             // go by component elements first to avoid stack overflow
//           }
//         }
//       }
//     });
//
//     return Optional.of(value.get());
//   }
//
//   private Optional<String> resolveChildKey(DslElementSyntax parentDsl, ParameterModel parameterModel,
//                                                   List<ComponentConfiguration> nestedComponents) {
//     return parentDsl.getChild(parameterModel.getName())
//       .map(childDsl -> resolveChildActualValue(nestedComponents, childDsl)
//         .map(complexValue -> {
//           if (childDsl.isWrapped()){
//             resolveKeyFromChild(parameterModel, complexValue, dslResolver.resolve(parameterModel.getType()));
//           }
//         })
//         .orElse(null));
//   }
//
//   private Optional<ComponentConfiguration> resolveChildActualValue(List<ComponentConfiguration> nestedComponents,
//                                                                           DslElementSyntax childDsl) {
//     return getIdentifier(childDsl)
//       .map(id -> nestedComponents
//         .stream()
//         .filter(c -> c.getIdentifier().equals(id))
//         // TODO repeated child names?
//         .findFirst().orElse(null));
//   }
//
//   private Optional<ComponentIdentifier> getIdentifier(DslElementSyntax dsl) {
//     if (isNotBlank(dsl.getElementName()) && isNotBlank(dsl.getPrefix())) {
//       return Optional.of(builder()
//                            .name(dsl.getElementName())
//                            .namespace(dsl.getPrefix())
//                            .build());
//     }
//
//     return empty();
//   }
//
//
// }
