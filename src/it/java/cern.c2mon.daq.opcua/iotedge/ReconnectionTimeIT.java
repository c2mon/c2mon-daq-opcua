package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.FailoverBase;
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
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;
import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_TOXI;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconnectionTimeIT extends EdgeTestBase {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
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

    private EdgeImage current;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
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

        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() throws InterruptedException {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        TimeUnit.MILLISECONDS.sleep(TIMEOUT_IT);
        shutdownAndAwaitTermination(executor);

        if (current != active) {
            log.info("Resetting fallback proxy {}", fallback.proxy);
            doWithTimeout(fallback, true);
            log.info("Resetting active proxy {}", active.proxy);
            try {
                CompletableFuture.runAsync(() -> doWithTimeout(active, false))
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
    public void mttrWithoutFailoverShouldBeLessThan1s() {
        log.info("mttrWithoutFailoverShouldBeLessThan1s");
        final double average = findAverageTime(new CountDownLatch(10), false);
        Assertions.assertTrue(average >= 0 && average < 1000);
    }

    @Test
    public void mttrWithFailoverShouldBeLessThan6s() {
        log.info("mttrWithFailoverShouldBeLessThan6s");
        final double average = findAverageTime(new CountDownLatch(10), true);
        Assertions.assertTrue(average >= 0 && average < 6000);
    }

    private double findAverageTime(CountDownLatch latch, boolean isFailover) {
        List<ConnectionRecord> connectionRecords = new ArrayList<>();
        try {
            executor.submit(() -> this.schedule(latch, isFailover, connectionRecords));
            latch.await();
        } catch (InterruptedException e) {
            log.info("Interrupted test bench. ", e);
            Thread.currentThread().interrupt();
        }
        final double average = connectionRecords.stream().map(ConnectionRecord::getDiff).mapToDouble(r -> r).average().orElse(-1.0);
        log.info("Connection Records for test {} failover: \n{}\n{}\n" +
                        "Average duration of reconnection: {}\n",
                isFailover ? "with" : "without",
                String.format("%1$14s %2$14s %3$14s %4$14s", "Reestablished", "Reconnected", "Difference", "Redundant"),
                connectionRecords.stream().map(ConnectionRecord::toString).collect(Collectors.joining("\n")),
                average);
        return average;
    }

    private void schedule(CountDownLatch latch, boolean isFailover, Collection<ConnectionRecord> records) {
        log.info("Test {} failover, {} remaining.", isFailover ? "with" : "without", latch.getCount());
        try {
            final ConnectionRecord connectionRecord = recordInstance(isFailover);
            records.add(connectionRecord);
            latch.countDown();
        } catch (Exception e) {
            log.info("Execution {} failed: ", latch.getCount(), e);
        }
        if (latch.getCount() > 0) {
            long interruptDelay = 1000L;
            executor.schedule(() -> schedule(latch, isFailover, records), interruptDelay, TimeUnit.MILLISECONDS);
        }
    }

    private ConnectionRecord recordInstance(boolean isFailover) throws InterruptedException, ExecutionException, TimeoutException {
        ConnectionRecord record;
        synchronized (this) {
            final boolean currentActive = current == active;
            if (isFailover) {
                log.info(currentActive ? "Failover from active to backup." : "Failover from backup to active.");
                final EdgeImage fallback = currentActive ? EdgeTestBase.fallback : active;
                record = changeConnection(current, fallback);
                current = fallback;
            } else {
                log.info("Temporarily cut connection on {}", currentActive ? "active" : "backup");
                record = changeConnection(current, current);
            }
        }
        return record;
    }

    private ConnectionRecord changeConnection(EdgeImage cut, EdgeImage uncut) throws InterruptedException, ExecutionException, TimeoutException {
        final boolean cutSuccess = doWithTimeout(cut, true);
        log.info("Cut connection {}.", cutSuccess ? "successfully" : "unsuccessfully");

        if (cut != uncut) {
            log.info("Triggering server switch.");
            executor.submit(() -> ReflectionTestUtils.invokeMethod(((FailoverBase) coldFailover), "triggerServerSwitch"));
        }

        long reestablished = System.currentTimeMillis();
        pulseListener.reset();

        doAndWait(pulseListener.getTagUpdate(), uncut, false);
        final ConnectionRecord record = ConnectionRecord.of(reestablished, System.currentTimeMillis(), cut != uncut);
        log.info("Record: {}", record);
        return record;
    }
}