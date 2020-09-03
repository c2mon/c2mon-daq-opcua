package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.TimeRecordMode;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.control.ControllerFactory;
import cern.c2mon.daq.opcua.control.NoFailover;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cern.c2mon.daq.opcua.config.AppConfigProperties.FailoverMode.*;
import static org.junit.jupiter.api.Assertions.*;

public class ControllerFactoryTest {

    ControllerFactory factory;

    @BeforeEach
    public void setUp() {
        AppConfigProperties properties = AppConfigProperties.builder().maxRetryAttempts(3).requestTimeout(300).timeRecordMode(TimeRecordMode.CLOSEST).retryDelay(1000).build();
        AppConfig config = new AppConfig(properties);
        factory = new ControllerFactory(properties, config.alwaysRetryTemplate());
    }

    @Test
    public void noneShouldReturnNoFailover() {
        assertTrue(factory.getObject(NONE) instanceof NoFailover);
    }

    @Test
    public void transparentShouldReturnNoFailover() {
        assertTrue(factory.getObject(NONE) instanceof NoFailover);
    }

    @Test
    public void allOtherTypesShouldReturnColdFailover() {
        AppConfigProperties.FailoverMode[] modes = {COLD, WARM, HOT, HOTANDMIRRORED};
        for (AppConfigProperties.FailoverMode m : modes)
        assertTrue(factory.getObject(m) instanceof ColdFailover);
    }

    @Test
    public void setSimpleObjectShouldDefaultToNoFailover() {
        assertTrue(factory.getObject() instanceof NoFailover);
    }

    @Test
    public void controllerFactoryShouldNotBeSingleton() {
        assertFalse(factory.isSingleton());
    }

    @Test
    public void controllerFactoryIsOfTypeConcreteController() {
        assertEquals(ConcreteController.class, factory.getObjectType());
    }
}
