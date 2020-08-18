package cern.c2mon.daq.opcua.controller;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

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

    @BeforeEach
    public void setUp() {
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
    public void disconnectAfterInitializationShouldThrowException() throws OPCUAException, InterruptedException, TimeoutException, ExecutionException {
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        final CompletableFuture<Void> f = runAsync(() -> assertThrows(CommunicationException.class,
                () -> setupConnectionMonitoringAndVerify("redundant")));
        initLatch.await(300L, TimeUnit.MILLISECONDS);
        endpoint.setThrowExceptions(true);
        f.get(1000L, TimeUnit.MILLISECONDS);
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
        endpoint.setDelay(200L);
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
    public void switchServersShouldSwitchToNextServer() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        coldFailover.switchServers();
        assertEquals("redundant", endpoint.getUri());
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
    public void onSessionActiveShouldTriggerFailover() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        TimeUnit.MILLISECONDS.sleep(100L);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void twoOnSessionInactivesShouldOnlyTriggerOneFailover() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionInactive(null);
        coldFailover.onSessionInactive(null);
        TimeUnit.MILLISECONDS.sleep(100L);
        assertEquals("redundant", endpoint.getUri());
    }

    @Test
    public void unsuccessfulFailoverShouldBeRepeated() throws OPCUAException, InterruptedException {
        setupConnectionMonitoring("redundant");
        initLatch = new CountDownLatch(5);
        endpoint.setInitLatch(initLatch);
        config.setFailoverDelay(1L);
        config.setRetryDelay(1);
        config.setRetryMultiplier(1);
        config.setFailoverDelay(1);
        endpoint.setThrowExceptions(true);
        coldFailover.onSessionInactive(null);
        assertDoesNotThrow(() -> initLatch.await(500L, TimeUnit.MILLISECONDS));

        //cleanup
        endpoint.setThrowExceptions(false);
        initLatch = new CountDownLatch(1);
        endpoint.setInitLatch(initLatch);
        initLatch.await(500L, TimeUnit.MILLISECONDS);
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
    public void twoOnSessionActivesShouldDoNothing() throws OPCUAException {
        setupConnectionMonitoring("redundant");
        config.setFailoverDelay(10L);
        coldFailover.onSessionActive(null);
        coldFailover.onSessionActive(null);
        assertEquals("test", endpoint.getUri());
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
