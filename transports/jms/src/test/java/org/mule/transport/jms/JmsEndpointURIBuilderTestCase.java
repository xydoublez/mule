/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.jms;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.mule.api.endpoint.EndpointURI;

public class JmsEndpointURIBuilderTestCase
{
    @Test
    public void testWithFullyQualifiedQueueName() throws Exception
    {
        JmsEndpointURIBuilder b = new JmsEndpointURIBuilder();
        EndpointURI u =  b.build(new URI("jms://address::queue1"), null);
        Assert.assertEquals("address::queue1", u.getAddress());
    }
}