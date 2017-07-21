/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.impl.internal.artifact;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_CONNECTION_MANAGER;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.ConfigurationBuilder;
import org.mule.runtime.core.api.config.ConfigurationException;
import org.mule.runtime.core.api.registry.RegistrationException;
import org.mule.runtime.core.internal.connection.CompositeConnectionManager;
import org.mule.runtime.core.internal.connection.DefaultConnectionManager;
import org.mule.runtime.deployment.model.api.DeployableArtifact;

/**
 * {@link ConfigurationBuilder}
 *
 * @since 4.0
 */
public class ConnectionManagerConfigurationBuilder implements ConfigurationBuilder {

  private DeployableArtifact parentArtifact;
  private boolean isConfigured = false;

  ConnectionManagerConfigurationBuilder(DeployableArtifact parentArtifact) {
    this.parentArtifact = parentArtifact;
  }

  @Override
  public void configure(MuleContext muleContext) throws ConfigurationException {
    try {
      if (parentArtifact == null) {
        muleContext.getRegistry().registerObject(OBJECT_CONNECTION_MANAGER, new DefaultConnectionManager(muleContext));
      } else {
        muleContext.getRegistry().registerObject(OBJECT_CONNECTION_MANAGER,
                                                 new CompositeConnectionManager(new DefaultConnectionManager(muleContext),
                                                                                parentArtifact.getMuleContext()
                                                                                    .getRegistry()
                                                                                    .get(OBJECT_CONNECTION_MANAGER)));
      }
      isConfigured = true;
    } catch (RegistrationException e) {
      throw new ConfigurationException(createStaticMessage("An error occurred trying to register the Mule Connection Manager"),
                                       e);
    }
  }

  @Override
  public boolean isConfigured() {
    return isConfigured;
  }
}
