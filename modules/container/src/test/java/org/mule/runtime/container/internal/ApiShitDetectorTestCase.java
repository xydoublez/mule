/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.container.internal;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;
import static org.mule.runtime.container.internal.ContainerClassLoaderFactory.SYSTEM_PACKAGES;
import org.mule.runtime.container.api.MuleModule;
import org.mule.runtime.core.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.filefilter.NameFileFilter;
import org.junit.Test;

public class ApiShitDetectorTestCase {


  private static final String PACKAGE_SEPARATOR = "\n";

  @Test
  public void detectsShit() throws Exception {
    File projectRoot = new File("/Users/pablokraan/devel/workspaces/mule-uber1/");

    Collection<File> modulePropertyFiles = FileUtils.findFiles(projectRoot, new NameFileFilter("mule-module.properties"), true);

    List<String> logOK = new ArrayList<>();
    List<String> logError = new ArrayList<>();

    int thirdPartyPackageCount = 0;
    int internalMulePackageCount = 0;
    int moduleErrorCount = 0;

    for (File modulePropertyFile : modulePropertyFiles) {
      if (modulePropertyFile.getAbsolutePath().contains("target")) {
        continue;
      }

      if (!modulePropertyFile.getAbsolutePath().contains("src/main"))
      {
        logOK.add("Test module: " + modulePropertyFile.getAbsolutePath());
        continue;
      }

      Properties properties = new Properties();
      properties.load(modulePropertyFile.toURL().openStream());
      MuleModule module = ClasspathModuleDiscoverer.createModule(properties);


      List<String> nonMulePackages = module.getExportedPackages().stream().filter(p -> !isMulePackage(p)).collect(toList());
      nonMulePackages.sort(String::compareTo);
      List<String> internalMulePackages = module.getExportedPackages().stream().filter(p -> isInternalMulePackage(p)).collect(toList());
      internalMulePackages.sort(String::compareTo);

      if (nonMulePackages.isEmpty() && internalMulePackages.isEmpty())
      {
        logOK.add("Module: " + module.getName() + " at " + modulePropertyFile.getAbsolutePath());
      }
      else {
        moduleErrorCount += 1;
        logError.add("\nModule: " + module.getName() + " at " + modulePropertyFile.getAbsolutePath());
        thirdPartyPackageCount += nonMulePackages.size();
        internalMulePackageCount += internalMulePackages.size();
        StringBuilder builder = new StringBuilder("Non Mule packages:");
        nonMulePackages.stream().forEach(p -> builder.append(PACKAGE_SEPARATOR).append(p));
        builder.append("\n\nInternal Mule packages");
        internalMulePackages.stream().forEach(p -> builder.append(PACKAGE_SEPARATOR).append(p));
        logError.add(builder.toString());
      }
    }

    System.out.println("MODULES OK");
    logOK.stream().forEach(System.out::println);
    if (thirdPartyPackageCount != 0) {
      System.out.println("\nMODULES WITH ERRORS");
      logError.stream().forEach(System.out::println);
      System.out.println("\nTOTAL MODULES WITH ERRORS: " + moduleErrorCount);
      System.out.println("TOTAL EXPORTED INTERNAL MULE PACKAGES: " + internalMulePackageCount);
      System.out.println("TOTAL EXPORTED THIRD PARTY PACKAGES: " + thirdPartyPackageCount);
      fail("Mule API exports third party and/or Mule internal packages");
    }     
  }

  private boolean isInternalMulePackage(String p) {
    return isMulePackage(p) && (!p.contains(".api.") && !p.endsWith(".api"));
  }

  private boolean isMulePackage(String p) {
    for (String systemPackage : SYSTEM_PACKAGES) {

      if (p.startsWith(systemPackage))
      {
        return true;
      }
    }

    if (p.startsWith("org.mule.mvel2"))
    {
      return false;
    }
    
    // TODO(pablo.kraan): apiJanitor - should this prefix be moved to org.mule.runtime?
    if (p.startsWith("org.mule."))
    {
      return true;
    }

    return false;
  }
}
