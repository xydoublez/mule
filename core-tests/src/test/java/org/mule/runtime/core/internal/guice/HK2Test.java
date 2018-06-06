/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.guice;

import static org.glassfish.hk2.utilities.BuilderHelper.link;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.testmodels.fruit.Apple;
import org.mule.tck.testmodels.fruit.Banana;
import org.mule.tck.testmodels.fruit.Fruit;


import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.junit.Before;
import org.junit.Test;

public class HK2Test extends AbstractMuleTestCase {

  ServiceLocator locator;

  @Before
  public void before() {
    ServiceLocatorFactory factory = ServiceLocatorFactory.getInstance();

    locator = factory.create("HelloWorld");

    DynamicConfigurationService dcs = locator.getService(DynamicConfigurationService.class);
    DynamicConfiguration config = dcs.createDynamicConfiguration();

    config.

    config.bind(link(FruitShop.class).in(Singleton.class).build());
    config.bind(link(Apple.class).in(Singleton.class).build());
    config.bind((link(Banana.class).to(BananaFactory.class)).build());

    config.commit();
  }

  @Test
  public void scopes() {

    FruitShop fs1 = locator.getService(FruitShop.class);
    FruitShop fs2 = locator.getService(FruitShop.class);

    assertThat(fs1, is(sameInstance(fs2)));

    assertThat(fs1.getApple(), is(sameInstance(fs2.getApple())));

    assertThat(locator.getService(Banana.class), is(not(sameInstance(locator.getService(Banana.class)))));
  }

  @Test
  public void lookupByParent() {
    List<ServiceHandle<?>> fruits = locator.getAllServiceHandles(BuilderHelper.createContractFilter(Fruit.class.getName()));
    assertThat(fruits, hasSize(2));
  }

    public static class FruitShop {

      @Inject
      private Apple apple;

      public Apple getApple() {
        return apple;
      }
    }


  private class BananaFactory implements Factory<Banana> {

    @Override
    public Banana provide() {
      return new Banana();
    }

    @Override
    public void dispose(Banana instance) {

    }
  }
}
