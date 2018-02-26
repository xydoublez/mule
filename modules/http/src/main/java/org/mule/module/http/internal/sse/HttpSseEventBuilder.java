/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.internal.sse;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Initialisable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.util.AttributeEvaluator;

public class HttpSseEventBuilder implements Initialisable, MuleContextAware
{

    private AttributeEvaluator data = new AttributeEvaluator(null);
    private AttributeEvaluator eventTpe = new AttributeEvaluator(null);
    
    private  MuleContext muleContext;
    
    public String evaluateData(MuleEvent event) {
        return data.resolveStringValue(event);
    }
    
    public String evaluateEventType(MuleEvent event) {
        return this.eventTpe.resolveStringValue(event);
    }
    
    @Override
    public void initialise() throws InitialisationException
    {
        data.initialize(muleContext.getExpressionManager());
        eventTpe.initialize(muleContext.getExpressionManager());
    }
    
    public String getData()
    {
        return data.getRawValue();
    }
    
    public void setData(String data)
    {
        this.data = new AttributeEvaluator(data);
    }
    
    public String getEventType()
    {
        return eventTpe.getRawValue();
    }
    
    public void setEventType(String eventType)
    {
        this.eventTpe = new AttributeEvaluator(eventType);
    }

    @Override
    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }
}
