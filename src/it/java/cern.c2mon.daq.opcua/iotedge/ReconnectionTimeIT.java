package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.DataTagHandler;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.ConnectionRecord;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.ToxicList;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;
import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_TOXI;

@Slf4j
@Testcontainers
@TestPropertySource(locations = "classpath:failover.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconnectionTimeIT extends EdgeTestBase {

    private static Long[] RATES = new Long[] {1L, 20L, 50L, 100L, 150L, 200L, 400L};
    private static Integer[] LATENCIES = new Integer[] {1, 20, 50, 100, 150, 200, 400};
    private static Integer[] SLICE_SIZES = new Integer[] {50, 100, 150, 200, 400};
    private static Integer SLICER_DELAY = 20;

    private static int RUNS_PER_TEST = 2;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    TestListeners.Pulse pulseListener;
    IDataTagHandler tagHandler;
    ConcreteController coldFailover;
    Controller controllerProxy;
    private EdgeImage current;

    @BeforeEach
    public void setupEndpoint() throws InterruptedException, ExecutionException, TimeoutException, OPCUAException {
        log.info("############ SET UP ############");
        super.setupEquipmentScope();
        current = active;
        pulseListener = new TestListeners.Pulse();
        tagHandler = ctx.getBean(DataTagHandler.class);
        controllerProxy = ctx.getBean(Controller.class);
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);
        FailoverIT.mockColdFailover();
        controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
        coldFailover = (ConcreteController) ReflectionTestUtils.getField(controllerProxy, "controller");
        tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.RandomUnsignedInt32.createDataTag()));
        pulseListener.getTagUpdate().get(0).get(TIMEOUT_TOXI, TimeUnit.SECONDS);
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
        resetImageConnectivity();
        log.info("Done cleaning up.");
    }


    @Test
    public void mttrByLatencyWithoutFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Latency";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, LATENCIES, o -> o.toxicList.latency(o.name, o.dir, o.arg).setJitter(o.arg/10), false);
        log. info("MTTR for Latency with Failover");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByBandwidthWithoutFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Bandwidth";
        log.info(testName);
        Map<Long, Double> averages = getAverages(testName, RATES, o -> o.toxicList.bandwidth(o.name, o.dir, o.arg), false);
        log. info("MTTR for Bandwidth with Failover");
        printResults(averages, Long::compareTo);
    }

    @Test
    public void mttrBySlicerWithoutFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, 0).setSizeVariation(o.arg/10), false);
        log. info("MTTR for Slicer without delay");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByDelaySlicerWithoutFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Delay_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, SLICER_DELAY).setSizeVariation(o.arg/10), false);
        log. info("MTTR for Slicer with delay");
        printResults(averages, Integer::compareTo);
    }



    @Test
    public void mttrByLatencyWithFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Latency";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, LATENCIES, o -> o.toxicList.latency(o.name, o.dir, o.arg), true);
        log. info("MTTR for Latency with Failover");
        printResults(averages, Integer::compareTo);
    }

    @Ignore
    public void mttrByBandwidthWithFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Bandwidth";
        log.info(testName);
        Map<Long, Double> averages = getAverages(testName, RATES, o -> o.toxicList.bandwidth(o.name, o.dir, o.arg), true);
        log. info("MTTR for Bandwidth with Failover");
        printResults(averages, Long::compareTo);
    }

    @Ignore
    public void mttrBySlicerWithFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, 0), true);
        log. info("MTTR for Slicer without delay");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByDelaySlicerWithFailover() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Delay_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, SLICER_DELAY), true);
        log. info("MTTR for Slicer with delay");
        printResults(averages, Integer::compareTo);
    }



    @AllArgsConstructor
    public static class AverageArg<T> {
        String name;
        T arg;
        ToxicDirection dir;
        ToxicList toxicList;
    }

    @FunctionalInterface
    public interface ThrowingFunc<T1,E extends Throwable,R>{
        R apply(T1 t1) throws E;
    }

    private <T, S extends Toxic> Map<T, Double> getAverages(String testName, T[] values, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction, boolean triggerFailover) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Map<T, Double> averages = new ConcurrentHashMap<>();
        for (T v : values) {
            log.info("Test with {}", v.toString());
            for (ToxicDirection direction : ToxicDirection.values()) {
                Toxic tFallback = toxicAction.apply(new AverageArg<>(testName + "_Fallback_" + direction.name() + "_" + v, v, direction, fallback.proxy.toxics()));
                Toxic tActive = toxicAction.apply(new AverageArg<>(testName + "_Active_" + direction.name() + "_" + v, v, direction, active.proxy.toxics()));
                double avg = findAverageTime(new CountDownLatch(RUNS_PER_TEST), testName, triggerFailover);
                averages.put(v, avg);
                uncut(active);
                doWithTimeout(tActive);
                log.info("Removed toxic {}", tActive.getName());
                doWithTimeout(tFallback);
                log.info("Removed toxic {}", tFallback.getName());
                cut(fallback); // cut a proxy only after removing the previous proxies, it may hang forever: https://github.com/Shopify/toxiproxy/pull/168
            }
        }
        return averages;
    }

    private <T> void printResults(Map<T, Double> averages, Comparator<T> c) {
        log.info(String.format("\n %1$14s %2$14s", "Toxicity", "MTTR \n") + averages.entrySet().stream()
                .sorted((t1, t2) -> c.compare(t1.getKey(), t2.getKey()))
                .map(s -> String.format("%1$14s %2$14s", s.getKey().toString(), s.getValue().toString()))
                .collect(Collectors.joining("\n")));

    }

    private void doWithTimeout (Toxic toxic) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.runAsync(() -> {
            try {
                toxic.remove();
            } catch (IOException e) {
                log.error("Could not remove Toxic {} ", toxic.getName(), e);
                throw new CompletionException(e);
            }
        }).get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
    }


    private double findAverageTime(CountDownLatch latch, String testName, boolean triggerFailover) {
        List<ConnectionRecord> connectionRecords = new ArrayList<>();
        try {
            executor.submit(() -> this.schedule(latch, triggerFailover, connectionRecords));
            latch.await();
        } catch (InterruptedException e) {
            log.info("Interrupted test bench. ", e);
            Thread.currentThread().interrupt();
        }
        final double average = connectionRecords.stream().map(ConnectionRecord::getDiff).mapToDouble(r -> r).average().orElse(-1.0);
        log.info("Connection Records for test {}: \n{}\n{}\n" +
                        "Average duration of reconnection: {}\n",
                testName,
                String.format("%1$10s : %2$10s : %3$10s : %4$10s", "Reestablished", "Reconnected", "Difference", "Redundant"),
                connectionRecords.stream().map(ConnectionRecord::toString).collect(Collectors.joining("\n")),
                average);
        return average;
    }

    private void schedule(CountDownLatch latch, boolean triggerFailover, Collection<ConnectionRecord> records) {
        log.info("Test {} failover, {} remaining.", triggerFailover ? "with" : "without", latch.getCount());
        try {
            final ConnectionRecord connectionRecord = recordInstance(triggerFailover);
            records.add(connectionRecord);
            latch.countDown();
        } catch (Exception e) {
            log.info("Execution {} failed: ", latch.getCount(), e);
        }
        if (latch.getCount() > 0) {
            long interruptDelay = 1000L;
            executor.schedule(() -> schedule(latch, triggerFailover, records), interruptDelay, TimeUnit.MILLISECONDS);
        }
    }

    private ConnectionRecord recordInstance(boolean triggerFailover) throws InterruptedException, ExecutionException, TimeoutException {
        ConnectionRecord record;
        synchronized (this) {
            final boolean currentActive = current == active;
            if (triggerFailover) {
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
        cut(cut);
        if (cut != uncut) {
            log.info("Triggering server switch.");
            executor.submit(() -> ReflectionTestUtils.invokeMethod(((FailoverBase) coldFailover), "triggerServerSwitch"));
        }

        long reestablished = System.currentTimeMillis();
        pulseListener.reset();

        waitUntilRegistered(pulseListener.getTagUpdate().get(0), uncut, false);
        final ConnectionRecord record = ConnectionRecord.of(reestablished, System.currentTimeMillis(), cut != uncut);
        log.info("Record: {}", record);
        return record;
    }
}