/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.context.MuleContextAware;
import org.mule.runtime.core.internal.context.MuleContextWithRegistry;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.junit.Test;

public class RegistryLifecycleTestCase extends AbstractMuleContextTestCase {

  private static final String LIFECYCLE_PHASES = "[setMuleContext, initialise, start, stop, dispose]";

  @Override
  protected Map<String, Object> getStartUpRegistryObjects() {
    Map<String, Object> objects = new HashMap<>();

    objects.put(InterfaceBasedTracker.class.getSimpleName(), new InterfaceBasedTracker());
    objects.put(JSR250ObjectLifecycleTracker.class.getSimpleName(), new JSR250ObjectLifecycleTracker());

    return objects;
  }

  @Test
  public void testObjectLifecycle() throws Exception {
    muleContext.start();

    InterfaceBasedTracker tracker = getInterfaceBasedTracker();

    muleContext.dispose();
    assertEquals(LIFECYCLE_PHASES, tracker.getTracker().toString());
  }

  @Test
  public void testJSR250ObjectLifecycle() throws Exception {
    muleContext.start();

    JSR250ObjectLifecycleTracker tracker = getRegistry().get(JSR250ObjectLifecycleTracker.class.getSimpleName());

    muleContext.dispose();
    assertEquals("[setMuleContext, initialise, dispose]", tracker.getTracker().toString());
  }

  @Test
  public void testObjectLifecycleStates() throws Exception {
    final InterfaceBasedTracker tracker = getInterfaceBasedTracker();
    assertEquals("[setMuleContext, initialise]", tracker.getTracker().toString());

    try {
      muleContext.initialise();
      fail("context already initialised");
    } catch (IllegalStateException e) {
      // expected
    }

    muleContext.start();
    assertEquals("[setMuleContext, initialise, start]", tracker.getTracker().toString());

    try {
      muleContext.start();
      fail("context already started");
    } catch (IllegalStateException e) {
      // expected
    }

    muleContext.stop();
    assertEquals("[setMuleContext, initialise, start, stop]", tracker.getTracker().toString());

    try {
      muleContext.stop();
      fail("context already stopped");
    } catch (IllegalStateException e) {
      // expected
    }

    muleContext.dispose();
    assertEquals(LIFECYCLE_PHASES, tracker.getTracker().toString());

    try {
      muleContext.dispose();
      fail("context already disposed");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  private InterfaceBasedTracker getInterfaceBasedTracker() {
    return getRegistry().get(InterfaceBasedTracker.class.getSimpleName());
  }

  @Test
  public void testObjectLifecycleRestart() throws Exception {
    InterfaceBasedTracker tracker = getInterfaceBasedTracker();

    muleContext.start();
    assertEquals("[setMuleContext, initialise, start]", tracker.getTracker().toString());

    muleContext.stop();
    assertEquals("[setMuleContext, initialise, start, stop]", tracker.getTracker().toString());

    muleContext.start();
    assertEquals("[setMuleContext, initialise, start, stop, start]", tracker.getTracker().toString());

    muleContext.dispose();
    assertEquals("[setMuleContext, initialise, start, stop, start, stop, dispose]", tracker.getTracker().toString());
  }

  private MuleRegistry getRegistry() {
    return ((MuleContextWithRegistry) muleContext).getRegistry();
  }

  public class InterfaceBasedTracker extends AbstractLifecycleTracker {
    // no custom methods
  }


  public class JSR250ObjectLifecycleTracker implements MuleContextAware {

    private final List<String> tracker = new ArrayList<>();

    public List<String> getTracker() {
      return tracker;
    }

    @Override
    public void setMuleContext(MuleContext context) {
      tracker.add("setMuleContext");
    }

    @PostConstruct
    public void init() {
      tracker.add("initialise");
    }

    @PreDestroy
    public void dispose() {
      tracker.add("dispose");
    }
  }
}
