package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.taghandling.AliveWriter;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagChanger;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
public class SpringScopeTest {

    @Autowired
    ApplicationContext ctx;

    @Test
    public final void whenRegisterScopeAndBeans_thenContextContainsFooAndBar() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(EquipmentScopeConfig.class);
            ctx.refresh();

            TagSubscriptionMapper foo = (TagSubscriptionMapper) ctx.getBean(TagSubscriptionManager.class);
            foo.sayHello();
            TagSubscriptionMapper bar = (TagSubscriptionMapper) ctx.getBean(TagSubscriptionReader.class);
            bar.sayHello();

            MessageSender aliveWriter = ctx.getBean(MessageSender.class);

            Map<String, TagSubscriptionMapper> foos = ctx.getBeansOfType(TagSubscriptionMapper.class);

            assertEquals(foos.size(), 1);
            assertTrue(foos.containsValue(foo));
            assertTrue(foos.containsValue(bar));

            BeanDefinition fooDefinition = ctx.getBeanDefinition("tagSubscriptionManager");
            BeanDefinition barDefinition = ctx.getBeanDefinition("aliveWriter");

            assertEquals(fooDefinition.getScope(), "equipment");
            assertEquals(barDefinition.getScope(), "equipment");
        }
    }

    @Test
    public final void newEquCreatesScope() {

        final ScopeTestHandler handler1 = new ScopeTestHandler(ctx);
        final ScopeTestHandler handler2 = new ScopeTestHandler(ctx);

        handler1.start();
        handler2.start();

        assertEquals(handler1.getCtx(), handler2.getCtx());
        assertNotEquals(handler1.getAliveWriter(), handler2.getAliveWriter());

        final Object controllerAliveWriter1 = ReflectionTestUtils.getField(handler1.getAliveWriter(), "controller");
        final Object controllerTagHandler1 = ReflectionTestUtils.getField(handler1.getDataTagHandler(), "controller");
        assertEquals(controllerAliveWriter1, controllerTagHandler1);

        final Object controllerAliveWriter2 = ReflectionTestUtils.getField(handler2.getAliveWriter(), "controller");
        final Object controllerTagHandler2 = ReflectionTestUtils.getField(handler2.getDataTagHandler(), "controller");
        assertEquals(controllerAliveWriter2, controllerTagHandler2);

        assertNotEquals(controllerAliveWriter1, controllerAliveWriter2);
    }

    @RequiredArgsConstructor
    @Getter
    public static class ScopeTestHandler {

        final ApplicationContext ctx;
        Controller controller;
        IDataTagHandler dataTagHandler;
        DataTagChanger dataTagChanger;
        AliveWriter aliveWriter;
        CommandTagHandler commandTagHandler;
        AppConfigProperties appConfigProperties;

        private static int counter = 0;

        public void start() {

            final EquipmentScope s = ctx.getBean(EquipmentScope.class);
            ((ConfigurableBeanFactory) ctx.getAutowireCapableBeanFactory()).registerScope("equipment", s);

            s.setName("Scope: # " + s.toString());
            controller = ctx.getBean("controller", Controller.class);
            dataTagHandler = ctx.getBean(IDataTagHandler.class);
            commandTagHandler = ctx.getBean(CommandTagHandler.class);
            appConfigProperties = ctx.getBean(AppConfigProperties.class);
            dataTagChanger = ctx.getBean(DataTagChanger.class);
            aliveWriter = ctx.getBean(AliveWriter.class);

            log.info(s.getName());
        }
    }
}
