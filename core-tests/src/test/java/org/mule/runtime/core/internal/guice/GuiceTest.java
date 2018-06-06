/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.guice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.testmodels.fruit.Apple;
import org.mule.tck.testmodels.fruit.Banana;
import org.mule.tck.testmodels.fruit.Fruit;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import java.util.Set;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;

public class GuiceTest extends AbstractMuleTestCase {

  Injector injector;

  @Before
  public void before() {
    injector = Guice.createInjector(new FruitShopModule());
  }

  @Test
  public void scopes() {

    FruitShop fs1 = injector.getInstance(FruitShop.class);
    FruitShop fs2 = injector.getInstance(FruitShop.class);

    assertThat(fs1, is(sameInstance(fs2)));

    assertThat(fs1.getApple(), is(sameInstance(fs2.getApple())));

    assertThat(injector.getInstance(Banana.class), is(not(sameInstance(injector.getInstance(Banana.class)))));
  }

  @Test
  public void lookupByParent() {
    injector.getAllBindings().values().forEach(b -> b.getProvider());
    Set<Fruit> fruits = injector.getInstance(Key.get(new TypeLiteral<Set<Fruit>>() {}));
    assertThat(fruits, hasSize(2));
  }

  @Test
  public void graph() {
  }


  public class FruitShopModule extends AbstractModule {

    @Override
    protected void configure() {
      bind(FruitShop.class).in(Singleton.class);
      Multibinder<Fruit> multibinder = Multibinder.newSetBinder(binder(), Fruit.class);
      multibinder.addBinding().to(Apple.class).in(Singleton.class);
      multibinder.addBinding().toProvider(Banana::new);
    }
  }

  public static class FruitShop {

    @Inject
    private Apple apple;

    public Apple getApple() {
      return apple;
    }
  }


}
