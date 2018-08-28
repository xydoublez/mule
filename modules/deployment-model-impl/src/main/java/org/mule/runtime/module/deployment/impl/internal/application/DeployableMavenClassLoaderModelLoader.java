/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.application;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkState;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.DOMAIN;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.POLICY;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.INCLUDE_TEST_DEPENDENCIES;
import static org.mule.runtime.deployment.model.api.artifact.ArtifactDescriptorConstants.MULE_LOADER_ID;
import static org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor.MULE_PLUGIN_CLASSIFIER;
import static org.mule.runtime.module.artifact.api.classloader.MuleMavenPlugin.MULE_MAVEN_PLUGIN_ARTIFACT_ID;
import static org.mule.runtime.module.artifact.api.classloader.MuleMavenPlugin.MULE_MAVEN_PLUGIN_GROUP_ID;
import static org.mule.runtime.module.deployment.impl.internal.maven.MavenUtils.getPomModelFolder;
import org.mule.maven.client.api.MavenClient;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.i18n.I18nMessageFactory;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.module.artifact.api.descriptor.BundleDependency;
import org.mule.runtime.module.artifact.api.descriptor.BundleDescriptor;
import org.mule.runtime.module.artifact.api.descriptor.ClassLoaderModel;
import org.mule.runtime.module.artifact.api.descriptor.ClassLoaderModel.ClassLoaderModelBuilder;
import org.mule.runtime.module.artifact.internal.util.FileJarExplorer;
import org.mule.runtime.module.artifact.internal.util.JarInfo;
import org.mule.runtime.module.deployment.impl.internal.maven.AbstractMavenClassLoaderModelLoader;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible of returning the {@link BundleDescriptor} of a given plugin's location and also creating a
 * {@link ClassLoaderModel}
 *
 * @since 4.0
 */
public class DeployableMavenClassLoaderModelLoader extends AbstractMavenClassLoaderModelLoader {

  private static final String PLUGIN_SHARED_LIBRARIES_TAG = "pluginSharedLibraries";
  private static final String PLUGIN_TAG = "plugin";
  private static final String SHARED_LIBRARIES_TAG = "sharedLibraries";
  private static final String SHARED_LIBRARY_TAG = "sharedLibrary";

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  public DeployableMavenClassLoaderModelLoader(MavenClient mavenClient) {
    super(mavenClient);
  }

  @Override
  public String getId() {
    return MULE_LOADER_ID;
  }

  @Override
  protected void addArtifactSpecificClassloaderConfiguration(File artifactFile, ClassLoaderModelBuilder classLoaderModelBuilder,
                                                             Set<BundleDependency> dependencies) {
    try {
      classLoaderModelBuilder.containing(artifactFile.toURI().toURL());
      exportSharedLibrariesResourcesAndPackages(artifactFile, classLoaderModelBuilder, dependencies);
    } catch (MalformedURLException e) {
      throw new MuleRuntimeException(e);
    }
  }

  private Set<BundleDependency> getSharedLibrariesBundleDependencies(Xpp3Dom parent, File applicationFolder,
                                                                     Set<BundleDependency> dependencies) {
    Xpp3Dom sharedLibrariesDom = parent.getChild(SHARED_LIBRARIES_TAG);
    Set<BundleDependency> sharedLibrariesBundleDependencies = new HashSet<>();
    if (sharedLibrariesDom != null) {
      Xpp3Dom[] sharedLibraries = sharedLibrariesDom.getChildren(SHARED_LIBRARY_TAG);
      if (sharedLibraries != null) {
        for (Xpp3Dom sharedLibrary : sharedLibraries) {
          String groupId = getSharedLibraryAttribute(applicationFolder, sharedLibrary, "groupId");
          String artifactId = getSharedLibraryAttribute(applicationFolder, sharedLibrary, "artifactId");
          Optional<BundleDependency> bundleDependencyOptional = dependencies.stream()
              .filter(bundleDependency -> bundleDependency.getDescriptor().getArtifactId().equals(artifactId)
                  && bundleDependency.getDescriptor().getGroupId().equals(groupId))
              .findFirst();
          bundleDependencyOptional.map(bundleDependency -> {
            sharedLibrariesBundleDependencies.add(bundleDependency);
            return bundleDependency;
          }).orElseThrow(() -> new MuleRuntimeException(
                                                        createStaticMessage(format(
                                                                                   "Dependency %s:%s could not be found within the artifact %s. It must be declared within the maven dependencies of the artifact.",
                                                                                   groupId,
                                                                                   artifactId, applicationFolder.getName()))));
        }
      }
    }
    return sharedLibrariesBundleDependencies;
  }

