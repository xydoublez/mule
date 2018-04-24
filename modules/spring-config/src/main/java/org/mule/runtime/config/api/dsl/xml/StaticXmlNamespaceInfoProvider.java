/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.api.dsl.xml;

import java.util.Collection;

import org.mule.runtime.dsl.api.xml.XmlNamespaceInfo;
import org.mule.runtime.dsl.api.xml.XmlNamespaceInfoProvider;

/**
 * A {@link XmlNamespaceInfoProvider} which provides a fixed set of {@link XmlNamespaceInfo} instances obtained through the
 * constructor
 *
 * @since 4.0
 * 
 * @deprecated use {@link org.mule.runtime.dsl.api.xml.StaticXmlNamespaceInfoProvider} instead.
 */
@Deprecated
public final class StaticXmlNamespaceInfoProvider extends org.mule.runtime.dsl.api.xml.StaticXmlNamespaceInfoProvider {

  /**
   * Creates a new instance
   *
   * @param namespaceInfos the {@link Collection} to be returned by {@link #getXmlNamespacesInfo()}
   */
  public StaticXmlNamespaceInfoProvider(Collection<XmlNamespaceInfo> namespaceInfos) {
    super(namespaceInfos);
  }
}
