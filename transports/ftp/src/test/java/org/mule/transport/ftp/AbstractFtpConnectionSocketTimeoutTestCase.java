/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.ftp;


import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mule.tck.AbstractServiceAndFlowTestCase.ConfigVariant.FLOW;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.util.concurrent.Latch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class AbstractFtpConnectionSocketTimeoutTestCase extends AbstractFtpServerTestCase
{

    protected static String CONNECTION_TIMEOUT = "1000";

    private static String NOOP_COMMAND = "NOOP";

    private static String ERROR_MESSAGE = "Read timed out";

    @Rule
    public SystemProperty systemProperty = new SystemProperty("connectionTimeout", CONNECTION_TIMEOUT);

    private Ftplet ftplet;

    private String nameScenario;

    private static Latch serverLatch;


    public AbstractFtpConnectionSocketTimeoutTestCase(Ftplet ftpLet, String nameScenario, String configResource)
    {
        super(FLOW, configResource);
        setStartContext(false);
        this.ftplet = ftpLet;
        this.nameScenario = nameScenario;
    }

    @Override
    protected void doSetUp() throws Exception
    {
        serverLatch = new Latch();
        super.doSetUp();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data()
    {
        List<Object[]> parameters = new ArrayList<>();
        parameters.add(new Object[] { ftpLetOnConnectSleep, "Connection Scenario"});
        parameters.add(new Object[] { ftpLetOnCommandSleep, "Connection Commands Scenario"});
        parameters.add(new Object[] { ftpLetOnNoOpCommandSleep, "NOOP Commands Scenario"});
        return parameters;
    }



    protected void assertException(Throwable exception)
    {
        assertThat("An timeout exception should be triggered in the " + nameScenario, exception, is(notNullValue()));
        assertThat("SocketTimeoutException should be triggered in the " + nameScenario, exception.getMessage(), containsString(ERROR_MESSAGE));
    }

    private static Ftplet ftpLetOnCommandSleep = new DefaultFtplet()
    {
        @Override
        public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException
        {
            sleep();
            return null;
        }
    };


    private static Ftplet ftpLetOnNoOpCommandSleep = new DefaultFtplet()
    {
        @Override
        public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException
        {
            if (NOOP_COMMAND.equals(request.getCommand()))
            {
                sleep();
            }
            return null;
        }
    };

    private static Ftplet ftpLetOnConnectSleep = new DefaultFtplet()
    {
        @Override
        public FtpletResult onConnect(FtpSession session) throws FtpException, IOException
        {
            sleep();
            return null;
        }
    };

    private static void sleep()
    {
        try
        {
            serverLatch.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted exception was triggered", e);
        }
    }

    @Override
    protected void doTearDown() throws Exception
    {
        serverLatch.countDown();
        super.doTearDown();
    }

    @Override
    protected Ftplet createFtpLet()
    {
        return ftplet;
    }

    @Override
    protected boolean testServerConnection()
    {
        return false;
    }
}
