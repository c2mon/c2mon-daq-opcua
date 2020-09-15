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

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
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
import org.junit.jupiter.api.AfterEach;
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
public abstract class ReconnectionTimeBase extends EdgeTestBase {

    protected static Long[] RATES = new Long[]{1L, 20L, 50L, 100L, 150L, 200L, 400L, 800L};
    protected static Integer[] LATENCIES = new Integer[]{10, 20, 50, 100, 150, 200, 400, 800};
    protected static Integer[] SLICE_SIZES = new Integer[]{10, 20, 50, 100, 150, 200, 400, 800};
    protected static Integer SLICER_DELAY = 20;
    protected static int RUNS_PER_TEST = 1;
    protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    protected TestListeners.Pulse pulseListener;
    protected IDataTagHandler tagHandler;
    protected ConcreteController controller;
    protected Controller controllerProxy;
    protected EdgeImage current;

    @AllArgsConstructor
    protected static class AverageArg<T> {
        String name;
        T arg;
        ToxicDirection dir;
        ToxicList toxicList;
    }

    @FunctionalInterface
    protected interface ThrowingFunc<T1, E extends Throwable, R> {
        R apply (T1 t1) throws E;
    }

    @AfterEach
    public void cleanUp () throws InterruptedException {
        log.info("############ CLEAN UP ############");
        controllerProxy.stop();
        TimeUnit.MILLISECONDS.sleep(TIMEOUT_IT);
        shutdownAndAwaitTermination(executor);
        resetImageConnectivity();
        log.info("Done cleaning up.");
    }

    protected abstract ConnectionRecord recordInstance () throws InterruptedException, ExecutionException, TimeoutException;

    protected abstract <T> Collection<Toxic> getToxics (String testName, T value, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction) throws IOException;

    protected void setUpBeans() throws ConfigurationException {
        super.setupEquipmentScope();
        current = active;
        pulseListener = new TestListeners.Pulse();
        tagHandler = ctx.getBean(DataTagHandler.class);
        controllerProxy = ctx.getBean(Controller.class);
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controllerProxy, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);
    }

    protected void connectAndSubscribe() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        controllerProxy.connect(Arrays.asList(active.getUri(), fallback.getUri()));
        controller = (ConcreteController) ReflectionTestUtils.getField(controllerProxy, "controller");
        tagHandler.subscribeTags(Collections.singletonList(EdgeTagFactory.RandomUnsignedInt32.createDataTag()));
        pulseListener.getTagUpdate().get(0).get(TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        pulseListener.setLogging(false);
    }

    protected <T> Map<T, Double> getAverages (String testName, T[] values, ThrowingFunc<AverageArg<T>, IOException, Toxic> toxicAction) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Map<T, Double> averages = new ConcurrentHashMap<>();
        for (T v : values) {
            log.info("Test {} with value {}.", testName, v.toString());
            Collection<Toxic> toxics = getToxics(testName, v, toxicAction);
            double avg = findAverageTime(new CountDownLatch(RUNS_PER_TEST), testName);
            averages.put(v, avg);
            uncut(active);
            for (Toxic t : toxics) {
                doWithTimeout(t);
                log.info("Removed toxic {}", t.getName());
            }
            cut(fallback); // cut a proxy only after removing the previous proxies, it may hang forever: https://github.com/Shopify/toxiproxy/pull/168
            toxics.clear();
        }
        return averages;
    }

    protected <T> void printResults (Map<T, Double> averages, Comparator<T> c) {
        log.info(String.format("\n %1$14s %2$14s", "Toxicity", "MTTR \n") + averages.entrySet().stream()
                .sorted((t1, t2) -> c.compare(t1.getKey(), t2.getKey()))
                .map(s -> String.format("%1$14s %2$14s", s.getKey().toString(), s.getValue().toString()))
                .collect(Collectors.joining("\n")));

    }

    protected void doWithTimeout (Toxic toxic) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture.runAsync(() -> {
            try {
                toxic.remove();
            } catch (IOException e) {
                log.error("Could not remove Toxic {} ", toxic.getName(), e);
                throw new CompletionException(e);
            }
        }).get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
    }


    protected double findAverageTime (CountDownLatch latch, String testName) {
        List<ConnectionRecord> connectionRecords = new ArrayList<>();
        try {
            executor.submit(() -> this.schedule(latch, connectionRecords));
            latch.await();
        } catch (InterruptedException e) {
            log.info("Interrupted test bench. ", e);
            Thread.currentThread().interrupt();
        }
        final double average = connectionRecords.stream()
                .map(ConnectionRecord::getDiff)
                .mapToDouble(r -> r)
                .average().orElse(-1.0);
        log.info("Connection Records for test {}: \n{}\n{}\nAverage duration of reconnection: {}\n",
                testName,
                String.format("%1$10s : %2$10s : %3$10s : %4$10s", "Reestablished", "Reconnected", "Difference", "Redundant"),
                connectionRecords.stream().map(ConnectionRecord::toString).collect(Collectors.joining("\n")),
                average);
        return average;
    }

    protected void schedule (CountDownLatch latch, Collection<ConnectionRecord> records) {
        log.info("{} remaining.", latch.getCount());
        try {
            final ConnectionRecord connectionRecord = recordInstance();
            records.add(connectionRecord);
            latch.countDown();
        } catch (Exception e) {
            log.info("Execution {} failed: ", latch.getCount(), e);
        }

        if (latch.getCount() > 0) {
            long interruptDelay = 1000L;
            executor.schedule(() -> schedule(latch, records), interruptDelay, TimeUnit.MILLISECONDS);
        }
    }

}
