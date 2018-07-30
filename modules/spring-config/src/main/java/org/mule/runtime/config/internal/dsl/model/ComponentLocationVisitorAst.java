/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.component.ComponentIdentifier.buildFromStringRepresentation;
import static org.mule.runtime.api.component.TypedComponentIdentifier.builder;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.OPERATION;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.ROUTE;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.SCOPE;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.UNKNOWN;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.ERROR_HANDLER_IDENTIFIER;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.FLOW_IDENTIFIER;
import static org.mule.runtime.config.api.dsl.CoreDslConstants.SUBFLOW_IDENTIFIER;
import static org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper.isErrorHandler;
import static org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper.isMessageSource;
import static org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper.isProcessor;
import static org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper.isRouter;
import static org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper.isTemplateOnErrorHandler;
import static org.mule.runtime.config.internal.model.ApplicationModel.HTTP_PROXY_OPERATION_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.HTTP_PROXY_POLICY_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.HTTP_PROXY_SOURCE_POLICY_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.MODULE_OPERATION_CHAIN;
import static org.mule.runtime.config.internal.model.ApplicationModel.MUNIT_AFTER_SUITE_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.MUNIT_AFTER_TEST_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.MUNIT_BEFORE_SUITE_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.MUNIT_BEFORE_TEST_IDENTIFIER;
import static org.mule.runtime.config.internal.model.ApplicationModel.MUNIT_TEST_IDENTIFIER;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.mule.runtime.api.artifact.ast.ArtifactAst;
import org.mule.runtime.api.artifact.ast.ComponentAst;
import org.mule.runtime.api.component.AbstractComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.config.internal.dsl.spring.ComponentModelHelper;
import org.mule.runtime.config.internal.model.ApplicationModel;
import org.mule.runtime.config.internal.model.ComponentModel;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation.DefaultLocationPart;

import com.google.common.collect.ImmutableList;

/**
 * Visitor that setups the {@link DefaultComponentLocation} for all mule components in the artifact configuration.
 *
 * @since 4.0
 */
// TODO MULE-13618 - Migrate ComponentLocationVisitor to use ExtensionModels
public class ComponentLocationVisitorAst implements Consumer<ComponentAst> {

  public static final ComponentIdentifier BATCH_JOB_COMPONENT_IDENTIFIER = buildFromStringRepresentation("batch:job");
  public static final ComponentIdentifier BATCH_PROCESSS_RECORDS_COMPONENT_IDENTIFIER =
      buildFromStringRepresentation("batch:process-records");
  private static final ComponentIdentifier BATCH_ON_COMPLETE_IDENTIFIER =
      buildFromStringRepresentation("batch:on-complete");
  private static final ComponentIdentifier BATCH_STEP_COMPONENT_IDENTIFIER = buildFromStringRepresentation("batch:step");
  private static final ComponentIdentifier BATCH_AGGREGATOR_COMPONENT_IDENTIFIER =
      buildFromStringRepresentation("batch:aggregator");
  private static final String PROCESSORS_PART_NAME = "processors";
  private static final ComponentIdentifier ROUTE_COMPONENT_IDENTIFIER = buildFromStringRepresentation("mule:route");
  private static final ComponentIdentifier CHOICE_WHEN_COMPONENT_IDENTIFIER = buildFromStringRepresentation("mule:when");
  private static final ComponentIdentifier CHOICE_OTHERWISE_COMPONENT_IDENTIFIER =
      buildFromStringRepresentation("mule:otherwise");
  private final ExtensionModelHelper extensionModelHelper;
  private final ArtifactAst artifactAst;

  public ComponentLocationVisitorAst(ExtensionModelHelper extensionModelHelper, ArtifactAst artifactAst) {
    this.extensionModelHelper = extensionModelHelper;
    this.artifactAst = artifactAst;
  }

