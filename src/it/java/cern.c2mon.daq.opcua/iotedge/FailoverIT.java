package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.testutils.TestUtils.*;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.niceMock;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class FailoverIT extends EdgeTestBase {

    @Autowired TestListeners.Pulse pulseListener;
    @Autowired IDataTagHandler tagHandler;
    @Autowired
    ConcreteController coldFailover;
    @Autowired
    Controller controllerProxy;
    @Autowired AppConfigProperties config;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final Runnable serverSwitch = () -> ReflectionTestUtils.invokeMethod(((FailoverBase) coldFailover), "triggerServerSwitch");

    private boolean resetConnection;
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
        config.setRedundancyMode(ColdFailover.class.getName());
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);

        mockColdFailover();
        controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.getTagUpdate().get(TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        resetConnection = false;
        executor = Executors.newScheduledThreadPool(2);
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        TimeUnit.MILLISECONDS.sleep(TIMEOUT_IT);
        shutdownAndAwaitTermination(executor);

        if (resetConnection) {
            log.info("Resetting fallback proxy {}", fallback.proxy);
            doWithTimeout(fallback, true);
            log.info("Resetting active proxy {}", active.proxy);
            doWithTimeout(active, false);
        } else {
            log.info("Skip resetting proxies");
        }
        log.info("Done cleaning up.");
    }

    @Test
    public void coldFailoverShouldReconnect() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        doAndWait(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(config.getRequestTimeout() + 1000);
        assertEquals(IMessageSender.EquipmentState.OK, doAndWait(pulseListener.listen(), fallback, false));
        resetConnection = true;
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions" + config.getRequestTimeout());
        doAndWait(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        doAndWait(pulseListener.listen(), fallback, false);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void longDisconnectShouldTriggerReconnectToAnyAvailableServer() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("longDisconnectShouldTriggerReconnectToAnyAvailableServer");
        doAndWait(pulseListener.listen(), active, true);
        doAndWait(pulseListener.listen(), fallback, false);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        doAndWait(pulseListener.listen(), active, true);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        doAndWait(pulseListener.listen(), active, false);
        assertTagUpdate();
    }

    @Test
    public void reconnectAfterLongDisconnectShouldCancelReconnection() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("restartServerWithColdFailoverShouldReconnectAndResubscribe");
        doAndWait(pulseListener.listen(), active, true);
        TimeUnit.MILLISECONDS.sleep(config.getRequestTimeout() + 1000);
        doAndWait(pulseListener.listen(), active, false);
        assertTagUpdate();
    }

    private void assertTagUpdate() {
        log.info("Assert tag update for tag with ID {}.", tag.getId());
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
    }
}