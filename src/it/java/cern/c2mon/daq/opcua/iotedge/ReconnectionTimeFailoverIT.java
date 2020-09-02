package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.ConnectionRecord;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Testcontainers
@TestPropertySource(locations = "classpath:failover.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ReconnectionTimeFailoverIT extends ReconnectionTimeBase {

    @BeforeEach
    public void setUp () throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("############ SET UP ############");
        setUpBeans();
        FailoverIT.mockColdFailover();
        connectAndSubscribe();
        log.info("############ TEST ############");
    }

    @Test
    public void baselineWithFailover () {
        String testName = "Baseline";
        log.info(testName);
        double avg = findAverageTime(new CountDownLatch(RUNS_PER_TEST), testName);
        log.info("MTTR Baseline with Failover: {}", avg);
    }

    @Test
    public void mttrByLatencyWithFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Latency";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, LATENCIES, o -> o.toxicList.latency(o.name, o.dir, o.arg));
        log.info("MTTR for Latency with Failover");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByBandwidthWithFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Bandwidth";
        log.info(testName);
        Map<Long, Double> averages = getAverages(testName, RATES, o -> o.toxicList.bandwidth(o.name, o.dir, o.arg));
        log.info("MTTR for Bandwidth with Failover");
        printResults(averages, Long::compareTo);
    }

    @Test
    public void mttrBySlicerWithFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, 0).setSizeVariation(o.arg / 10));
        log.info("MTTR for Slicer without delay");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByDelaySlicerWithFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Delay_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, SLICER_DELAY).setSizeVariation(o.arg / 10));
        log.info("MTTR for Slicer with delay");
        printResults(averages, Integer::compareTo);
    }

    @Override
    protected <T> Collection<Toxic> getToxics (String testName, T value, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction) throws IOException {
        Collection<Toxic> toxics = new ArrayList<>();
        for (ToxicDirection direction : ToxicDirection.values()) {
            toxics.add(toxicAction.apply(new AverageArg<>(testName + "_Active_" + direction.name() + "_" + value, value, direction, active.proxy.toxics())));
            toxics.add(toxicAction.apply(new AverageArg<>(testName + "_Fallback_" + direction.name() + "_" + value, value, direction, fallback.proxy.toxics())));
        }
        return toxics;
    }

    @Override
    protected ConnectionRecord recordInstance () throws InterruptedException, ExecutionException, TimeoutException {
        ConnectionRecord record;
        final boolean currentActive = current == active;
        log.info(currentActive ? "Failover from active to backup." : "Failover from backup to active.");
        final EdgeImage fallback = currentActive ? EdgeTestBase.fallback : active;
        record = changeConnection(current, fallback);
        current = fallback;
        return record;
    }

    private ConnectionRecord changeConnection (EdgeImage cut, EdgeImage uncut) throws InterruptedException, ExecutionException, TimeoutException {
        ConnectionRecord record;
        cut(cut);
        log.info("Triggering server switch.");
        executor.submit(() -> ReflectionTestUtils.invokeMethod(((FailoverBase) controller), "triggerServerSwitch"));
        log.info("RECORDING current time");
        synchronized (this) {
            long reestablished = System.currentTimeMillis();
            pulseListener.reset();
            waitUntilRegistered(pulseListener.listen(), uncut, false);
            record = ConnectionRecord.of(reestablished, System.currentTimeMillis());
        }
        pulseListener.getTagUpdate().get(0).join();
        log.info("Record: {}", record);
        return record;
    }
}