  /**
   * For every {@link ComponentModel} in the configuration, sets the {@link DefaultComponentLocation} associated within an
   * annotation under the key {@link AbstractComponent#LOCATION_KEY}.
   *
   * @param componentAst the component model that will be assign it's {@link DefaultComponentLocation}.
   */
  @Override
  public void accept(ComponentAst componentAst) {
    if (isRoot(componentAst)) {
      // do not process root element
      return;
    }
    DefaultComponentLocation componentLocation;
    Optional<TypedComponentIdentifier> typedComponentIdentifier =
        of(builder().identifier(componentAst.getComponentIdentifier()).type(componentAst.getComponentType().orElse(UNKNOWN))
            .build());

    if (isRoot(componentAst)) {
      String componentModelNameAttribute = componentAst.getNameParameterValueOrNull();
      ImmutableList<DefaultLocationPart> parts =
          ImmutableList.<DefaultLocationPart>builder()
              .add(new DefaultLocationPart(componentModelNameAttribute,
                                           typedComponentIdentifier,
                                           Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                           Optional.of(componentAst.getSourceCodeLocation().getStartLine())))
              .build();
      componentLocation = new DefaultComponentLocation(ofNullable(componentModelNameAttribute), parts);
    } else if (existsWithinRootContainer(componentAst)) {
      ComponentAst parentComponentAst = getParentComponentAst(componentAst);
      DefaultComponentLocation parentComponentLocation = (DefaultComponentLocation) parentComponentAst.getComponentLocation();
      if (isHttpProxyPart(componentAst)) {
        componentLocation =
            parentComponentLocation.appendLocationPart(componentAst.getComponentIdentifier().getName(), typedComponentIdentifier,
                                                       Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                       Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
      } else if (isRootProcessorScope(parentComponentAst)) {
        componentLocation = processFlowDirectChild(componentAst, parentComponentLocation, typedComponentIdentifier);
      } else if (isMunitFlowIdentifier(parentComponentAst)) {
        componentLocation = parentComponentLocation.appendRoutePart()
            .appendLocationPart(findNonProcessorPath(componentAst), typedComponentIdentifier,
                                Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
      } else if (isErrorHandler(componentAst)) {
        componentLocation = processErrorHandlerComponent(componentAst, parentComponentLocation, typedComponentIdentifier);
      } else if (isTemplateOnErrorHandler(componentAst)) {
        componentLocation = processOnErrorModel(componentAst, parentComponentLocation, typedComponentIdentifier);
      } else if (parentComponentIsRouter(componentAst)) {
        if (isRoute(componentAst)) {
          componentLocation = parentComponentLocation.appendRoutePart()
              .appendLocationPart(findRoutePath(componentAst), of(TypedComponentIdentifier.builder().type(SCOPE)
                  .identifier(ROUTE_COMPONENT_IDENTIFIER).build()),
                                  Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                  Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
        } else if (isProcessor(componentAst)) {
          // this is the case of the routes directly inside the router as with scatter-gather
          componentLocation = parentComponentLocation
              .appendRoutePart()
              .appendLocationPart(findProcessorPath(componentAst), empty(), empty(), empty())
              .appendProcessorsPart()
              .appendLocationPart("0", typedComponentIdentifier, Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                  Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
        } else {
          componentLocation =
              parentComponentLocation.appendLocationPart(findNonProcessorPath(componentAst), typedComponentIdentifier,
                                                         Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                         Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
        }
      } else if (isProcessor(componentAst)) {
        if (isModuleOperation(getParentComponentAst(componentAst))) {
          final Optional<TypedComponentIdentifier> operationTypedIdentifier =
              ApplicationModel.MODULE_OPERATION_CHAIN.equals(typedComponentIdentifier.get().getIdentifier())
                  ? getModuleOperationTypeComponentIdentifier(componentAst)
                  : typedComponentIdentifier;
          componentLocation = processModuleOperationChildren(componentAst, operationTypedIdentifier);
        } else {
          componentLocation = parentComponentLocation.appendProcessorsPart().appendLocationPart(findProcessorPath(componentAst),
                                                                                                typedComponentIdentifier,
                                                                                                Optional.of(componentAst
                                                                                                    .getSourceCodeLocation()
                                                                                                    .getFilename()),
                                                                                                Optional.of(componentAst
                                                                                                    .getSourceCodeLocation()
                                                                                                    .getStartLine()));
        }
      } else {
        if (isBatchAggregator(componentAst)) {
          componentLocation = parentComponentLocation
              .appendLocationPart(BATCH_AGGREGATOR_COMPONENT_IDENTIFIER.getName(),
                                  of(TypedComponentIdentifier.builder().type(UNKNOWN)
                                      .identifier(BATCH_AGGREGATOR_COMPONENT_IDENTIFIER).build()),
                                  Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                  Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
        } else {
          componentLocation =
              parentComponentLocation.appendLocationPart(findNonProcessorPath(componentAst), typedComponentIdentifier,
                                                         Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                         Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
        }
      }
    } else {
      DefaultComponentLocation parentComponentLocation =
          (DefaultComponentLocation) getParentComponentAst(componentAst).getComponentLocation();
      componentLocation =
          parentComponentLocation.appendLocationPart(findNonProcessorPath(componentAst), typedComponentIdentifier,
                                                     Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                     Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
    }
    componentAst.setComponentLocation(componentLocation);
  }

  private ComponentAst getParentComponentAst(ComponentAst componentAst) {
    return artifactAst.getParentComponentAst(componentAst).orElse(null);
  }

  private boolean isBatchAggregator(ComponentAst componentAst) {
    return BATCH_AGGREGATOR_COMPONENT_IDENTIFIER.equals(componentAst.getComponentIdentifier());
  }

  private boolean isRoute(ComponentAst componentAst) {
    return componentAst.getComponentIdentifier().equals(ROUTE_COMPONENT_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(CHOICE_WHEN_COMPONENT_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(CHOICE_OTHERWISE_COMPONENT_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(BATCH_PROCESSS_RECORDS_COMPONENT_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(BATCH_ON_COMPLETE_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(BATCH_STEP_COMPONENT_IDENTIFIER)
        || componentAst.getComponentType().map(componentType -> componentType == ROUTE).orElse(false);
  }

  private boolean isHttpProxyPart(ComponentAst componentAst) {
    return componentAst.getComponentIdentifier().equals(HTTP_PROXY_SOURCE_POLICY_IDENTIFIER)
        || componentAst.getComponentIdentifier().equals(HTTP_PROXY_OPERATION_IDENTIFIER);
  }

  private boolean isMunitFlowIdentifier(ComponentAst componentAst) {
    return componentAst.getComponentIdentifier().equals(MUNIT_TEST_IDENTIFIER);
  }

  private boolean isRootProcessorScope(ComponentAst componentAst) {
    ComponentIdentifier identifier = componentAst.getComponentIdentifier();
    return identifier.equals(FLOW_IDENTIFIER) || identifier.equals(MUNIT_BEFORE_SUITE_IDENTIFIER) ||
        identifier.equals(MUNIT_BEFORE_TEST_IDENTIFIER) || identifier.equals(MUNIT_AFTER_SUITE_IDENTIFIER) ||
        identifier.equals(MUNIT_AFTER_TEST_IDENTIFIER);
  }

  private boolean isModuleOperation(ComponentAst componentAst) {
    return componentAst.getComponentIdentifier().equals(MODULE_OPERATION_CHAIN);
  }

  private boolean parentComponentIsRouter(ComponentAst componentAst) {
    return existsWithinRouter(componentAst) && isRouter(getParentComponentAst(componentAst));
  }

  private boolean existsWithinRouter(ComponentAst componentAst) {
    while (getParentComponentAst(componentAst) != null) {
      if (isRouter(componentAst)) {
        return true;
      }
      componentAst = getParentComponentAst(componentAst);
    }
    return false;
  }

  private String findNonProcessorPath(ComponentAst componentAst) {
    // we just lookup the position of the component model within the children
    int i = 0;
    for (ComponentAst child : getParentComponentAst(componentAst).getAllNestedComponentAst()) {
      if (child == componentAst) {
        break;
      }
      i++;
    }
    return String.valueOf(i);
  }

  private String findRoutePath(ComponentAst componentAst) {
    int i = 0;
    for (ComponentAst child : getParentComponentAst(componentAst).getAllNestedComponentAst()) {
      if (child == componentAst) {
        break;
      }
      if (isRoute(child)) {
        i++;
      }
    }
    return String.valueOf(i);
  }


  private DefaultComponentLocation processOnErrorModel(ComponentAst componentAst,
                                                       DefaultComponentLocation parentComponentLocation,
                                                       Optional<TypedComponentIdentifier> typedComponentIdentifier) {
    ComponentAst parentComponentAst = getParentComponentAst(componentAst);
    int i = 0;
    for (ComponentAst childComponentAst : parentComponentAst.getAllNestedComponentAst()) {
      if (childComponentAst == componentAst) {
        break;
      }
      i++;
    }
    return parentComponentLocation.appendLocationPart(String.valueOf(i), typedComponentIdentifier,
                                                      Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                      Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
  }

  private DefaultComponentLocation processFlowDirectChild(ComponentAst componentAst,
                                                          DefaultComponentLocation parentComponentLocation,
                                                          Optional<TypedComponentIdentifier> typedComponentIdentifier) {
    DefaultComponentLocation componentLocation;
    if (isMessageSource(componentAst)) {
      componentLocation =
          parentComponentLocation.appendLocationPart("source", typedComponentIdentifier,
                                                     Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                     Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
    } else if (isProcessor(componentAst)) {
      if (isModuleOperation(componentAst)) {
        // just point to the correct typed component operation identifier
        typedComponentIdentifier = getModuleOperationTypeComponentIdentifier(componentAst);
      }
      componentLocation = parentComponentLocation
          .appendLocationPart(PROCESSORS_PART_NAME, empty(), empty(), empty())
          .appendLocationPart(findProcessorPath(componentAst), typedComponentIdentifier,
                              Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                              Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
    } else if (isErrorHandler(componentAst)) {
      componentLocation = processErrorHandlerComponent(componentAst, parentComponentLocation, typedComponentIdentifier);
    } else {
      componentLocation =
          parentComponentLocation.appendLocationPart(findNonProcessorPath(componentAst), typedComponentIdentifier,
                                                     Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                     Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
    }
    return componentLocation;
  }

  private Optional<TypedComponentIdentifier> getModuleOperationTypeComponentIdentifier(ComponentAst componentModel) {
    // TODO review
    final ComponentIdentifier originalIdentifier = null;
    // final ComponentIdentifier originalIdentifier =
    // (ComponentIdentifier) componentModel.getCustomAttributes().get(ORIGINAL_IDENTIFIER);

    final String namespace = originalIdentifier.getNamespace();
    final String operationName = originalIdentifier.getName();

    final ComponentIdentifier operationIdentifier =
        ComponentIdentifier.builder().namespace(namespace).name(operationName).build();
    return of(builder().identifier(operationIdentifier).type(OPERATION).build());
  }

  /**
   * It rewrites the history for those macro expanded operations that are not direct children from a flow, which means the
   * returned {@link ComponentLocation} are mapped to the new operation rather the original flow.
   *
   * @param componentAst source to generate the new {@link ComponentLocation}, it also relies in its parent
   *        {@link ComponentModel#getParent()}
   * @param operationTypedIdentifier identifier of the current operation
   * @return a fictitious {@link ComponentLocation}
   */
  private DefaultComponentLocation processModuleOperationChildren(ComponentAst componentAst,
                                                                  Optional<TypedComponentIdentifier> operationTypedIdentifier) {
    final Optional<TypedComponentIdentifier> parentOperationTypedIdentifier =
        getModuleOperationTypeComponentIdentifier(getParentComponentAst(componentAst));
    final String operationName = parentOperationTypedIdentifier.get().getIdentifier().getName();
    return new DefaultComponentLocation(of(operationName), emptyList())
        .appendLocationPart(operationName, parentOperationTypedIdentifier,
                            Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                            Optional.of(componentAst.getSourceCodeLocation().getStartLine()))
        .appendLocationPart(PROCESSORS_PART_NAME, empty(), empty(), empty())
        .appendLocationPart(findProcessorPath(componentAst), operationTypedIdentifier,
                            Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                            Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
  }

  private DefaultComponentLocation processErrorHandlerComponent(ComponentAst componentAst,
                                                                DefaultComponentLocation parentComponentLocation,
                                                                Optional<TypedComponentIdentifier> typedComponentIdentifier) {
    DefaultComponentLocation componentLocation;
    componentLocation =
        parentComponentLocation.appendLocationPart("errorHandler", typedComponentIdentifier,
                                                   Optional.of(componentAst.getSourceCodeLocation().getFilename()),
                                                   Optional.of(componentAst.getSourceCodeLocation().getStartLine()));
    return componentLocation;
  }

  private String findProcessorPath(ComponentAst componentAst) {
    ComponentAst parentComponentAst = getParentComponentAst(componentAst);
    List<ComponentAst> processorModels =
        parentComponentAst.getAllNestedComponentAst().stream().filter(ComponentModelHelper::isProcessor)
            .collect(Collectors.toList());
    int i = 0;
    for (ComponentAst processorAst : processorModels) {
      if (processorAst == componentAst) {
        break;
      }
      i++;
    }
    return String.valueOf(i);
  }

  private boolean existsWithinRootContainer(ComponentAst componentAst) {
    return existsWithin(componentAst, FLOW_IDENTIFIER)
        || existsWithin(componentAst, MUNIT_TEST_IDENTIFIER)
        || existsWithin(componentAst, MUNIT_BEFORE_SUITE_IDENTIFIER)
        || existsWithin(componentAst, MUNIT_BEFORE_TEST_IDENTIFIER)
        || existsWithin(componentAst, MUNIT_AFTER_SUITE_IDENTIFIER)
        || existsWithin(componentAst, MUNIT_AFTER_TEST_IDENTIFIER)
        || existsWithin(componentAst, HTTP_PROXY_POLICY_IDENTIFIER)
        || existsWithinRootErrorHandler(componentAst)
        || existsWithinSubflow(componentAst);
  }

  private boolean existsWithinRootErrorHandler(ComponentAst componentAst) {
    while (getParentComponentAst(componentAst) != null) {
      if (getParentComponentAst(componentAst).getComponentIdentifier().equals(ERROR_HANDLER_IDENTIFIER) && isRoot(componentAst)) {
        return true;
      }
      componentAst = getParentComponentAst(componentAst);
    }
    return false;
  }

  private boolean isRoot(ComponentAst componentAst) {
    return getParentComponentAst(componentAst) == null;
  }

  private boolean existsWithinSubflow(ComponentAst componentAst) {
    return existsWithin(componentAst, SUBFLOW_IDENTIFIER);
  }

  private boolean existsWithin(ComponentAst componentAst, ComponentIdentifier componentIdentifier) {
    while (!isRoot(componentAst)) {
      if (getParentComponentAst(componentAst).getComponentIdentifier().equals(componentIdentifier)) {
        return true;
      }
      componentAst = getParentComponentAst(componentAst);
    }
    return false;
  }
}
