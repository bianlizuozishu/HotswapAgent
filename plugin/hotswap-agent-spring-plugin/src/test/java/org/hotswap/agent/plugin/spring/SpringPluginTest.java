/*
 * Copyright 2013-2019 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.inject.Inject;

import org.hotswap.agent.plugin.spring.scanner.XmlBeanDefinitionScannerAgent;
import org.hotswap.agent.plugin.spring.testBeans.BeanPrototype;
import org.hotswap.agent.plugin.spring.testBeans.BeanRepository;
import org.hotswap.agent.plugin.spring.testBeans.BeanService;
import org.hotswap.agent.plugin.spring.testBeans.BeanServiceImpl;
import org.hotswap.agent.plugin.spring.testBeans.Pojo;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanPrototype2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanRepository2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.BeanServiceImpl2;
import org.hotswap.agent.plugin.spring.testBeansHotswap.Pojo2;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.spring.io.resource.ClassPathResource;
import org.hotswap.agent.util.spring.io.resource.Resource;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Hotswap class files of spring beans.
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Jiri Bubnik
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:applicationContext.xml" })
public class SpringPluginTest {

    @Autowired
    ApplicationContext applicationContext;

    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    /**
     * Check correct setup.
     */
    @Test
    public void basicTest() {
        assertEquals("Hello from Repository ServiceWithAspect", applicationContext.getBean(BeanService.class).hello());
        assertEquals("Hello from Repository ServiceWithAspect Prototype",
                applicationContext.getBean(BeanPrototype.class).hello());
    }

    /**
     * Switch method implementation (using bean definition or interface). Injection
     * of {@link Autowired @Autowired} dependency is tested as well.
     */
    @Test
    public void hotswapServiceTest() throws Exception {
        BeanServiceImpl bean = applicationContext.getBean(BeanServiceImpl.class);
        assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class);
        assertEquals("Hello from ChangedRepository Service2WithAspect", bean.hello());
        // ensure that using interface is Ok as well
        assertEquals("Hello from ChangedRepository Service2WithAspect",
                applicationContext.getBean(BeanService.class).hello());
    }

    /**
     * Add new method - invoke via reflection (not available at compilation time).
     */
    @Test
    public void hotswapServiceAddMethodTest() throws Exception {
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class);

        // get bean via interface
        String helloNewMethodIfaceVal = (String) ReflectionHelper.invoke(applicationContext.getBean(BeanService.class),
                BeanServiceImpl.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod Service2", helloNewMethodIfaceVal);

        // get bean via implementation
        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(
                applicationContext.getBean(BeanServiceImpl.class), BeanServiceImpl.class, "helloNewMethod",
                new Class[] {});
        assertEquals("Hello from helloNewMethod Service2", helloNewMethodImplVal);
    }

    /**
     * Make sure added field with {@link Inject} annotation is injected
     */
    @Test
    public void hotswapServiceAddFieldWithInject() throws Exception {
        assertEquals("injected:true", applicationContext.getBean(BeanService.class).isInjectFieldInjected());
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class);
        assertEquals("injectedChanged:true", applicationContext.getBean(BeanService.class).isInjectFieldInjected());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {
        BeanServiceImpl bean = applicationContext.getBean(BeanServiceImpl.class);
        assertEquals("Hello from Repository ServiceWithAspect", bean.hello());
        swapClasses(BeanRepository.class, BeanRepository2.class);
        assertEquals("Hello from ChangedRepository2 ServiceWithAspect", bean.hello());
    }

    @Test
    public void hotswapRepositoryNewMethodTest() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect",
                applicationContext.getBean(BeanServiceImpl.class).hello());

        swapClasses(BeanRepository.class, BeanRepository2.class);

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(
                applicationContext.getBean("beanRepository", BeanRepository.class), BeanRepository.class,
                "helloNewMethod", new Class[] {});
        assertEquals("Repository new method", helloNewMethodImplVal);
    }

    @Test
    public void hotswapPrototypeTestNewInstance() throws Exception {
        assertEquals("Hello from Repository ServiceWithAspect Prototype",
                applicationContext.getBean(BeanPrototype.class).hello());

        // swap service this prototype is dependent to
        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class);
        assertEquals("Hello from ChangedRepository Service2WithAspect Prototype",
                applicationContext.getBean(BeanPrototype.class).hello());

        // swap prototype. New prototype depends directly on repository instead of
        // service.
        swapClasses(BeanPrototype.class, BeanPrototype2.class);
        assertEquals("Hello from Repository Prototype2", applicationContext.getBean(BeanPrototype.class).hello());
    }

    @Test
    public void hotswapPrototypeTestExistingInstance() throws Exception {
        BeanPrototype beanPrototypeInstance = applicationContext.getBean(BeanPrototype.class);
        assertEquals("Hello from Repository ServiceWithAspect Prototype", beanPrototypeInstance.hello());

        swapClasses(BeanServiceImpl.class, BeanServiceImpl2.class);
        assertEquals("Hello from ChangedRepository Service2WithAspect Prototype", beanPrototypeInstance.hello());
    }

    @Test
    public void pojoTest() throws Exception {
        Pojo pojo = new Pojo();
        assertEquals(0, applicationContext.getBeanNamesForType(Pojo.class).length);
        swapClasses(Pojo.class, Pojo2.class);
        assertEquals(0, applicationContext.getBeanNamesForType(Pojo.class).length);
    }

    private void swapClasses(Class<?> original, Class<?> swap) throws Exception {
        swappingRule.swapClasses(original, swap);
    }

    private static ApplicationContext xmlApplicationContext;
    private static Resource xmlContext = new ClassPathResource("xmlContext.xml");
    private static Resource xmlContextWithRepo = new ClassPathResource("xmlContextWithRepository.xml");
    private static Resource xmlContextWithChangedRepo = new ClassPathResource("xmlContextWithChangedRepository.xml");

    /*
     * -------Xml test, has to be put in there so xmlContext is load after component
     * scan context----------- (because we don't support multiple component-scan
     * context in single app, or ClassPathBeanDefinitionScannerAgent will not be
     * able to get the right registry to use (it will only use the first one it
     * encountered, Spring tends to reuse ClassPathScanner))
     */
    @Before
    public void before() throws IOException {
        if (xmlApplicationContext == null) {
            writeRepositoryToXml();
            xmlApplicationContext = new ClassPathXmlApplicationContext("xmlContext.xml");
        }
    }

    private void writeRepositoryToXml() throws IOException {
        Files.copy(xmlContextWithRepo.getFile().toPath(), xmlContext.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeChangedRepositoryToXml() throws IOException {
        Files.copy(xmlContextWithChangedRepo.getFile().toPath(), xmlContext.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void swapXmlTest() throws IOException {
        BeanService beanService = xmlApplicationContext.getBean("beanService", BeanService.class);
        Assert.assertEquals(beanService.hello(), "Hello from Repository ServiceWithAspect");

        XmlBeanDefinitionScannerAgent.reloadFlag = true;
        writeChangedRepositoryToXml();
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !XmlBeanDefinitionScannerAgent.reloadFlag;
            }
        }, 5000));

        Assert.assertEquals(beanService.hello(), "Hello from ChangedRepository ServiceWithAspect");

        XmlBeanDefinitionScannerAgent.reloadFlag = true;
        writeRepositoryToXml();
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !XmlBeanDefinitionScannerAgent.reloadFlag;
            }
        }, 5000));
        Assert.assertEquals(beanService.hello(), "Hello from Repository ServiceWithAspect");
    }
}
