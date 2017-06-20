/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.heisenberg.extension;

import static java.util.Arrays.asList;
import static org.mule.runtime.api.metadata.DataType.fromType;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_XML;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.extension.api.runtime.transformer.ImplicitTransformer;
import org.mule.test.heisenberg.extension.model.Methylamine;

import com.thoughtworks.xstream.XStream;

import java.util.List;

public class XmlToMethylamine implements ImplicitTransformer {

  public static final String NAME = "xmlToMethylamine";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public List<DataType> getSourceTypes() {
    return asList(DataType.builder().type(String.class).mediaType(APPLICATION_XML).build());
  }

  @Override
  public DataType getOutputType() {
    return fromType(Methylamine.class);
  }

  @Override
  public Object transform(Object value) {
    XStream xStream = new XStream();
    xStream.alias("methylamine", Methylamine.class);
    return xStream.fromXML((String) value);
  }
}
