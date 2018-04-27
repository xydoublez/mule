/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.module.extension.connector;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mule.test.petstore.extension.FailingPetStoreSource.connectionException;
import static org.mule.test.petstore.extension.FailingPetStoreSource.executor;
import org.mule.runtime.core.api.retry.policy.RetryPolicyExhaustedException;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.test.module.extension.AbstractExtensionFunctionalTestCase;
import org.mule.test.petstore.extension.PetStoreConnector;
import org.mule.test.runner.RunnerDelegateTo;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runners.Parameterized;

@RunnerDelegateTo(Parameterized.class)
public class PetStoreSourceRetryPolicyFailsDeploymentTestCase extends AbstractExtensionFunctionalTestCase {

  public static final int TIMEOUT_MILLIS = 1000;
  public static final int POLL_DELAY_MILLIS = 50;

  @Rule
  public ExpectedException exception = none();

  @Rule
  public SystemProperty team = new SystemProperty("initialState", "started");

  @Parameterized.Parameter(0)
  public boolean failsDeployment;

  @Parameterized.Parameter(1)
  public String connectionConfig;


  @Parameterized.Parameters(name = "{1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {false, "petstore-connection-dont-fail-deployment.xml"},
                         new Object[] {true, "petstore-connection-fail-deployment.xml"});
  }

  @Override
  protected String[] getConfigFiles() {
    return new String[] {connectionConfig, "petstore-source-retry-policy.xml"};
  }

  @Before
  public void setUp() throws Exception {
    PetStoreConnector.timesStarted = new HashMap<>();
  }

  @After
  public void tearDown() {
    PetStoreConnector.timesStarted = new HashMap<>();
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    if (failsDeployment) {
      exception.expect(RetryPolicyExhaustedException.class);
      exception.expectCause(sameInstance(connectionException));
    } else {
      exception = none();
    }
  }

  @Test
  public void retryPolicySourceFailOnStart() throws Exception {
    new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS)
        .check(new JUnitLambdaProbe(() -> {
          assertThat(PetStoreConnector.timesStarted.get("source-fail-on-start"), is(2));
          return true;
        }));
  }

  @Test
  public void retryPolicySourceFailWithConnectionException() throws Exception {
    new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS)
        .check(new JUnitLambdaProbe(() -> {
          assertThat(PetStoreConnector.timesStarted.get("source-fail-with-connection-exception"), is(3));
          return true;
        }));
  }

}
