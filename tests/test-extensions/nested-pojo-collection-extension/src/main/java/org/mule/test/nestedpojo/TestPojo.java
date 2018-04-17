/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.nestedpojo;

import static org.mule.runtime.api.meta.ExpressionSupport.SUPPORTED;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

public final class TestPojo implements Serializable, Component {

  @Override
  public Object getAnnotation(QName name) {
    return null;
  }

  @Override
  public Map<QName, Object> getAnnotations() {
    return null;
  }

  @Override
  public void setAnnotations(Map<QName, Object> annotations) {

  }

  @Override
  public ComponentLocation getLocation() {
    return null;
  }

  @Override
  public Location getRootContainerLocation() {
    return null;
  }

  @Parameter
  @Optional
  @NullSafe
  @Expression(SUPPORTED)
  private List<String> innerStrings;

  public List<String> getInnerStrings() {
    return innerStrings;
  }
}
