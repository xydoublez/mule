/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.processor;

import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.mule.AbstractAnnotatedObject;
import org.mule.api.AnnotatedObject;
import org.mule.api.construct.FlowConstructAware;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.Lifecycle;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.processor.MessageProcessorContainer;
import org.mule.api.processor.MessageProcessorPathElement;
import org.mule.util.NotificationUtils;

/**
 * An object that owns message processors and delegates startup/shutdown events to them.
 */
public abstract class AbstractMessageProcessorOwner extends AbstractMuleObjectOwner<MessageProcessor> implements Lifecycle, MuleContextAware, FlowConstructAware, AnnotatedObject, MessageProcessorContainer
{
    private final AnnotatedObject annotatedObject = new MessageProcessorOwnerAnnotatedObject();

    public final Object getAnnotation(QName name)
    {
        return annotatedObject.getAnnotation(name);
    }

    public final Map<QName, Object> getAnnotations()
    {
        return annotatedObject.getAnnotations();
    }

    public synchronized final void setAnnotations(Map<QName, Object> newAnnotations)
    {
        annotatedObject.setAnnotations(newAnnotations);
    }

    protected List<MessageProcessor> getOwnedObjects()
    {
        return getOwnedMessageProcessors();
    }

    protected abstract List<MessageProcessor> getOwnedMessageProcessors();

    @Override
    public void addMessageProcessorPathElements(MessageProcessorPathElement pathElement)
    {
        NotificationUtils.addMessageProcessorPathElements(getOwnedMessageProcessors(), pathElement);
    }

    private static class MessageProcessorOwnerAnnotatedObject extends AbstractAnnotatedObject
    {
    }
}

