/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.internal.sse;

public interface SseConstants
{

    int DEFAULT_RECONNECTION_TIME_MILLIS = 3000;
    String LAST_EVENT_ID_KEY = "Last-Event-ID";
    String TEXT_EVENT_STREAM_MEDIA_TYPE = "text/event-stream";

}
