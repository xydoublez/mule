/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.config.internal.dsl.xtext;

import org.mule.runtime.config.api.dsl.processor.ConfigFile;

import com.google.inject.Injector;

import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.xtext.example.mydsl.MyDslStandaloneSetup;

public class XtextParser {

  private XtextResourceSet resourceSet;

  public Resource parse(ConfigFile configFile) {
    // do this only once per application
    Injector injector = new MyDslStandaloneSetup().createInjectorAndDoEMFRegistration();

    // obtain a resourceset from the injector
    XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);

    // load a resource by URI, in this case from the file system
    java.net.URI resourceUri;
    try {
      resourceUri = Thread.currentThread().getContextClassLoader().getResource(configFile.getFilename()).toURI();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException();
    }

    //Resource resource = resourceSet.getResource(URI.createURI(resourceUri.toString()), true);
    ///Users/pablokraan/devel/workspaces/mule-uber2/mule/modules/deployment/src/test/resources/simple-app.mydsl
    URI fileURI = URI
        .createFileURI("/Users/pablokraan/devel/workspaces/mule-uber2/mule/modules/deployment/src/test/resources/simple-app.mydsl");
    Resource resource = resourceSet.getResource(fileURI, true);



    // Validation
    IResourceValidator validator = ((XtextResource) resource).getResourceServiceProvider().getResourceValidator();
    List<Issue> issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl);
    for (Issue issue : issues) {
      System.out.println(issue.getMessage());
    }

    return resource;


    //// Code Generator
    //GeneratorDelegate generator = injector.getInstance(GeneratorDelegate.class);
    //InMemoryFileSystemAccess fsa = new InMemoryFileSystemAccess();
    //generator.doGenerate(resource, fsa);
    //for (Entry<String, CharSequence> file : fsa.getTextFiles().entrySet()) {
    //  System.out.println("Generated file path : "+file.getKey());
    //  System.out.println("Generated file contents : "+file.getValue());
    //}
  }
}
