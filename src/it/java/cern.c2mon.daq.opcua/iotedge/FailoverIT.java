package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.testutils.TestUtils.*;
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
    @Autowired IDataTagHandler tagHandler;
    @Autowired Controller coldFailover;
    @Autowired IControllerProxy controllerProxy;
    @Autowired AppConfigProperties config;

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final NonTransparentRedundancyTypeNode redundancyMock = niceMock(NonTransparentRedundancyTypeNode.class);
    private final Runnable serverSwitch = () -> ((FailoverBase) coldFailover).triggerServerSwitch();
    private boolean resetConnection;

    ExecutorService executor;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        config.setRedundancyMode(ColdFailover.class.getName());
        pulseListener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);

        mockColdFailover();
        controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.getTagValUpdate().get(TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        resetConnection = false;
        executor = Executors.newSingleThreadExecutor();
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        TimeUnit.MILLISECONDS.sleep(TIMEOUT_IT);
        shutdownAndAwaitTermination();

        if (resetConnection) {
            log.info("Resetting fallback proxy {}", fallback.proxy);
            fallback.proxy.setConnectionCut(true);
            log.info("Resetting active proxy {}", active.proxy);
            try {
                CompletableFuture.runAsync(() -> active.proxy.setConnectionCut(false))
                        .get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Error cutting connection.", e);
            }
        } else {
            log.info("Skip resetting proxies");
        }
        log.info("Done cleaning up.");
    }

    @Test
    public void coldFailoverShouldReconnect() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldReconnectClient");
        cutConnection(pulseListener, active);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(config.getTimeout() + 1000);
        Assertions.assertEquals(IMessageSender.EquipmentState.OK, uncutConnection(pulseListener, fallback));
        resetConnection = true;
    }

    @Test
    public void coldFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("coldFailoverShouldResumeSubscriptions" + config.getTimeout());
        cutConnection(pulseListener, active);
        executor.submit(serverSwitch);
        uncutConnection(pulseListener, fallback);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void longDisconnectShouldTriggerReconnectToAnyAvailableServer() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("longDisconnectShouldTriggerReconnectToAnyAvailableServer");
        cutConnection(pulseListener, active);
        uncutConnection(pulseListener, fallback);
        assertTagUpdate();
        resetConnection = true;
    }

    @Test
    public void regainActiveConnectionWithColdFailoverShouldResumeSubscriptions() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("regainActiveConnectionWithColdFailoverShouldResumeSubscriptions");
        cutConnection(pulseListener, active);
        executor.submit(serverSwitch);
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
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
        log.info("Assert tag update for tag with ID {}.", tag.getId());
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
    }

    private void shutdownAndAwaitTermination() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(TIMEOUT_IT, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(TIMEOUT_IT, TimeUnit.MILLISECONDS)) {
                    log.error("Server switch still running");
                }
            }
        } catch (InterruptedException ie) {
            log.error("Interrupted... ", ie);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Executor shut down");
    }
}