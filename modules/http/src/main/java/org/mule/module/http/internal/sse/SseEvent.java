/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.internal.sse;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.lineSeparator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.Writer;

final class SseEvent implements Serializable
{
    private static final long serialVersionUID = 191118921198317316L;

    private long timestamp;
    private String id;
    private String eventType;
    private String value;

    public SseEvent(String id, String eventType, String value)
    {
        this.timestamp = currentTimeMillis();
        this.id = id;
        this.eventType = eventType;
        this.value = value;
    }
    
    public long getTimestamp()
    {
        return timestamp;
    }

    public void write(Writer writer) throws IOException
    {
        writer.write("id: " + id + lineSeparator());
        if (eventType != null)
        {
            writer.write("event: " + eventType + lineSeparator());
        }

        BufferedReader reader = new BufferedReader(new StringReader(value));
        String line = reader.readLine();
        while (line != null)
        {
            writer.write("data: " + line + lineSeparator());
            line = reader.readLine();
        }
        writer.write(lineSeparator());
    }
    
}