/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.xml;

import org.mule.runtime.dsl.api.xml.XmlNamespaceInfo;

/**
 * A {@link XmlNamespaceInfo} which returns fix values obtained through the constructor
 *
 * @since 4.0
 *
 * @deprecated use {@link org.mule.runtime.dsl.api.xml.StaticXmlNamespaceInfo} instead.
 */
@Deprecated
public final class StaticXmlNamespaceInfo extends org.mule.runtime.dsl.api.xml.StaticXmlNamespaceInfo {

  /**
   * Creates a new instance
   *
   * @param namespaceUriPrefix the value to be returned by {@link #getNamespaceUriPrefix()}
   * @param namespace the value to be returned by {@link #getNamespace()}
   */
  public StaticXmlNamespaceInfo(String namespaceUriPrefix, String namespace) {
    super(namespaceUriPrefix, namespace);
  }
}
