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
package cern.c2mon.daq.opcua.retry;

import cern.c2mon.daq.opcua.SpringTestBase;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.testutils.TestController;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@TestPropertySource(properties = {"c2mon.daq.opcua.retryDelay=1000"}, locations = "classpath:application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RetryFailoverIT extends SpringTestBase {

    TestController testController;

    @BeforeEach
    public void setupEquipmentScope() throws ConfigurationException {
        super.setupEquipmentScope();
        testController = new TestController(config);
        testController.setEndpoint(ctx.getBean(Endpoint.class));
        config.setFailoverDelay(1L);
        testController.setStopped(false);
        testController.setListening(true);
        testController.setTo(2, null);
    }

    @Test
    public void successfulSwitchShouldNotBeRetried() {
        config.setRetryMultiplier(1);
        config.setRetryDelay(1L);
        testController.triggerRetryFailover();
        assertTrue(testController.getSwitchDone().isDone());
    }

    @Test
    public void endpointDisconnectedShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new EndpointDisconnectedException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void communicationExceptionShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void configurationExceptionShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void stoppedEndpointShouldNotBeRetried() {
        testController.setToThrow(new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION));
        testController.setStopped(true);
        testController.triggerRetryFailover();
        assertTrue(testController.getSwitchDone().isDone());
    }
}
