/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.mule.runtime.api.artifact.semantic.Artifact;
import org.mule.runtime.api.artifact.semantic.Component;
import org.mule.runtime.api.artifact.sintax.ArtifactDefinition;
import org.mule.runtime.api.artifact.sintax.ComponentDefinition;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.config.internal.dsl.model.ExtensionsHelper;

public class ArtifactModelFactory
{

    private final Set<ExtensionModel> extensionsModels;
    private final ExtensionsHelper extensionsHelper;

    public ArtifactModelFactory(Set<ExtensionModel> extensionModels) {
        this.extensionsModels = extensionModels;
        this.extensionsHelper = new ExtensionsHelper(extensionModels);
    }

    public Artifact createFrom(ArtifactDefinition artifactDefinition) {
        Artifact.ArtifactBuilder artifactBuilder = Artifact.builder().withArtifactDefinition(Optional.of(artifactDefinition));

        return artifactBuilder.withGlobalComponents(createGlobalComponents(artifactDefinition)).build();
    }

    private List<Component> createGlobalComponents(ArtifactDefinition artifactDefinition)
    {
        List<ComponentDefinition> globalDefinitions = artifactDefinition.getGlobalDefinitions();

        globalDefinitions.stream().forEach(componentDefinition -> {
            Object model = extensionsHelper.findModel(componentDefinition.getIdentifier());
        });
        return null;
    }

}
