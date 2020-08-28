package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.*;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
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
    AppConfigProperties config;
    ControllerFactory controllerFactory;
    ApplicationContext applicationContext;
    ControllerFactory controllerFactoryMock;
    Controller proxy;
    TestEndpoint testEndpoint;

    @BeforeEach
    public void setUp() {
        config = TestUtils.createDefaultConfig();
        controllerFactory = new ControllerFactory(config);
        applicationContext = createMock(ApplicationContext.class);
        controllerFactoryMock = createMock(ControllerFactory.class);
        testEndpoint = new TestEndpoint(new TestListeners.TestListener(), new TagSubscriptionMapper());
        proxy = new ControllerProxy(controllerFactoryMock, config, testEndpoint);
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
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Warm, ColdFailover.class);
    }

    @Test
    public void hotShouldFallbackToColdFailover() throws OPCUAException {
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Hot, ColdFailover.class);
    }

    @Test
    public void hotAndMirroredShouldFallbackToColdFailover() throws OPCUAException {
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.HotAndMirrored, ColdFailover.class);
    }

    @Test
    public void transparentShouldUseNone() throws OPCUAException {
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(RedundancySupport.Transparent, NoFailover.class);
    }

    @Test
    public void failoverAndUrisInConfigShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.COLD);
        config.setRedundantServerUris(Collections.singletonList("redundantAddress"));
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
        config.setRedundancyMode(FailoverMode.COLD);
        config.setRedundantServerUris(Collections.emptyList());
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void uriUsedToInitializeInConfigShouldQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.COLD);
        config.setRedundantServerUris(Collections.singletonList("test"));
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void noFailoverInConfigShouldNotQueryUris() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.NONE);
        replay(testEndpoint.getServerRedundancyNode());
        verifyConfigFailover(FailoverMode.NONE);
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.COLD);
        mockServerUriCall();
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void coldFailoverWithUrisInArgsShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.COLD);
        expect(controllerFactoryMock.getObject(FailoverMode.COLD))
                .andReturn(controllerFactory.getObject(FailoverMode.COLD))
                .once();
        replay(controllerFactoryMock, testEndpoint.getServerRedundancyNode());
        proxy.connect(Arrays.asList("test", "test2"));
        verify(controllerFactoryMock, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldFailoverWithUrisInConfigShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.COLD);
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        verifyResultingRedundancyMode(FailoverMode.COLD, ColdFailover.class);
    }

    @Test
    public void currentlyConnectedUriShouldBeFilteredFromList() throws OPCUAException {
        ColdFailover coldFailover = createMock(ColdFailover.class);
        config.setRedundancyMode(FailoverMode.COLD);
        config.setRedundantServerUris(Arrays.asList("test", "test2", "test3"));
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
