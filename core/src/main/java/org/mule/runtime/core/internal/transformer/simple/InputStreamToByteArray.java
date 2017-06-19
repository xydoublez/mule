/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.transformer.simple;

import static org.mule.runtime.api.metadata.DataType.BYTE_ARRAY;
import static org.mule.runtime.api.metadata.DataType.CURSOR_STREAM_PROVIDER;
import static org.mule.runtime.api.metadata.DataType.INPUT_STREAM;
import org.mule.runtime.api.streaming.bytes.CursorStreamProvider;
import org.mule.runtime.core.api.transformer.TransformerException;
import org.mule.runtime.core.transformer.AbstractDiscoverableTransformer;

import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Transforms {@link InputStream input streams} into byte arrays. Streams are accepted in either its
 * natural {@link InputStream} form or as a {@link CursorStreamProvider}
 *
 * @since 4.0
 */
public class InputStreamToByteArray extends AbstractDiscoverableTransformer {

  private ObjectToByteArray delegate = new ObjectToByteArray();

  public InputStreamToByteArray() {
    setPriorityWeighting(MAX_PRIORITY_WEIGHTING);
    registerSourceType(CURSOR_STREAM_PROVIDER);
    registerSourceType(INPUT_STREAM);
    setReturnDataType(BYTE_ARRAY);
  }

  @Override
  protected Object doTransform(Object src, Charset enc) throws TransformerException {
    return delegate.doTransform(src, enc);
  }
}
