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
package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.*;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.config.AppConfigProperties.FailoverMode;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class ControllerProxyTest {
    AppConfigProperties properties;
    ControllerFactory controllerFactory;
    ApplicationContext applicationContext;
    ControllerFactory controllerFactoryMock;
    Controller proxy;
    TestEndpoint testEndpoint;

    @BeforeEach
    public void setUp() {
        properties = TestUtils.createDefaultConfig();
        controllerFactory = new ControllerFactory(properties, new AppConfig().alwaysRetryTemplate(properties));
        applicationContext = createMock(ApplicationContext.class);
        controllerFactoryMock = createMock(ControllerFactory.class);
        testEndpoint = new TestEndpoint(new TestListeners.TestListener(), new TagSubscriptionMapper(new MetricProxy(new SimpleMeterRegistry())));
        proxy = new ControllerProxy(controllerFactoryMock, properties, testEndpoint);
        final UaMonitoredItem item = testEndpoint.getMonitoredItem();
        reset(item, testEndpoint.getServerRedundancyNode(), applicationContext, controllerFactoryMock);
        expect(item.getStatusCode()).andReturn(StatusCode.GOOD).anyTimes();
        replay(item);
    }

    @Test
    public void connectWithNoAddressesShouldThrowConfigException() {
        assertThrows(ConfigurationException.class, () -> proxy.connect(Collections.emptyList()));
    }

    @Test
    public void connectingUnsuccessfullyShouldThrowException() {
        testEndpoint.setThrowExceptions(true);
        assertThrows(CommunicationException.class, () -> proxy.connect(Arrays.asList("opc.tcp://test1:1", "opc.tcp://test2:2")));
    }

    @Test
    public void connectShouldUSeSecondAddressIfFirstIsUnsuccessful() throws InterruptedException {
        final String expected = "opc.tcp://test2:2";
        testEndpoint.setDelay(200L);
        testEndpoint.setThrowExceptions(true);
        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> {
            try {
                proxy.connect(Arrays.asList("opc.tcp://test1:1", expected));
            } catch (OPCUAException e) {
                log.error("Exception", e);
            }
        });
        TimeUnit.MILLISECONDS.sleep(300);
        testEndpoint.setThrowExceptions(false);
        s.shutdown();
        s.awaitTermination(1000L, TimeUnit.MILLISECONDS);
        assertEquals(expected, testEndpoint.getUri());
    }

    @Test
    public void noConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        verifyServerFailover(RedundancySupport.None);
    }

    @Test
    public void exceptionWhenLoadingRedundancyShouldFallbackToNone() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andThrow(new CompletionException(new UnknownHostException()))
                .once();
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        verifyServerFailover(RedundancySupport.None);
    }

    @Test
    public void nullRedundancyNodeShouldDefaultToNone() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(null))
                .once();
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        verifyServerFailover(RedundancySupport.None);
    }

    @Test
    public void warmShouldFallbackToColdFailover() throws OPCUAException {
        properties.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Warm, ColdFailover.class);
    }

    @Test
    public void hotShouldFallbackToColdFailover() throws OPCUAException {
        properties.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Hot, ColdFailover.class);
    }

    @Test
    public void hotAndMirroredShouldFallbackToColdFailover() throws OPCUAException {
        properties.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.HotAndMirrored, ColdFailover.class);
    }

    @Test
    public void transparentShouldUseNone() throws OPCUAException {
        properties.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Transparent, NoFailover.class);
    }

    @Test
    public void failoverAndUrisInConfigShouldNotQueryServer() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        properties.setRedundantServerUris(Collections.singletonList("redundantAddress"));
        replay(testEndpoint.getServerRedundancyNode());
        verifyConfigFailover(FailoverMode.COLD);
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void noConfigShouldQueryServerAndInitializeConfiguredFailover() throws OPCUAException {
        mockServerUriCall();
        verifyResultingRedundancyMode(RedundancySupport.Cold, ColdFailover.class);
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void noFailoverShouldNotQueryServerForUris() throws OPCUAException {
        verifyResultingRedundancyMode(RedundancySupport.None, NoFailover.class);
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void emptyUriListInConfigShouldQueryServer() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        properties.setRedundantServerUris(Collections.emptyList());
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void uriUsedToInitializeInConfigShouldQueryServer() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        properties.setRedundantServerUris(Collections.singletonList("test"));
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void noFailoverInConfigShouldNotQueryUris() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.NONE);
        replay(testEndpoint.getServerRedundancyNode());
        verifyConfigFailover(FailoverMode.NONE);
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void coldFailoverWithUrisInArgsShouldNotQueryServer() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        expect(controllerFactoryMock.getObject(FailoverMode.COLD))
                .andReturn(controllerFactory.getObject(FailoverMode.COLD))
                .once();
        replay(controllerFactoryMock, testEndpoint.getServerRedundancyNode());
        proxy.connect(Arrays.asList("test", "test2"));
        verify(controllerFactoryMock, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldFailoverWithUrisInConfigShouldNotQueryServer() throws OPCUAException {
        properties.setRedundancyMode(FailoverMode.COLD);
        properties.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void currentlyConnectedUriShouldBeFilteredFromList() throws OPCUAException {
        ColdFailover coldFailover = createMock(ColdFailover.class);
        properties.setRedundancyMode(FailoverMode.COLD);
        properties.setRedundantServerUris(Arrays.asList("test", "test2", "test3"));
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        expect(controllerFactoryMock.getObject(FailoverMode.COLD)).andReturn(coldFailover).once();
        replay(controllerFactoryMock);
        coldFailover.initialize(testEndpoint, "test2","test3");
        expectLastCall().once();
        replay(coldFailover);
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover);
    }

    private void mockServerUriCall() {
        expect(((NonTransparentRedundancyTypeNode)testEndpoint.getServerRedundancyNode()).getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();
    }

    private <E extends ConcreteController> void verifyResultingRedundancyMode(FailoverMode mode, Class<E> shouldBeInstance) throws OPCUAException {
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        verifyConfigFailover(mode);
        assertTrue(controllerFactory.getObject(mode).getClass().isAssignableFrom(shouldBeInstance));
    }

    private <E extends ConcreteController> void verifyResultingRedundancyMode(RedundancySupport mode, Class<E> shouldBeInstance) throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(mode))
                .once();
        replay(applicationContext, testEndpoint.getServerRedundancyNode());
        verifyServerFailover(mode);
        assertTrue(controllerFactory.getObject(mode).getClass().isAssignableFrom(shouldBeInstance));
    }

    private void verifyServerFailover(RedundancySupport redundancySupport) throws OPCUAException {
        expect(controllerFactoryMock.getObject(redundancySupport))
                .andReturn(controllerFactory.getObject(redundancySupport))
                .once();
        replay(controllerFactoryMock);
        proxy.connect(Collections.singleton("test"));
        verify(controllerFactoryMock);
    }

    private void verifyConfigFailover(FailoverMode mode) throws OPCUAException {
        expect(controllerFactoryMock.getObject(mode))
                .andReturn(controllerFactory.getObject(mode))
                .once();
        replay(controllerFactoryMock);
        proxy.connect(Collections.singleton("test"));
        verify(controllerFactoryMock);
    }


}
