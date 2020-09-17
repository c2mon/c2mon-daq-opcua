/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.ConnectionRecord;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.toxic.Slicer;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES, o -> {
            Slicer slicer = o.toxicList.slicer(o.name, o.dir, o.arg, 0);
            slicer.setSizeVariation(o.arg / 10);
            return slicer;
        });
        log.info("MTTR for Slicer without delay");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByDelaySlicerWithFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Failover_Delay_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES, o -> {
            Slicer slicer = o.toxicList.slicer(o.name, o.dir, o.arg, SLICER_DELAY);
            slicer.setSizeVariation(o.arg / 10);
            return slicer;
        });
        log.info("MTTR for Slicer with delay");
        printResults(averages, Integer::compareTo);
    }

    @Override
    protected <T> Collection<Toxic> applyToxics (String testName, T value, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction) throws IOException {
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

    private synchronized ConnectionRecord changeConnection (EdgeImage cut, EdgeImage uncut) throws InterruptedException, ExecutionException, TimeoutException {
        ConnectionRecord record;
        cutConnection(cut, true);
        log.info("Triggering server switch.");
        executor.submit(() -> ReflectionTestUtils.invokeMethod(((FailoverBase) controller), "triggerServerSwitch"));
        log.info("RECORDING current time");
        long reestablished = System.currentTimeMillis();
        pulseListener.reset();
        waitUntilRegistered(pulseListener.getTagUpdate().get(0), uncut, false);
        record = ConnectionRecord.of(reestablished, System.currentTimeMillis());
        log.info("Record: {}", record);
        return record;
    }
}
