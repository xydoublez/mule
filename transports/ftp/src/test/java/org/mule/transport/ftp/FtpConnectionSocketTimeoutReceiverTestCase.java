/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.ftp;


import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

import org.mule.api.construct.FlowConstruct;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.lifecycle.CreateException;
import org.mule.api.transport.Connector;
import org.mule.util.concurrent.Latch;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.ftpserver.ftplet.Ftplet;
import org.junit.Test;

public class FtpConnectionSocketTimeoutReceiverTestCase extends AbstractFtpConnectionSocketTimeoutTestCase
{

    private static Exception receiverException;

    private static Latch latch;

    public FtpConnectionSocketTimeoutReceiverTestCase(Ftplet ftpLet, String nameScenario)
    {
        super(ftpLet, nameScenario, "ftp-connection-timeout-receiver-config-flow.xml");
    }

    @Override
    public void doSetUp() throws Exception
    {
        receiverException = null;
        latch = new Latch();
        super.doSetUp();
    }

    @Test
    public void testReceiverTimeout() throws Exception
    {
        muleContext.start();
        latch.await();
        assertException(getRootCause(receiverException));
    }


    public static class TestFtpMessageReceiver extends FtpMessageReceiver
    {

        public TestFtpMessageReceiver(Connector connector, FlowConstruct flowConstruct, InboundEndpoint endpoint, long frequency) throws CreateException
        {
            super(connector, flowConstruct, endpoint, frequency);
        }

        @Override
        protected FTPFile[] listFiles() throws Exception
        {
            FTPFile[] files;
            try
            {
                files = super.listFiles();
                return files;
            }
            catch (Exception e)
            {
                receiverException = e;
                latch.countDown();
                throw new Exception("Wrapping exception for avoiding problems in mule context disposing.");
            }
        }
    }

}
