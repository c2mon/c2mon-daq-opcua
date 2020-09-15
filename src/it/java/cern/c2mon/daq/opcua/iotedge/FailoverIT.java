/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerBase;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.DataTagHandler;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.testutils.TestUtils.*;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@Testcontainers
@TestPropertySource(properties = {"c2mon.daq.opcua.failoverDelay=200", "c2mon.daq.opcua.retryDelay=250", "c2mon.daq.opcua.redundancyMode=COLD"}, locations = "classpath:application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FailoverIT extends EdgeTestBase {
    TestListeners.Pulse pulseListener;
    IDataTagHandler tagHandler;
    ControllerBase coldFailover;
    Controller controllerProxy;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final Runnable serverSwitch = () -> ReflectionTestUtils.invokeMethod(coldFailover, "triggerServerSwitch");
    private ExecutorService executor;

    public static void mockColdFailover() {
        final NonTransparentRedundancyTypeNode redundancyMock = niceMock(NonTransparentRedundancyTypeNode.class);
        expect(redundancyMock.getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Cold))
                .anyTimes();
        expect(redundancyMock.getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{fallback.getUri()}))
                .anyTimes();
        EasyMock.replay(redundancyMock);
    }

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        super.setupEquipmentScope();
        pulseListener = new TestListeners.Pulse();
        tagHandler = ctx.getBean(DataTagHandler.class);
        controllerProxy = ctx.getBean(Controller.class);

        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);

        mockColdFailover();
        controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.getTagUpdate().get(0).get(TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        executor = Executors.newScheduledThreadPool(2);

        coldFailover = (ControllerBase) ReflectionTestUtils.getField(controllerProxy, "controller");
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        resetImageConnectivity();
        shutdownAndAwaitTermination(executor);
        log.info("Done cleaning up.");
    }

    @Test
    public void coldFailoverShouldReconnect() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        waitUntilRegistered(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(config.getRequestTimeout() + 1000);
        assertEquals(MessageSender.EquipmentState.OK, waitUntilRegistered(pulseListener.listen(), fallback, false));
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions: " + config.getRequestTimeout());
        waitUntilRegistered(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        waitUntilRegistered(pulseListener.listen(), fallback, false);
        assertTagUpdate();
    }

    @Test
    public void longDisconnectShouldTriggerReconnectToAnyAvailableServer() throws InterruptedException, ExecutionException, TimeoutException {
        config.setFailoverDelay(100L);
        log.info("longDisconnectShouldTriggerReconnectToAnyAvailableServer");
        waitUntilRegistered(pulseListener.listen(), active, true);
        waitUntilRegistered(pulseListener.listen(), fallback, false);
        assertTagUpdate();
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        waitUntilRegistered(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        waitUntilRegistered(pulseListener.listen(), active, false);
        assertTagUpdate();
    }

    @Test
    public void reconnectAfterLongDisconnectShouldCancelReconnection() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("restartServerWithColdFailoverShouldReconnectAndResubscribe");
        waitUntilRegistered(pulseListener.listen(), active, true);
        TimeUnit.MILLISECONDS.sleep(config.getRequestTimeout() + 1000);
        waitUntilRegistered(pulseListener.listen(), active, false);
        assertTagUpdate();
    }

    private void assertTagUpdate() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.runAsync(() -> {
            log.info("Assert tag update for tag with ID {}.", tag.getId());
            pulseListener.reset();
            pulseListener.setSourceID(tag.getId());
            assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
        }).get(TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }
}
