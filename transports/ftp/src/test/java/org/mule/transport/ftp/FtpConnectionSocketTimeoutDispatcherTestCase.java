/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.ftp;


import static java.lang.Long.parseLong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.client.SimpleOptions;
import java.util.HashMap;

import org.apache.ftpserver.ftplet.Ftplet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FtpConnectionSocketTimeoutDispatcherTestCase extends AbstractFtpConnectionSocketTimeoutTestCase
{

    private static int TEST_DELTA = 500;

    private static long WAIT_TIMEOUT = parseLong(CONNECTION_TIMEOUT) + TEST_DELTA;


    public FtpConnectionSocketTimeoutDispatcherTestCase(Ftplet ftpLet, String nameScenario)
    {
        super(ftpLet, nameScenario, "ftp-connection-timeout-dispatcher-config-flow.xml");
    }

    @Test
    public void testDispatcherTimeoutConnection() throws Exception
    {
        muleContext.start();
        MuleClient client = muleContext.getClient();
        MuleMessage testMessage = new DefaultMuleMessage("test", new HashMap(), muleContext);
        MuleMessage result = client.send("vm://in", testMessage, new SimpleOptions(WAIT_TIMEOUT));
        assertThat(result, is(notNullValue()));
        assertException(result.getExceptionPayload().getRootException());
    }

}
