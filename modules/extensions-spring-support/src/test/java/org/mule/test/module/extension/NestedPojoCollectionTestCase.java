/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.module.extension;


import org.junit.Test;

public class NestedPojoCollectionTestCase extends AbstractExtensionFunctionalTestCase {

  @Override
  protected String getConfigFile() {
    return "nested-pojo-collection-config.xml";
  }

  @Test
  public void checkIfCorrect() throws Exception {
    flowRunner("testFlow").run();
  }
}
