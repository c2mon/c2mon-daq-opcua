/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
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
        AppConfig config = new AppConfig();
        factory = new ControllerFactory(properties, config.alwaysRetryTemplate(properties));
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
