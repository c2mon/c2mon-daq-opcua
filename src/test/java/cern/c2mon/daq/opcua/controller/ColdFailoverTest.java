package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.FailoverController;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
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
import org.junit.jupiter.api.AfterEach;
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
    AppConfigProperties properties;
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
        properties = TestUtils.createDefaultConfig();
        coldFailover = new ColdFailover(properties, new AppConfig().alwaysRetryTemplate(properties));
        endpoint = new TestEndpoint(listener, new TagSubscriptionMapper());
        endpoint.setReadValue(UByte.valueOf(250));
        endpoint.setThrowExceptions(false);
        endpoint.setInitLatch(initLatch);
        endpoint.setReadLatch(readLatch);
    }

    @AfterEach
    public void cleanUp() {
        coldFailover.stop();
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
        synchronized (endpoint.getInitLatch()) {
            initLatch.await(300L, TimeUnit.MILLISECONDS);
            endpoint.setThrowExceptions(true);
        }
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void initializeAndStopShouldNotSetupMonitoring() throws InterruptedException, OPCUAException {
        endpoint.setReadValue(UByte.valueOf(10));
        endpoint.initialize("test");
        replay(endpoint.getMonitoredItem());
        runAsync(() -> {
            try {
                coldFailover.initialize(endpoint, "redundant2", "redundant2", "redundant3", "redundant4");
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        synchronized (endpoint.getReadLatch()) {
            readLatch.await(200L, TimeUnit.MILLISECONDS);
            coldFailover.stop();
        }
        verify(endpoint.getMonitoredItem());
    }

    @Test
    public void monitoringShouldNotBeSetupOnStoppedEndpoint() throws InterruptedException, TimeoutException, ExecutionException, OPCUAException {
        endpoint.initialize("test");
        replay(endpoint.getMonitoredItem());
        final CompletableFuture<Void> f = runAsync(() -> {
            try {
                coldFailover.initialize(endpoint);
                verify(endpoint.getMonitoredItem());
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        synchronized (endpoint.getReadLatch()) {
            readLatch.await(1000L, TimeUnit.MILLISECONDS);
            coldFailover.stop();
        }
        f.get(3000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void initializingWithUninitializedEndpointShouldThrowException() {
        endpoint.setReadValue(UByte.valueOf(10));
        assertThrows(IllegalArgumentException.class, () -> coldFailover.initialize(endpoint));
    }

    @Test
    public void initializeShouldConnectToNextEndpointIfFirstIsUnhealthy() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> initializeCatchAndVerifyConnectionMonitoring("redundant"));
        synchronized (endpoint.getReadLatch()) {
            readLatch.await(300L, TimeUnit.MILLISECONDS);
            log.info("Setting service level to 250");
            endpoint.setReadValue(UByte.valueOf(250));
        }
        f.get(1000L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void initializeShouldConnectToFirstEndpointIfItIsHealthiest() throws InterruptedException, TimeoutException, ExecutionException {
        readLatch = new CountDownLatch(1);
        endpoint.setReadLatch(readLatch);
        endpoint.setReadValue(UByte.valueOf(100));
        final CompletableFuture<Void> f = runAsync(() -> initializeCatchAndVerifyConnectionMonitoring("redundant"));
        synchronized (endpoint.getReadLatch()) {
            readLatch.await(300L, TimeUnit.MILLISECONDS);
            endpoint.setReadValue(UByte.valueOf(50));
        }
        f.get(1000L, TimeUnit.MILLISECONDS);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void disconnectedEndpointShouldThrowCommunicationException() throws InterruptedException, TimeoutException, ExecutionException {
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> assertThrows(CommunicationException.class,
                () -> setupConnectionMonitoringAndVerify("redundant"),
                ExceptionContext.NO_REDUNDANT_SERVER.getMessage()));
        synchronized (endpoint.getInitLatch()) {
            initLatch.await(200L, TimeUnit.MILLISECONDS);
            endpoint.setThrowExceptions(true);
        }
        f.get(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void stoppingEndpointDuringConnectionShouldNotSetupMonitoring() throws InterruptedException, TimeoutException, ExecutionException {
        replay(endpoint.getMonitoredItem());
        endpoint.setReadValue(UByte.valueOf(10));
        final CompletableFuture<Void> f = runAsync(() -> {
            try {
                endpoint.initialize("test");
                coldFailover.initialize(endpoint, "redundant1", "redundant2");
            } catch (OPCUAException e) {
                log.error("Exception: ", e);
            }
        });
        synchronized (endpoint.getInitLatch()) {
            initLatch.await(200L, TimeUnit.MILLISECONDS);
            coldFailover.stop();
        }
        f.get(1000L, TimeUnit.MILLISECONDS);
        verify(endpoint.getMonitoredItem());
    }
    @Test
    public void stoppingEndpointAfterMonitoringShouldNotSetupConsumer() throws InterruptedException, TimeoutException, ExecutionException, OPCUAException {
        setupConnectionMonitoring("redundant1", "redundant2");
        synchronized (endpoint.getInitLatch()) {
            initLatch.await(200L, TimeUnit.MILLISECONDS);
            coldFailover.stop();
        }
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
        ((FailoverController)coldFailover).switchServers();
        assertFalse(initLatch.await(200L, TimeUnit.MILLISECONDS));
    }


    @Test
    public void twoFailoverTriggersShouldOnlyTriggerOneFailover() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        captureConsumers(serviceLevel, serverState, "redundant1", "redundant2");
        waitForConsumerCapture(serviceLevel);
        initLatch = new CountDownLatch(2);
        endpoint.setInitLatch(initLatch);
        endpoint.setDelay(100L);
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
        properties.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void unsuccessfulFailoverShouldBeRepeated() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        endpoint.setThrowExceptions(true);
        initLatch = new CountDownLatch(4);
        endpoint.setInitLatch(initLatch);
        properties.setFailoverDelay(2L);
        properties.setRetryDelay(1L);
        properties.setRetryMultiplier(1);
        coldFailover.onSessionInactive(null);
        assertTrue(initLatch.await(500L, TimeUnit.MILLISECONDS));

        //cleanup
        synchronized (endpoint.getInitLatch()) {
            endpoint.setThrowExceptions(false);
            initLatch = new CountDownLatch(1);
            endpoint.setInitLatch(initLatch);
        }
        initLatch.await(100L, TimeUnit.MILLISECONDS);
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
    public void onSessionActiveShouldCancelFailover() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        properties.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void onSessionInactiveActiveInactiveShouldTriggerFailover() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        properties.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        coldFailover.onSessionInactive(null);
        initLatch.await(100L, TimeUnit.MILLISECONDS);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void onSessionActiveWhenNeverInactiveShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        properties.setFailoverDelay(10L);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void twoOnSessionActivesShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        properties.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionActive(null);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void twoOnSessionInactivesShouldTriggerOne() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        properties.setFailoverDelay(10L);
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
        expect(endpoint.getMonitoredItem().getReadValueId()).andReturn(new ReadValueId(Identifiers.Server, null, null, null)).anyTimes();
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
