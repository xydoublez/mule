/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.nestedpojo;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import java.util.List;

@Configuration(name = "ExplicitConfig")
public class NestedPojoConfig implements Initialisable, Startable {

  @Parameter
  @Optional
  @NullSafe
  private List<TestPojo> testPojos;

  @Override
  public void start() throws MuleException {
    if (testPojos.get(0).getInnerStrings().isEmpty()) {
      throw new MuleRuntimeException(createStaticMessage("Not populating inner lists"));
    }
  }

  @Override
  public void initialise() throws InitialisationException {
    if (testPojos.get(0).getInnerStrings().isEmpty()) {
      throw new MuleRuntimeException(createStaticMessage("Not populating inner lists"));
    }
  }
}
