package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.control.DataTagHandler;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailoverDecorator;
import cern.c2mon.daq.opcua.failover.Controller;
import cern.c2mon.daq.opcua.failover.ControllerProxy;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
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

import java.util.Collections;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FailoverIT extends EdgeTestBase {
    @Autowired TestListeners.Pulse pulseListener;
    @Autowired DataTagHandler tagHandler;
    @Autowired Controller singleServerController;
    @Autowired Controller coldFailover;
    @Autowired ControllerProxy controllerProxy;
    @Autowired AppConfigProperties config;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final NonTransparentRedundancyTypeNode redundancyMock = niceMock(NonTransparentRedundancyTypeNode.class);
    private boolean resetConnection;


    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        config.setRedundancyMode(ColdFailoverDecorator.class.getName());
        config.setRedundantServerUris(Collections.singletonList(fallback.getUri()));
        pulseListener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(controllerProxy, "messageSender", pulseListener);
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        ReflectionTestUtils.setField(singleServerController.currentEndpoint(), "messageSender", pulseListener);
        mockColdFailover();
        controllerProxy.connect(active.getUri());
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        resetConnection = false;
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        if (resetConnection) {
            log.info("Resetting active proxy {}", active.proxy);
            active.proxy.setConnectionCut(false);
            log.info("Resetting fallpack proxy {}", fallback.proxy);
            fallback.proxy.setConnectionCut(true);
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
        Assertions.assertEquals(MessageSender.EquipmentState.OK, uncutConnection(pulseListener, fallback));
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
        CompletableFuture.runAsync(() -> ((ColdFailoverDecorator) coldFailover).triggerServerSwitch());
    }
}