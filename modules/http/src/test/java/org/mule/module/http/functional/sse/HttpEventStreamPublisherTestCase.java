/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.functional.sse;

import static java.lang.System.lineSeparator;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.api.transport.PropertyScope.INBOUND;
import static org.mule.module.http.internal.sse.DefaultHttpEventStreamListener.RECONNECTION_TIME_KEY;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.api.store.ObjectStore;
import org.mule.module.http.internal.sse.SseConstants;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.util.store.MuleObjectStoreManager;

public class HttpEventStreamPublisherTestCase extends FunctionalTestCase
{
    @Rule
    public DynamicPort serverPort = new DynamicPort("serverPort");
    
    private MuleClient client;
    
    private ObjectStore<? extends Serializable> consumerObjectStore;

    @Before
    public void before() throws Exception
    {
        client = muleContext.getClient();
        consumerObjectStore = ((MuleObjectStoreManager)muleContext.getObjectStoreManager()).getUserObjectStore("event-stream-listener-sseConsumer", true);
    }

    @Override
    protected String getConfigFile()
    {
        return "http-event-stream-publisher-config.xml";
    }
    
    @Test
    public void cleanState() throws Exception
    {
        assertThat(consumerObjectStore.contains(RECONNECTION_TIME_KEY), is(false));
        startIfNeeded(muleContext.getRegistry().lookupFlowConstruct("sseConsumer"));

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), nullValue());
        assertThat((Long)consumerObjectStore.retrieve(RECONNECTION_TIME_KEY), is(Long.valueOf(10000L)));
    }

    @Test
    public void publishDefault() throws Exception
    {
        assertThat(consumerObjectStore.contains(SseConstants.LAST_EVENT_ID_KEY), is(false));
        startIfNeeded(muleContext.getRegistry().lookupFlowConstruct("sseConsumer"));

        runFlow("publishDefault", getTestEvent(TEST_MESSAGE));

        MuleMessage event = client.request("vm://out", RECEIVE_TIMEOUT);
        assertThat(event.getPayloadAsString(), is(TEST_MESSAGE));
        assertThat(event.getInboundProperty("event"), nullValue());

        assertThat((String)consumerObjectStore.retrieve(SseConstants.LAST_EVENT_ID_KEY), is("0"));

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), nullValue());
    }
    
    @Test
    public void publishDefaultMultiline() throws Exception
    {
        startIfNeeded(muleContext.getRegistry().lookupFlowConstruct("sseConsumer"));

        runFlow("publishDefault", getTestEvent(TEST_MESSAGE + lineSeparator() + TEST_MESSAGE));

        MuleMessage event = client.request("vm://out", RECEIVE_TIMEOUT);
        assertThat(event.getPayloadAsString(), is(TEST_MESSAGE + lineSeparator() + TEST_MESSAGE));
        assertThat((String)event.getInboundProperty("id"), is("0"));
        assertThat(event.getInboundProperty("event"), nullValue());

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), nullValue());
    }
    
    @Test
    public void publishCustom() throws Exception
    {
        startIfNeeded(muleContext.getRegistry().lookupFlowConstruct("sseConsumer"));

        MuleEvent eventOut = getTestEvent("");
        Map<String, Object> props = new HashMap<>();
        props.put("data", TEST_MESSAGE);
        props.put("event", "push");
        eventOut.getMessage().addProperties(props, INBOUND);
        eventOut.getMessage().setPayload("Don't see me!");
        
        runFlow("publishCustom", eventOut);

        MuleMessage eventIn = client.request("vm://out", RECEIVE_TIMEOUT);
        assertThat(eventIn.getPayloadAsString(), is(TEST_MESSAGE));
        assertThat((String)eventIn.getInboundProperty("id"), is("0"));
        assertThat((String)eventIn.getInboundProperty("event"), is("push"));

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), nullValue());
    }
    
    @Test
    public void publishAfterSomeTime() throws Exception
    {
        startIfNeeded(muleContext.getRegistry().lookupFlowConstruct("sseConsumer"));
        
        MuleEvent eventOut = getTestEvent("");
        Map<String, Object> props = new HashMap<>();
        props.put("data", TEST_MESSAGE);
        props.put("event", "push");
        eventOut.getMessage().addProperties(props, INBOUND);
        eventOut.getMessage().setPayload("Don't see me!");
        
        runFlow("publishCustom", eventOut);
        
        MuleMessage eventIn = client.request("vm://out", RECEIVE_TIMEOUT);
        assertThat(eventIn.getPayloadAsString(), is(TEST_MESSAGE));
        assertThat((String)eventIn.getInboundProperty("id"), is("0"));
        assertThat((String)eventIn.getInboundProperty("event"), is("push"));
        
        // This time is greater than the response timeout of the consumer
        Thread.sleep(25000);
        runFlow("publishCustom", eventOut);

        assertThat(client.request("vm://out", RECEIVE_TIMEOUT), not(nullValue()));
    }
    
}
