package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailover;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.failover.NoFailover;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.FailoverTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.extern.slf4j.Slf4j;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FailoverIT extends EdgeTestBase {
    @Autowired TestListeners.Pulse pulseListener;
    @Autowired Controller controller;
    @Autowired NoFailover noFailover;
    @Autowired FailoverMode coldFailover;
    @Autowired FailoverTestEndpoint testEndpoint;
    @Autowired AppConfig config;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final NonTransparentRedundancyTypeNode redundancyMock = niceMock(NonTransparentRedundancyTypeNode.class);
    private boolean resetConnection = false;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        pulseListener.setSourceID(tag.getId());
        testEndpoint.setRedundancyMock(redundancyMock);
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(coldFailover, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(noFailover, "endpoint", testEndpoint);
        ReflectionTestUtils.setField(testEndpoint, "endpointListener", pulseListener);
        mockColdFailover();
        controller.connect(active.getUri());
        controller.subscribeTag(tag);
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
        if (resetConnection) {
            log.info("Resetting proxies");
            active.proxy.setConnectionCut(false);
            fallback.proxy.setConnectionCut(true);
            resetConnection = false;
        } else {
            log.info("Skip resetting proxies");
        }
        log.info("Done cleaning up.");
    }

    @Test
    public void coldFailoverShouldReconnect() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        cutConnection(pulseListener, active);
        triggerServerSwitch();
        Assertions.assertEquals(EndpointListener.EquipmentState.OK, uncutConnection(pulseListener, fallback));
        resetConnection = true;
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions");
        cutConnection(pulseListener, active);
        triggerServerSwitch();
        uncutConnection(pulseListener, fallback);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void longDisconnectShouldTriggerReconnectToAnyAvailableServer() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("longDisconnectShouldTriggerReconnectToAnyAvailableServer");
        cutConnection(pulseListener, active);
        TimeUnit.MILLISECONDS.sleep(config.getTimeout() + 1000);
        uncutConnection(pulseListener, fallback);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        cutConnection(pulseListener, active);
        triggerServerSwitch();
        TimeUnit.MILLISECONDS.sleep(TestUtils.TIMEOUT);
        uncutConnection(pulseListener, active);
        assertTagUpdate();
    }

    @Test
    public void reconnectAfterLongDisconnectShouldCancelReconnection() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("restartServerWithColdFailoverShouldReconnectAndResubscribe");
        cutConnection(pulseListener, active);
        TimeUnit.MILLISECONDS.sleep(config.getTimeout() + 1000);
        uncutConnection(pulseListener, active);
        assertTagUpdate();
    }

    private void mockColdFailover() {
        expect(redundancyMock.getRedundancySupport())
                .andReturn(CompletableFuture.completedFuture(RedundancySupport.Cold))
                .anyTimes();
        expect(redundancyMock.getServerUriArray())
                .andReturn(CompletableFuture.completedFuture(new String[]{fallback.getUri()}))
                .anyTimes();
        EasyMock.replay(redundancyMock);
    }

    private void assertTagUpdate() {
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
    }

    private void triggerServerSwitch() {
        CompletableFuture.runAsync(() -> ((ColdFailover) coldFailover).triggerServerSwitch());
    }
}