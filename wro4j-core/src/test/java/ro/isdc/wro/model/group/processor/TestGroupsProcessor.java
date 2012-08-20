/*
 * Copyright (c) 2010. All rights reserved.
 */
package ro.isdc.wro.model.group.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import junit.framework.Assert;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.cache.CacheEntry;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.manager.factory.BaseWroManagerFactory;
import ro.isdc.wro.manager.factory.WroManagerFactory;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.locator.ResourceLocator;
import ro.isdc.wro.model.resource.locator.factory.AbstractResourceLocatorFactory;
import ro.isdc.wro.model.resource.locator.factory.ResourceLocatorFactory;
import ro.isdc.wro.model.resource.locator.support.AbstractResourceLocator;
import ro.isdc.wro.model.resource.processor.ResourceProcessor;
import ro.isdc.wro.model.resource.processor.decorator.ProcessorDecorator;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.processor.factory.SimpleProcessorsFactory;
import ro.isdc.wro.util.WroTestUtils;


/**
 * TestGroupsProcessor.
 * 
 * @author Alex Objelean
 * @created Created on Jan 5, 2010
 */
public class TestGroupsProcessor {
  private GroupsProcessor victim;
  final String groupName = "group";
  
  @Before
  public void setUp() {
    Context.set(Context.standaloneContext());
    victim = new GroupsProcessor();
    initVictim(new WroConfiguration());
  }
  
  @After
  public void tearDown() {
    Context.unset();
  }
  
  private void initVictim(final WroConfiguration config) {
    final WroModelFactory modelFactory = WroTestUtils.simpleModelFactory(new WroModel().addGroup(new Group(groupName)));
    final WroManagerFactory managerFactory = new BaseWroManagerFactory().setModelFactory(modelFactory);
    initVictim(config, managerFactory);
  }
  
  private void initVictim(final WroConfiguration config, final WroManagerFactory managerFactory) {
    Context.set(Context.standaloneContext(), config);
    final Injector injector = InjectorBuilder.create(managerFactory).build();
    injector.inject(victim);
  }
  
  @Test
  public void shouldReturnEmptyStringWhenGroupHasNoResources() {
    final CacheEntry key = new CacheEntry(groupName, ResourceType.JS, true);
    Assert.assertEquals(StringUtils.EMPTY, victim.process(key));
  }
  
  /**
   * Same as above, but with ignoreEmptyGroup config updated.
   */
  @Test(expected = WroRuntimeException.class)
  public void shouldFailWhenGroupHasNoResourcesAndIgnoreEmptyGroupIsFalse() {
    WroConfiguration config = new WroConfiguration();
    config.setIgnoreEmptyGroup(false);
    initVictim(config);
    final CacheEntry key = new CacheEntry("group", ResourceType.JS, true);
    victim.process(key);
  }
  
  @Test
  public void shouldLeaveContentUnchangedWhenAProcessorFails() {
    final CacheEntry key = new CacheEntry(groupName, ResourceType.JS, true);
    final Group group = new Group(groupName).addResource(Resource.create("1.js")).addResource(Resource.create("2.js"));
    final WroModelFactory modelFactory = WroTestUtils.simpleModelFactory(new WroModel().addGroup(group));
    // the locator which returns the name of the resource as its content
    final ResourceLocatorFactory locatorFactory = new AbstractResourceLocatorFactory() {
      public ResourceLocator getLocator(final String uri) {
        return new AbstractResourceLocator() {
          public InputStream getInputStream()
              throws IOException {
            return new ByteArrayInputStream(uri.getBytes());
          }
        };
      }
    };
    
    final ResourceProcessor failingPreProcessor = new ResourceProcessor() {
      public void process(final Resource resource, final Reader reader, final Writer writer)
          throws IOException {
        throw new IOException("BOOM!");
      }
    };
    final ProcessorsFactory processorsFactory = new SimpleProcessorsFactory().addPreProcessor(failingPreProcessor).addPostProcessor(
        new ProcessorDecorator(failingPreProcessor));
    final BaseWroManagerFactory managerFactory = new BaseWroManagerFactory().setModelFactory(modelFactory).setLocatorFactory(
        locatorFactory);
    managerFactory.setProcessorsFactory(processorsFactory);
    
    final WroConfiguration config = new WroConfiguration();
    config.setIgnoreFailingProcessor(true);
    initVictim(config, managerFactory);
    
    final String actual = victim.process(key);
    Assert.assertEquals("1.js2.js", actual);
  }
}
