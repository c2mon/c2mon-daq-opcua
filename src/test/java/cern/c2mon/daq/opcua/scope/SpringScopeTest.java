package cern.c2mon.daq.opcua.scope;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.taghandling.AliveWriter;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagChanger;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Slf4j
@SpringBootTest
public class SpringScopeTest {

    @Autowired
    ApplicationContext ctx;


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

            controller = ctx.getBean(Controller.class);
            dataTagHandler = ctx.getBean(IDataTagHandler.class);
            commandTagHandler = ctx.getBean(CommandTagHandler.class);
            appConfigProperties = ctx.getBean(AppConfigProperties.class);
            dataTagChanger = ctx.getBean(DataTagChanger.class);
            aliveWriter = ctx.getBean(AliveWriter.class);

            log.info(s.toString());
        }
    }
}
