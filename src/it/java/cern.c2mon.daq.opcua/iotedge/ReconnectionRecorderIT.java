package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.ConnectionRecord;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import lombok.extern.slf4j.Slf4j;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;
import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_TOXI;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconnectionRecorderIT extends EdgeTestBase {
    @Autowired TestListeners.Pulse pulseListener;
    @Autowired IDataTagHandler tagHandler;
    @Autowired Controller coldFailover;
    @Autowired IControllerProxy controllerProxy;
    @Autowired AppConfigProperties config;

    private final Random random = new Random();
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);;
    private static final Collection<ConnectionRecord> connectionRecords = new ArrayList<>();
    private AtomicReference<EdgeImage> current = new AtomicReference<>(active);
    private AtomicReference<EdgeImage> backup = new AtomicReference<>(fallback);
    private boolean resetConnection;
    private long interruptDelayMilli = 2000L;
    private int testDurationMin = 5;
    private double avgWithoutSwitch = -1.0;
    private double avgWithExplSwitch = -1.0;
    private double avgWithImplSwitch = -1.0;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        if (connectionRecords.isEmpty()) {
            config.setRedundancyMode(ColdFailover.class.getName());
            ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
            final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
            ReflectionTestUtils.setField(e, "messageSender", pulseListener);
            FailoverIT.mockColdFailover();
            controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
            tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.RandomUnsignedInt32.createDataTag()));
            pulseListener.getTagUpdate().get(TIMEOUT_TOXI, TimeUnit.SECONDS);
            pulseListener.reset();
            resetConnection = false;
            pulseListener.setLogging(false);

            log.info("############ RUN TEST BENCH ############");
            connectionRecords.addAll(recordConnectionTimes());
        }
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        if (connectionRecords.isEmpty()) {
            log.info("############ CLEAN UP ############");
            controllerProxy.stop();
            TimeUnit.MILLISECONDS.sleep(TIMEOUT_IT);
            shutdownAndAwaitTermination(executor);

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
    }


    @Test
    public void mttrWithoutFailoverShouldBeLessThan1s() {
        log.info("mttrWithoutFailoverShouldBeLessThan1s");
        Assertions.assertTrue(avgWithoutSwitch < 1000);
    }

    @Test
    public void mttrWithExplicitFailoverShouldBeLessThan4s() {
        log.info("mttrWithExplicitFailoverShouldBeLessThan4s");
        Assertions.assertTrue(avgWithExplSwitch < 4000);
    }


    @Test
    public void mttrWithTimoutFailoverShouldBeLessThan15s() {
        log.info("mttrWithTimoutFailoverShouldBeLessThan15s");
        Assertions.assertTrue(avgWithImplSwitch < 15000);
    }


    private List<ConnectionRecord> recordConnectionTimes() throws InterruptedException, ExecutionException, TimeoutException {
        List<ConnectionRecord> connectionRecords = new ArrayList<>();
        final ScheduledFuture<?> recordFuture = executor.scheduleWithFixedDelay(() -> connectionRecords.add(recordDisconnectionTime()), 0L, interruptDelayMilli, TimeUnit.MILLISECONDS);
        executor.schedule(() -> recordFuture.cancel(true), testDurationMin -1, TimeUnit.MINUTES).get(testDurationMin, TimeUnit.MINUTES);

        final double average = connectionRecords.stream().map(ConnectionRecord::getDiff).mapToDouble(r -> r).average().orElse(-1.0);;
        avgWithoutSwitch = avgForFilter(connectionRecords, r -> !r.isFailoverToRedundantServer());
        avgWithExplSwitch = avgForFilter(connectionRecords, r -> r.isFailoverToRedundantServer() && r.isExplicitFailover());
        avgWithImplSwitch = avgForFilter(connectionRecords,r -> r.isFailoverToRedundantServer() && !r.isExplicitFailover());

        log.info("Connection Records: \n{}\n{}\n" +
                        "Average duration of reconnection: {}, " +
                        " Without server switch: {}," +
                        " With server switch {}, " +
                        " (explicit {}, implicit {})",
                String.format("%1$14s", "Reestablished") + String.format(" %1$14s", "Reconnected") + String.format(" %1$4s", "Difference") + String.format(" %1$14s", "Redundant") + String.format(" %1$14s", "Explicit"),
                connectionRecords.stream().map(ConnectionRecord::toString).collect(Collectors.joining("\n")),
                average, avgWithoutSwitch,
                avgForFilter(connectionRecords,ConnectionRecord::isFailoverToRedundantServer),
                avgWithExplSwitch, avgWithImplSwitch);
        resetConnection = current.get() == fallback;
        return connectionRecords;
    }

    private double avgForFilter(Collection<ConnectionRecord> connectionRecords, Predicate<ConnectionRecord> p) {
        return connectionRecords.stream().filter(p)
                .map(ConnectionRecord::getDiff)
                .mapToDouble(r -> r).average()
                .orElse(-1.0);
    }

    private ConnectionRecord failover(EdgeImage cut, EdgeImage uncut) {
        long reestablished = -1L;
        long reconnected = - 1L;
        final boolean isFailover = cut != uncut;
        final boolean explicitFailover = isFailover && random.nextBoolean();
        try {
            cut.proxy.setConnectionCut(true);
            if (explicitFailover) {
                executor.submit(this::switchServer);
            }
            TimeUnit.MILLISECONDS.sleep(random.ints(0,4000).findFirst().getAsInt());
            reestablished = System.currentTimeMillis();
            uncut.proxy.setConnectionCut(false);
            pulseListener.reset();
            CompletableFuture.anyOf(pulseListener.getTagUpdate(), pulseListener.getStateUpdate()).get();
            reconnected = System.currentTimeMillis();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error during failover from {} to {}: ", cut.uri, uncut.uri, e);
        }
        return ConnectionRecord.of(reestablished, reconnected, isFailover, explicitFailover);
    }

    private void switchServer() {
        ReflectionTestUtils.invokeMethod(coldFailover, "triggerServerSwitch");
    }

    private ConnectionRecord recordDisconnectionTime() {
        ConnectionRecord record;
        if (random.nextBoolean()) {
            record = failover(current.get(), backup.get());
            switchCurrentImg();
        } else {
            record = failover(current.get(), current.get());
        }
        return record;
    }

    private void switchCurrentImg() {
        EdgeImage tmp = current.get();
        current.set(backup.get());
        backup.set(tmp);
    }

}