/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.module.extension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_XML;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.test.heisenberg.extension.model.Glass;
import org.mule.test.heisenberg.extension.model.Methylamine;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ExtensionImplicitTransformerTestCase extends AbstractExtensionFunctionalTestCase {

  private static final String IMPORTER = "Pollos Hermanos";
  private static final boolean STOLEN = true;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Override
  protected String[] getConfigFiles() {
    return new String[] {"heisenberg-transformer-config.xml"};
  }

  @Override
  protected boolean isDisposeContextPerClass() {
    return true;
  }

  @Test
  public void transformJson() throws Exception {
    assertMethylamine(String.format("{\"importer\": \"%s\", \"stolen\": %s}", IMPORTER, STOLEN), APPLICATION_JSON);
  }

  @Test
  public void transformXml() throws Exception {
    assertMethylamine(String.format("<methylamine><importer>%s</importer><stolen>%s</stolen></methylamine>", IMPORTER, STOLEN),
                      APPLICATION_XML);
  }

  @Test
  public void transformGlass() throws Exception {
    Glass glass = new Glass();
    glass.setBrand(IMPORTER);
    assertMethylamine(glass, MediaType.ANY);
  }

  private void assertMethylamine(Object payload, MediaType mediaType) throws Exception {
    Object result = flowRunner("implicitTransformer")
        .withPayload(payload)
        .withMediaType(mediaType)
        .run().getMessage().getPayload().getValue();

    assertThat(result, is(instanceOf(Methylamine.class)));
    Methylamine methylamine = (Methylamine) result;
    assertThat(methylamine.getImporter(), equalTo(IMPORTER));
    assertThat(methylamine.isStolen(), is(STOLEN));
  }

}
