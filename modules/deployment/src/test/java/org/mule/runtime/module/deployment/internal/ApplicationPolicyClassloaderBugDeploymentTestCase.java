/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mule.runtime.api.deployment.meta.Product.MULE;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.MULE_LOADER_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID;
import static org.mule.runtime.module.deployment.internal.TestPolicyProcessor.invocationCount;
import static org.mule.runtime.module.extension.api.loader.java.DefaultJavaExtensionModelLoader.JAVA_LOADER_ID;

import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptorBuilder;
import org.mule.runtime.api.deployment.meta.MulePluginModel;
import org.mule.runtime.api.deployment.meta.MulePolicyModel;
import org.mule.runtime.api.deployment.meta.Product;
import org.mule.runtime.core.api.policy.PolicyParametrization;
import org.mule.runtime.module.deployment.impl.internal.builder.ApplicationFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.JarFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.builder.PolicyFileBuilder;
import org.mule.tck.util.CompilerUtils;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Contains test for application deployment with policies on the default domain
 */
public class ApplicationPolicyClassloaderBugDeploymentTestCase extends AbstractDeploymentTestCase {

  private static final String FOO_POLICY_NAME = "fooPolicy";
  private static final String ANOTHER_POLICY_NAME = "bazPolicy";
  // Policy artifact file builders
  private static File simpleExtensionJarFile;

  public ApplicationPolicyClassloaderBugDeploymentTestCase(boolean parallelDeployment) {
    super(parallelDeployment);
  }

  @BeforeClass
  public static void compileTestClasses() throws Exception {
    simpleExtensionJarFile =
        new CompilerUtils.ExtensionCompiler().compiling(getResourceFile("/org/foo/simple/SimpleExtension.java"),
                                                        getResourceFile("/org/foo/simple/SimpleOperation.java"))
            .compile("mule-module-simple-4.0-SNAPSHOT.jar", "1.0.0");
  }

  @Test
  public void appliesApplicationPolicyIncludingPluginAndResource() throws Exception {
    ArtifactPluginFileBuilder simpleExtensionPlugin = createSingleExtensionPlugin();

    policyManager.registerPolicyTemplate(policyWithPluginAndResource().getArtifactFile());

    ApplicationFileBuilder applicationFileBuilder = createExtensionApplicationWithServices("app-with-simple-extension-config.xml",
                                                                                           simpleExtensionPlugin);

    // If adding the class to the application instead of the policy, it can be loaded from the policy
    // applicationFileBuilder.containingClass(echoTestClassFile, "org/foo/EchoTest.class");

    addPackedAppFromBuilder(applicationFileBuilder);

    startDeployment();
    assertApplicationDeploymentSuccess(applicationDeploymentListener, applicationFileBuilder.getId());

    policyManager.addPolicy(applicationFileBuilder.getId(), policyIncludingPluginFileBuilder.getArtifactId(),
                            new PolicyParametrization(FOO_POLICY_ID, s -> true, 1, emptyMap(),
                                                      getResourceFile("/appPluginPolicy-classloaderBug.xml"), emptyList()));

    executeApplicationFlow("main");
    assertThat(invocationCount, equalTo(1));
  }

  private PolicyFileBuilder policyWithPluginAndResource() {
    MulePolicyModel.MulePolicyModelBuilder mulePolicyModelBuilder = new MulePolicyModel.MulePolicyModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName(ANOTHER_POLICY_NAME)
        .setRequiredProduct(Product.MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(ANOTHER_POLICY_NAME, MULE_POLICY_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID))
        .withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptor(MULE_LOADER_ID, emptyMap()));
    return new PolicyFileBuilder(ANOTHER_POLICY_NAME).describedBy(mulePolicyModelBuilder
        .build())
        .containingClass(echoTestClassFile, "org/foo/EchoTest.class")
        .dependingOn(helloExtensionV1Plugin);
  }

  private ArtifactPluginFileBuilder createSingleExtensionPlugin() {
    MulePluginModel.MulePluginModelBuilder mulePluginModelBuilder = new MulePluginModel.MulePluginModelBuilder()
        .setMinMuleVersion(MIN_MULE_VERSION).setName("simpleExtensionPlugin").setRequiredProduct(MULE)
        .withBundleDescriptorLoader(createBundleDescriptorLoader("simpleExtensionPlugin", MULE_EXTENSION_CLASSIFIER,
                                                                 PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, "1.0.0"));
    mulePluginModelBuilder.withClassLoaderModelDescriptorLoader(new MuleArtifactLoaderDescriptorBuilder().setId(MULE_LOADER_ID)
        .build());
    mulePluginModelBuilder.withExtensionModelDescriber().setId(JAVA_LOADER_ID)
        .addProperty("type", "org.foo.hello.SimpleExtension")
        .addProperty("version", "1.0.0");
    return new ArtifactPluginFileBuilder("simpleExtensionPlugin")
        .dependingOn(new JarFileBuilder("simpleExtension", simpleExtensionJarFile))
        .describedBy(mulePluginModelBuilder.build());
  }

}
