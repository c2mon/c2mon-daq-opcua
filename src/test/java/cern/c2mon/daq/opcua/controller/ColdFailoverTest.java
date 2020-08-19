package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.FailoverMode;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.runAsync;
import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ColdFailoverTest {

    ColdFailover coldFailover;
    TestEndpoint endpoint;
    TestListeners.TestListener listener = new TestListeners.TestListener();
    AppConfigProperties config;
    CountDownLatch initLatch;
    CountDownLatch readLatch;
    Capture<Consumer<DataValue>> serviceLevel;
    Capture<Consumer<DataValue>> serverState;

    @BeforeEach
    public void setUp() {
        serviceLevel = newCapture(CaptureType.ALL);
        serverState = newCapture(CaptureType.ALL);

        readLatch = new CountDownLatch(1);
        initLatch = new CountDownLatch(2);
        config = TestUtils.createDefaultConfig();
        coldFailover = new ColdFailover(config);
        endpoint = new TestEndpoint(listener, new TagSubscriptionMapper());
        endpoint.setReadValue(UByte.valueOf(250));
        endpoint.setThrowExceptions(false);
        endpoint.setInitLatch(initLatch);
        endpoint.setReadLatch(readLatch);
    }

    @Test
    public void successfulInitializeShouldSubscribeConnectionTag() throws OPCUAException {
        setupConnectionMonitoringAndVerify();
    }

    @Test
    public void noHealthyServerShouldStillConnect() throws OPCUAException {
        endpoint.setReadValue(UByte.valueOf(10));
        setupConnectionMonitoringAndVerify();
    }

    @Test
    public void badServiceLevelReadingsShouldReturnUnhealthyButConnect() throws OPCUAException {
        endpoint.setReadValue("badReadingValue");
        setupConnectionMonitoringAndVerify();
    }

    @Test
    public void disconnectAfterInitializationShouldThrowException() throws InterruptedException, TimeoutException, ExecutionException {
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        final CompletableFuture<Void> f = runAsync(() -> assertThrows(CommunicationException.class,
                () -> setupConnectionMonitoringAndVerify("redundant")));
        initLatch.await(300L, TimeUnit.MILLISECONDS);
        endpoint.setThrowExceptions(true);
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void initializeAndStopNotSetupMonitoring() throws InterruptedException, OPCUAException {
        endpoint.setDelay(100L);
        endpoint.setReadValue(UByte.valueOf(10));
        initLatch = new CountDownLatch(1);
        endpoint.initialize("test");
        replay(endpoint.getMonitoredItem());
        runAsync(() -> {
            try {
                coldFailover.initialize(endpoint, "redundant2", "redundant2", "redundant3", "redundant4");
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        readLatch.await(200L, TimeUnit.MILLISECONDS);
        runAsync(() -> coldFailover.stop());
        verify(endpoint.getMonitoredItem());
    }

    @Test
    public void endpointDisconnectedExceptionShouldNotThrowException() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setDelay(100L);
        endpoint.setToThrow(new EndpointDisconnectedException(ExceptionContext.READ));
        final CompletableFuture<Void> f = runAsync(() -> assertDoesNotThrow(() -> setupConnectionMonitoring()));
        readLatch.await(300L, TimeUnit.MILLISECONDS);
        endpoint.setThrowExceptions(true);
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void monitoringShouldNotBeSetupOnStoppedEndpoint() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setDelay(300L);
        final CompletableFuture<Void> f = runAsync(() -> {
            try {
                endpoint.initialize("test");
                replay(endpoint.getMonitoredItem());
                coldFailover.initialize(endpoint);
                verify(endpoint.getMonitoredItem());
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        readLatch.await(1000L, TimeUnit.MILLISECONDS);
        coldFailover.stop();
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void initializingWithUninitializedEndpointShouldThrowException() {
        endpoint.setReadValue(UByte.valueOf(10));
        assertThrows(IllegalArgumentException.class, () -> coldFailover.initialize(endpoint));
    }

    @Test
    public void initializeShouldConnectToNextEndpointIfFirstIsUnhealthy() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setDelay(100L);
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> initializeCatchAndVerifyConnectionMonitoring("redundant"));
        initLatch.await(300L, TimeUnit.MILLISECONDS);
        endpoint.setReadValue(UByte.valueOf(250));
        f.get(1000L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void initializeShouldConnectToFirstEndpointIfItIsHealthiest() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setDelay(50L);
        initLatch = new CountDownLatch(3);
        endpoint.setInitLatch(initLatch);
        endpoint.setReadValue(UByte.valueOf(100));
        final CompletableFuture<Void> f = runAsync(() -> initializeCatchAndVerifyConnectionMonitoring("redundant"));
        initLatch.await(300L, TimeUnit.MILLISECONDS);
        endpoint.setReadValue(UByte.valueOf(50));
        f.get(1000L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void disconnectedEndpointShouldThrowCommunicationException() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setDelay(100L);
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> assertThrows(CommunicationException.class,
                () -> setupConnectionMonitoringAndVerify("redundant"),
                ExceptionContext.NO_REDUNDANT_SERVER.getMessage()));
        initLatch.await(200L, TimeUnit.MILLISECONDS);
        endpoint.setThrowExceptions(true);
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void stoppingEndpointDuringConnectionShouldNotSetupMonitoring() throws InterruptedException, TimeoutException, ExecutionException {
        replay(endpoint.getMonitoredItem());
        endpoint.setDelay(100L);
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> {
            try {
                endpoint.initialize("test");
                coldFailover.initialize(endpoint, "redundant1", "redundant2");
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        initLatch.await(200L, TimeUnit.MILLISECONDS);
        coldFailover.stop();
        f.get(1000L, TimeUnit.MILLISECONDS);
        verify(endpoint.getMonitoredItem());
    }
    @Test
    public void stoppingEndpointAfterMonitoringShouldNotSetupConsumer() throws InterruptedException, TimeoutException, ExecutionException, OPCUAException {
        endpoint.setDelay(300L);
        setupConnectionMonitoring("redundant1", "redundant2");
        initLatch.await(200L, TimeUnit.MILLISECONDS);
        coldFailover.stop();
        verify(endpoint.getMonitoredItem());
    }

    @Test
    public void switchServersShouldSwitchToNextServer() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        coldFailover.switchServers();
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void connectionMonitoringShouldSetValueConsumers() {
        CompletableFuture.runAsync(() -> {
            try {
                captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
            } catch (OPCUAException e) {
                log.error("Exception:", e);
            }
        });
        assertDoesNotThrow(() -> waitForConsumerCapture(serviceLevel, serverState));
    }

    @Test
    public void serviceLevelConsumerShouldTriggerFailoverIfUnhealthy() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serviceLevel.getValue().accept(new DataValue(new Variant(UByte.valueOf(10))));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant1", endpoint.getUri());
    }

    @Test
    public void badNodeIdShouldSetIrrelevantConsumer() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        expect(endpoint.getMonitoredItem().getReadValueId())
                .andReturn(ReadValueId.builder().nodeId(Identifiers.LocaleId).build())
                .anyTimes();
        endpoint.getMonitoredItem().setValueConsumer(capture(serviceLevel));
        expectLastCall().anyTimes();
        setupConnectionMonitoring("redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serviceLevel.getValue().accept(new DataValue(new Variant(UByte.valueOf(10))));
        assertFalse(initLatch.await(200L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void triggerFailoverOnUninitializedServerShouldDoNothing() throws InterruptedException, OPCUAException {
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        ((FailoverMode)coldFailover).switchServers();
        assertFalse(initLatch.await(200L, TimeUnit.MILLISECONDS));
    }


    @Test
    public void twoFailoverTriggersShouldOnlyTriggerOneFailover() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(2);
        endpoint.setInitLatch(initLatch);
        endpoint.setDelay(10L);
        CompletableFuture.runAsync(() -> serviceLevel.getValue().accept(new DataValue(new Variant(UByte.valueOf(10)))));
        CompletableFuture.runAsync(() -> serviceLevel.getValue().accept(new DataValue(new Variant(UByte.valueOf(10)))));
        assertFalse(initLatch.await(200L, TimeUnit.MILLISECONDS));
        assertEquals("redundant1", endpoint.getUri());
    }

    @Test
    public void serviceLevelConsumerShouldNotTriggerFailoverIfHealthy() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serviceLevel.getValue().accept(new DataValue(new Variant(UByte.valueOf(220))));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void serviceLevelConsumerShouldIgnoreBadValue() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serviceLevel.getValue().accept(new DataValue(new Variant("bad value")));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void serverStateConsumerShouldIgnoreBadUpdates() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serverState);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serverState.getValue().accept(new DataValue(new Variant("bad value")));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void serverStateConsumerShouldTriggerFailoverIfUnhealthy() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serverState);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serverState.getValue().accept(new DataValue(new Variant(ServerState.CommunicationFault)));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant1", endpoint.getUri());
    }

    @Test
    public void serverStateConsumerShouldNotTriggerFailoverIfHealthy() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serverState);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        serverState.getValue().accept(new DataValue(new Variant(ServerState.Running)));
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void switchServersOnAfterStopShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        coldFailover.stop();
        coldFailover.switchServers();
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void switchServersShouldThrowExceptionOnUnsuccessfulConnect() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        endpoint.setThrowExceptions(true);
        assertThrows(OPCUAException.class, () -> coldFailover.switchServers());
    }

    @Test
    public void onSessionInactiveShouldTriggerFailover() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void unsuccessfulFailoverShouldBeRepeated() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        initLatch = new CountDownLatch(4);
        endpoint.setInitLatch(initLatch);
        config.setFailoverDelay(1L);
        config.setRetryDelay(1);
        config.setRetryMultiplier(1);
        config.setFailoverDelay(1);
        endpoint.setThrowExceptions(true);
        coldFailover.onSessionInactive(null);
        assertTrue(initLatch.await(500L, TimeUnit.MILLISECONDS));

        //cleanup
        endpoint.setThrowExceptions(false);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        assertTrue(initLatch.await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void onSessionInactiveOnStoppedServerShouldDoNothing() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        coldFailover.stop();
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        coldFailover.onSessionInactive(null);
        assertFalse(initLatch.await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void onSessionInactiveWithNegativeFailoverDelayShouldDoNothing() throws OPCUAException, InterruptedException {
        config.setFailoverDelay(-1);
        setupConnectionMonitoring("redundant");
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        coldFailover.onSessionInactive(null);
        assertFalse(initLatch.await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void onSessionActiveShouldCancelFailover() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void onSessionInactiveActiveInactiveShouldTriggerFailover() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        coldFailover.onSessionInactive(null);
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void onSessionActiveWhenNeverInactiveShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void twoOnSessionActivesShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void twoOnSessionInactivesShouldTriggerOne() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionInactive(null);
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }


    private void waitForConsumerCapture(Capture<Consumer<DataValue>>... capture) throws InterruptedException, ExecutionException, TimeoutException {
        runAsync(() -> {
            while (!Arrays.stream(capture).allMatch(Capture::hasCaptured)) {
                try {
                    TimeUnit.MILLISECONDS.sleep(endpoint.getDelay());
                } catch (InterruptedException e) {
                    log.info("Interrupted, proceed now");
                    return;
                }
            }
        }).get(10000L, TimeUnit.MILLISECONDS);
    }

    private void captureConsumers(Capture<Consumer<DataValue>> serviceLevel, Capture<Consumer<DataValue>> serverState, String... uris) throws OPCUAException {
        expect(endpoint.getMonitoredItem().getReadValueId())
                .andReturn(ReadValueId.builder().nodeId(Identifiers.Server_ServiceLevel).build())
                .once();
        endpoint.getMonitoredItem().setValueConsumer(capture(serviceLevel));
        expectLastCall().times(1);
        expect(endpoint.getMonitoredItem().getReadValueId())
                .andReturn(ReadValueId.builder().nodeId(Identifiers.ServerState).build())
                .once();
        endpoint.getMonitoredItem().setValueConsumer(capture(serverState));
        expectLastCall().times(1);
        setupConnectionMonitoring(uris);
    }


    private void initializeCatchAndVerifyConnectionMonitoring(String... uris) {
        try {
            setupConnectionMonitoringAndVerify(uris);
        } catch (OPCUAException e) {
            log.error("Exception: ", e);
        }

    }

    private void setupConnectionMonitoringAndVerify(String... uris) throws OPCUAException {
        endpoint.initialize("test");
        expect(endpoint.getMonitoredItem().getStatusCode()).andReturn(StatusCode.GOOD).times(2);
        replay(endpoint.getMonitoredItem());
        coldFailover.initialize(endpoint, uris);
        verify(endpoint.getMonitoredItem());
    }

    private void setupConnectionMonitoring(String... uris) throws OPCUAException {
        endpoint.initialize("test");
        expect(endpoint.getMonitoredItem().getStatusCode()).andReturn(StatusCode.GOOD).anyTimes();
        replay(endpoint.getMonitoredItem());
        coldFailover.initialize(endpoint, uris);
    }
}
