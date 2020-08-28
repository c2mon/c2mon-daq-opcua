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
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class ControllerProxyTest {
    AppConfigProperties config = TestUtils.createDefaultConfig();
    ApplicationContext applicationContext = createMock(ApplicationContext.class);
    Controller proxy;
    TestEndpoint testEndpoint;
    NoFailover noFailover = createNiceMock(NoFailover.class);
    ColdFailover coldFailover = createNiceMock(ColdFailover.class);

    @BeforeEach
    public void setUp() {
        testEndpoint = new TestEndpoint(new TestListeners.TestListener(), new TagSubscriptionMapper());
        proxy = new ControllerProxy(applicationContext, config, testEndpoint);
        for (FailoverMode.Type t : FailoverMode.Type.values()) {
            if (t.equals(FailoverMode.Type.NONE)) {
                expect(applicationContext.getBean(ConcreteController.class, t)).andReturn(noFailover).anyTimes();
            } else {
                expect(applicationContext.getBean(ConcreteController.class, t)).andReturn(coldFailover).anyTimes();
            }
        }
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
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void emptyModeInConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void exceptionWhenLoadingRedundancyShouldFallbackToNone() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andThrow(new CompletionException(new UnknownHostException()))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void nullRedundancyNodeShouldDefaultToNone() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(null))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void badModeInConfigShouldInitializeNoFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(noFailover);
    }

    @Test
    public void controllerThatConcreteControllerAndBeanShouldInitializeConcreteController() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Arrays.asList("test", "test2"));
        verify(noFailover);
    }

    @Test
    public void warmShouldFallbackToColdFailover() throws OPCUAException {
        verifyColdFailoverForOtherRedundancyModes(RedundancySupport.Warm);
    }

    @Test
    public void hotShouldFallbackToColdFailover() throws OPCUAException {
        verifyColdFailoverForOtherRedundancyModes(RedundancySupport.Hot);
    }

    @Test
    public void hotAndMirroredShouldFallbackToColdFailover() throws OPCUAException {
        verifyColdFailoverForOtherRedundancyModes(RedundancySupport.HotAndMirrored);
    }

    @Test
    public void transparentShouldUseNone() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Transparent))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void failoverAndUrisInConfigShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Collections.singletonList("redundantAddress"));
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), coldFailover);
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void noConfigShouldQueryServerAndInitializeConfiguredFailover() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Cold))
                .once();
        mockFailoverInitialization(coldFailover, true);
        mockServerUriCall();
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), coldFailover);
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void badlyConfiguredServerShouldIgnoreRedundantUris() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        testEndpoint.setTransparent(true);
        mockFailoverInitialization(coldFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void noFailoverShouldNotQueryServerForUris() throws OPCUAException {
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.None))
                .once();
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void emptyUriListInConfigShouldQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Collections.emptyList());
        mockFailoverInitialization(coldFailover, false);
        mockServerUriCall();
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void uriUsedToInitializeInConfigShouldQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Collections.singletonList("test"));
        mockFailoverInitialization(coldFailover, false);
        mockServerUriCall();
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("test"));
        verify(testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void singleUriInArgConfigAndServerShouldNotUseRedundantAddresses() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Collections.singletonList("redUri1"));
        mockFailoverInitialization(coldFailover, false);
        mockServerUriCall();
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), noFailover);
        proxy.connect(Collections.singleton("redUri1"));
        verify(testEndpoint.getServerRedundancyNode());
    }


    @Test
    public void noFailoverInConfigShouldNotQueryUris() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.NONE);
        mockFailoverInitialization(noFailover, false);
        replay(applicationContext, noFailover, testEndpoint.getServerRedundancyNode());
        proxy.connect(Collections.singleton("test"));
        verify(noFailover, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldConfigNoUrisShouldQueryServerForUrisNotForMode() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        mockServerUriCall();
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, noFailover, coldFailover, testEndpoint.getServerRedundancyNode());
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover);
    }

    @Test
    public void coldFailoverWithUrisInArgsShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, coldFailover, testEndpoint.getServerRedundancyNode());
        proxy.connect(Arrays.asList("test", "test2"));
        verify(coldFailover, testEndpoint.getServerRedundancyNode());
    }

    @Test
    public void coldFailoverWithUrisInConfigShouldNotQueryServer() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Arrays.asList("test1", "test2"));
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, noFailover, coldFailover);
        proxy.connect(Collections.singletonList("test1"));
        verify(coldFailover);
    }

    @Test
    public void currentlyConnectedUriShouldBeFilteredFromList() throws OPCUAException {
        config.setRedundancyMode(FailoverMode.Type.COLD);
        config.setRedundantServerUris(Arrays.asList("test1", "test2", "test3"));
        coldFailover.initialize(testEndpoint, "test2", "test3");
        expectLastCall().once();

        replay(applicationContext, noFailover, coldFailover);
        proxy.connect(Collections.singletonList("test1"));
        verify(coldFailover);
    }

    private void mockFailoverInitialization(ConcreteController mode, boolean expectRedundantAddress) throws OPCUAException {
        if (expectRedundantAddress) {
            mode.initialize(anyObject(), anyString());
        } else {
            mode.initialize(anyObject());
        }
        expectLastCall().atLeastOnce();
    }

    private void mockServerUriCall() {
        expect(((NonTransparentRedundancyTypeNode)testEndpoint.getServerRedundancyNode()).getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{"redUri1"}))
                .once();

    }

    private void verifyColdFailoverForOtherRedundancyModes(RedundancySupport mode) throws OPCUAException {
        config.setRedundantServerUris(Collections.singletonList("redUri2"));
        expect(testEndpoint.getServerRedundancyNode().getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(mode))
                .once();
        mockFailoverInitialization(coldFailover, true);
        replay(applicationContext, testEndpoint.getServerRedundancyNode(), coldFailover);
        proxy.connect(Collections.singleton("test"));
        verify(coldFailover);
    }

}
