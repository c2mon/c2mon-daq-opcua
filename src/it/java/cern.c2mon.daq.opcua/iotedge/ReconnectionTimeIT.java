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
import cern.c2mon.daq.opcua.testutils.TestUtils;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;
import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_TOXI;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconnectionTimeIT extends EdgeTestBase {
    private static final Collection<ConnectionRecord> connectionRecords = new ArrayList<>();
    private static double avgWithoutSwitch = -1.0;
    private static double avgWithExplSwitch = -1.0;
    private static double avgWithImplSwitch = -1.0;
    private static boolean resetConnection = false;
    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    @Autowired
    TestListeners.Pulse pulseListener;
    @Autowired
    IDataTagHandler tagHandler;
    @Autowired
    Controller coldFailover;
    @Autowired
    IControllerProxy controllerProxy;
    @Autowired
    AppConfigProperties config;
    private long interruptDelayMilli = 2000L;
    private int testDurationMin = 5;
    private EdgeImage current;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        if (connectionRecords.isEmpty()) {
            current = active;

            config.setRedundancyMode(ColdFailover.class.getName());
            ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
            final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
            ReflectionTestUtils.setField(e, "messageSender", pulseListener);
            FailoverIT.mockColdFailover();
            controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
            tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.RandomUnsignedInt32.createDataTag()));
            pulseListener.getTagUpdate().get(TIMEOUT_TOXI, TimeUnit.SECONDS);
            pulseListener.reset();
            pulseListener.setLogging(false);

            log.info("############ RUN TEST BENCH ############");
            connectionRecords.addAll(recordConnectionTimes());
        }
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        if (connectionRecords.isEmpty()) {
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
        Assertions.assertTrue(avgWithoutSwitch >= 0 && avgWithoutSwitch < 1000);
    }

    @Test
    public void mttrWithExplicitFailoverShouldBeLessThan4s() {
        log.info("mttrWithExplicitFailoverShouldBeLessThan4s");
        Assertions.assertTrue(avgWithExplSwitch >= 0 && avgWithExplSwitch < 4000);
    }


    @Test
    public void mttrWithTimoutFailoverShouldBeLessThan15s() {
        log.info("mttrWithTimoutFailoverShouldBeLessThan15s");
        Assertions.assertTrue(avgWithImplSwitch >= 0 && avgWithImplSwitch < 15000);
    }


    private List<ConnectionRecord> recordConnectionTimes() {
        List<ConnectionRecord> connectionRecords = new ArrayList<>();
        final ScheduledFuture<?> recordFuture = executor.scheduleWithFixedDelay(
                () -> connectionRecords.add(recordDisconnectionTime()),
                0L, interruptDelayMilli, TimeUnit.MILLISECONDS);
        try {
            executor.schedule(() -> {
                log.info("Cancelling... ");
                recordFuture.cancel(true);
            }, testDurationMin, TimeUnit.MINUTES)
                    .get(testDurationMin * 2, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.info("Failed with exception: ", e);
            recordFuture.cancel(true);
        }

        final double average = avgForFilter(connectionRecords, r -> true);
        avgWithoutSwitch = avgForFilter(connectionRecords, r -> !r.isFailoverToRedundantServer());
        avgWithExplSwitch = avgForFilter(connectionRecords, r -> r.isFailoverToRedundantServer() && r.isExplicitFailover());
        avgWithImplSwitch = avgForFilter(connectionRecords, r -> r.isFailoverToRedundantServer() && !r.isExplicitFailover());

        log.info("Connection Records: \n{}\n{}\n" +
                        "Average duration of reconnection: {}\n" +
                        "    Without server switch: {}\n" +
                        "    With server switch {}\n" +
                        "        explicit {}\n" +
                        "        implicit {}",
                String.format("%1$14s", "Reestablished") + String.format(" %1$14s", "Reconnected") + String.format(" %1$14s", "Difference") + String.format(" %1$14s", "Redundant") + String.format(" %1$14s", "Explicit"),
                connectionRecords.stream().map(ConnectionRecord::toString).collect(Collectors.joining("\n")),
                average, avgWithoutSwitch,
                avgForFilter(connectionRecords, ConnectionRecord::isFailoverToRedundantServer),
                avgWithExplSwitch, avgWithImplSwitch);
        resetConnection = current == fallback;
        return connectionRecords;
    }

    private double avgForFilter(Collection<ConnectionRecord> connectionRecords, Predicate<ConnectionRecord> p) {
        return connectionRecords.stream().filter(p)
                .map(ConnectionRecord::getDiff)
                .mapToDouble(r -> r).average()
                .orElse(-1.0);
    }

    private ConnectionRecord recordDisconnectionTime() {
        ConnectionRecord record;
        synchronized (this) {
            final boolean currentActive = current == active;
            if (random.nextBoolean()) {
                log.info(currentActive ? "Failover from active to backup." : "Failover from backup to active.");
                final EdgeImage fallback = currentActive ? EdgeTestBase.fallback : active;
                record = failover(current, fallback);
                current = fallback;
            } else {
                log.info("Temporarily cut connection on {}", currentActive ? "active" : "backup");
                record = failover(current, current);
            }
        }
        return record;
    }

    private ConnectionRecord failover(EdgeImage cut, EdgeImage uncut) {
        long reestablished = -1L;
        long reconnected = -1L;
        final boolean explicitFailover = cut != uncut && random.nextBoolean();
        cutAndLog(cut);

        if (explicitFailover) {
            log.info("Triggering server switch.");
            executor.submit(() -> ReflectionTestUtils.invokeMethod(coldFailover, "triggerServerSwitch"));
        }

        final int waitFor = random.ints(500, 4000).findFirst().orElse(0);
        log.info("Wait for {} ms.", waitFor);

        try {
            TimeUnit.MILLISECONDS.sleep(waitFor);
            reestablished = System.currentTimeMillis();
            reconnected = uncutAndAwait(uncut);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error during failover: ", e);
        }
        final ConnectionRecord record = ConnectionRecord.of(reestablished, reconnected, cut != uncut, explicitFailover);
        log.info("Record: {}", record);
        return record;
    }

    private void cutAndLog(EdgeImage cut) {
        log.info("Cutting proxy to {}.", cut == active ? "active" : "fallback");
        cut.proxy.setConnectionCut(true);
        log.info("Proxy cut.");
    }

    private long uncutAndAwait(EdgeImage uncut) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("Uncutting proxy to {}.", uncut == active ? "active" : "fallback");
        uncut.proxy.setConnectionCut(false);
        log.info("Proxy uncut.");
        pulseListener.reset();
        pulseListener.getTagUpdate().get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
        log.info("Connection reestablished.");
        return System.currentTimeMillis();
    }

}