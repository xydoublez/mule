/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.dsl.model;

import org.mule.runtime.config.internal.model.ComponentModel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents the context of resolution of an expression.
 *
 * Since an expression may have nested expressions (with other operation calls) this class allows to hold intermediate definition
 * as well as the related auxiliary variables names used for their intermediate results
 */
public class StatementResolutionContext {

  private AtomicInteger auxVariableIndex = new AtomicInteger(1);
  private List<ComponentModel> componentModels = new LinkedList<>();

  public void addComponentModel(ComponentModel componentModel) {
    this.componentModels.add(componentModel);
  }

  public String generateNextAuxVarName() {
    return "aux" + auxVariableIndex.getAndIncrement();
  }

  public List<ComponentModel> getComponentModels() {
    return Collections.unmodifiableList(componentModels);
  }
}