  private void handleSharedLibraries(Object muleMavenPluginConfiguration, File applicationFolder,
                                     ClassLoaderModelBuilder classLoaderModelBuilder, Set<BundleDependency> dependencies) {
    Set<BundleDependency> sharedLibrariesBundleDependencies =
        getSharedLibrariesBundleDependencies((Xpp3Dom) muleMavenPluginConfiguration, applicationFolder, dependencies);
    FileJarExplorer fileJarExplorer = new FileJarExplorer();
    sharedLibrariesBundleDependencies.forEach(dep -> {
      JarInfo jarInfo = fileJarExplorer.explore(dep.getBundleUri());
      classLoaderModelBuilder.exportingPackages(jarInfo.getPackages());
      classLoaderModelBuilder.exportingResources(jarInfo.getResources());
    });
  }

  private void handlePluginExclusiveSharedLibraries(Object muleMavenPluginConfiguration, File applicationFolder,
                                                    ClassLoaderModelBuilder classLoaderModelBuilder,
                                                    Set<BundleDependency> dependencies) {
    Set<BundleDependency> pluginDepenedencies =
        dependencies.stream().filter(dep -> dep.getDescriptor().isPlugin()).collect(toSet());
    FileJarExplorer fileJarExplorer = new FileJarExplorer();
    Xpp3Dom pluginSharedLibrariesDom = ((Xpp3Dom) muleMavenPluginConfiguration).getChild(PLUGIN_SHARED_LIBRARIES_TAG);
    if (pluginSharedLibrariesDom != null) {
      Xpp3Dom[] plugins = pluginSharedLibrariesDom.getChildren(PLUGIN_TAG);
      if (plugins != null) {
        for (Xpp3Dom plugin : plugins) {
          Set<BundleDependency> pluginExclusiveSharedLibraries =
              getSharedLibrariesBundleDependencies(plugin, applicationFolder, pluginDepenedencies);
          pluginExclusiveSharedLibraries.forEach(
                                                 dep -> {
                                                   JarInfo jarInfo = fileJarExplorer.explore(dep.getBundleUri());
                                                   classLoaderModelBuilder.exportingPackagesForPlugin(dep, jarInfo.getPackages());
                                                 });
        }
      }
    }
  }

  private void exportSharedLibrariesResourcesAndPackages(File applicationFolder, ClassLoaderModelBuilder classLoaderModelBuilder,
                                                         Set<BundleDependency> dependencies) {
    Model model = loadPomModel(applicationFolder);
    Build build = model.getBuild();
    if (build != null) {
      List<Plugin> plugins = build.getPlugins();
      if (plugins != null) {
        Optional<Plugin> packagingPluginOptional =
            plugins.stream().filter(plugin -> plugin.getArtifactId().equals(MULE_MAVEN_PLUGIN_ARTIFACT_ID)
                && plugin.getGroupId().equals(MULE_MAVEN_PLUGIN_GROUP_ID)).findFirst();
        packagingPluginOptional.ifPresent(packagingPlugin -> {
          Object configuration = packagingPlugin.getConfiguration();
          if (configuration != null) {
            handleSharedLibraries(configuration, applicationFolder, classLoaderModelBuilder, dependencies);
            handlePluginExclusiveSharedLibraries(configuration, applicationFolder, classLoaderModelBuilder, dependencies);
          }
        });
      }
    }
  }

  private String getSharedLibraryAttribute(File applicationFolder, Xpp3Dom sharedLibraryDom, String attributeName) {
    Xpp3Dom attributeDom = sharedLibraryDom.getChild(attributeName);
    checkState(attributeDom != null,
               format("%s element was not defined within the shared libraries declared in the pom file of the artifact %s",
                      attributeName, applicationFolder.getName()));
    String attributeValue = attributeDom.getValue().trim();
    checkState(!isEmpty(attributeValue),
               format("%s was defined but has an empty value within the shared libraries declared in the pom file of the artifact %s",
                      attributeName, applicationFolder.getName()));
    return attributeValue;
  }

  @Override
  protected boolean includeTestDependencies(Map<String, Object> attributes) {
    return Boolean.valueOf((String) attributes.getOrDefault(INCLUDE_TEST_DEPENDENCIES, "false"));
  }

  @Override
  protected Model loadPomModel(File artifactFile) {
    return getPomModelFolder(artifactFile);
  }

  @Override
  protected boolean includeProvidedDependencies(ArtifactType artifactType) {
    return artifactType.equals(APP);
  }

  @Override
  public boolean supportsArtifactType(ArtifactType artifactType) {
    return artifactType.equals(APP) || artifactType.equals(DOMAIN) || artifactType.equals(POLICY);
  }
}
