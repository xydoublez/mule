/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.integration.sse;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

public class JmsSseBridgeTestCase extends FunctionalTestCase
{

    @Rule
    public DynamicPort httpServerPort = new DynamicPort("httpServerPort");

    private MuleClient client;
    
    @Override
    protected String getConfigFile()
    {
        return "org/mule/test/integration/sse/jms-sse-brigde.xml";
    }
    
    @Before
    public void before()
    {
        client = muleContext.getClient();
    }
    
    @Test
    public void doTest() throws MuleException {
        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), nullValue());

        MuleMessage result = client.send("jms://sse.bridge", TEST_PAYLOAD, null);

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT).getPayload().toString(), is(TEST_PAYLOAD));
    }
}
