/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.dsl;

import static java.lang.Thread.currentThread;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;


public class ClassLoaderResourceProviderTestCase {

  private ClassLoaderResourceProvider resourceProvider;

  @Before
  public void setUp() {
    resourceProvider = new ClassLoaderResourceProvider(currentThread().getContextClassLoader());
  }

  @Test
  public void sameJarResourceGetsLoaded() {
    verifyResourceGetsLoadedSuccessfully(Paths.get("META-INF", "mule-module.properties").toString());
  }

  @Test
  public void differentJarResourceGetsLoaded() {
    verifyResourceGetsLoadedSuccessfully(Paths.get("javax", "inject", "Inject.class").toString());
  }

  @Test
  public void absolutePathResourceGetsLoaded() {
    File file = toFile(getClass().getClassLoader().getResource(Paths.get("META-INF", "mule-module.properties").toString()));
    verifyResourceGetsLoadedSuccessfully(file.getAbsolutePath());
  }

  private void verifyResourceGetsLoadedSuccessfully(String resource) {
    try (InputStream content = resourceProvider.getResourceAsStream(resource)) {
      assertThat(content, notNullValue());
    } catch (IOException e) {
      fail();
    }
  }

  public static File toFile(URL url) {
    String filename = url.getFile().replace('/', File.separatorChar);
    return new File(filename);
  }

}
