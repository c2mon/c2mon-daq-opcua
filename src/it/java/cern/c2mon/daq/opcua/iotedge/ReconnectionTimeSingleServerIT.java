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

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.ConnectionRecord;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"c2mon.daq.opcua.retryDelay=250", "c2mon.daq.opcua.redundancyMode=NONE"}, locations = "classpath:application.properties")
public class ReconnectionTimeSingleServerIT extends ReconnectionTimeBase {

    @BeforeEach
    public void setUp () throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("############ SET UP ############");
        setUpBeans();
        connectAndSubscribe();
        log.info("############ TEST ############");
    }

    @Test
    public void baselineWithoutFailover () {
        String testName = "Baseline";
        log.info(testName);
        double avg = findAverageTime(new CountDownLatch(RUNS_PER_TEST), testName);
        log.info("MTTR Baseline without Failover: {}", avg);
    }

    @Test
    public void mttrByLatencyWithoutFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Latency";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, LATENCIES, o -> o.toxicList.latency(o.name, o.dir, o.arg).setJitter(o.arg / 10));
        log.info("MTTR for Latency with Failover");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByBandwidthWithoutFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Bandwidth";
        log.info(testName);
        Map<Long, Double> averages = getAverages(testName, RATES, o -> o.toxicList.bandwidth(o.name, o.dir, o.arg));
        log.info("MTTR for Bandwidth with Failover");
        printResults(averages, Long::compareTo);
    }

    @Test
    public void mttrBySlicerWithoutFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, 0).setSizeVariation(o.arg / 10));
        log.info("MTTR for Slicer without delay");
        printResults(averages, Integer::compareTo);
    }

    @Test
    public void mttrByDelaySlicerWithoutFailover () throws InterruptedException, ExecutionException, TimeoutException, IOException {
        String testName = "Delay_Slicer";
        log.info(testName);
        Map<Integer, Double> averages = getAverages(testName, SLICE_SIZES,
                o -> o.toxicList.slicer(o.name, o.dir, o.arg, SLICER_DELAY).setSizeVariation(o.arg / 10));
        log.info("MTTR for Slicer with delay");
        printResults(averages, Integer::compareTo);
    }

    @Override
    protected <T> Collection<Toxic> applyToxics (String testName, T value, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction) throws IOException {
        Collection<Toxic> toxics = new ArrayList<>();
        for (ToxicDirection direction : ToxicDirection.values()) {
            toxics.add(toxicAction.apply(new AverageArg<>(testName + "_Active_" + direction.name() + "_" + value, value, direction, active.proxy.toxics())));
        }
        return toxics;
    }

    @Override
    protected ConnectionRecord recordInstance () throws InterruptedException, ExecutionException, TimeoutException {
        log.info("Temporarily cut connection.");
        ConnectionRecord record;
        waitUntilRegistered(pulseListener.listen(), active, true);
        synchronized (this) {
            long reestablished = System.currentTimeMillis();
            pulseListener.reset();
            waitUntilRegistered(pulseListener.getTagUpdate().get(0), active, false);
            record = ConnectionRecord.of(reestablished, System.currentTimeMillis());
            log.info("Record: {}", record);
        }
        return record;
    }
}
