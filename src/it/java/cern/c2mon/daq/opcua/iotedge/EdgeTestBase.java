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

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.SpringTestBase;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;

@Slf4j
public abstract class EdgeTestBase extends SpringTestBase {

    public static final int IOTEDGE = 50000;
    private static final Network network = Network.newNetwork();
    private static final ToxiproxyContainer toxiProxyContainer;
    public static EdgeImage active;
    public static EdgeImage fallback;

    static {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
    }

    @BeforeAll
    public static void setupContainers () {
        log.info("Starting EdgeTestBase containers");
        toxiProxyContainer.start();
        active = new EdgeImage(toxiProxyContainer, network, "active");
        fallback = new EdgeImage(toxiProxyContainer, network, "fallback");
        resetImageConnectivity();
    }

    @AfterAll
    public static void stopContainers () {
        log.info("Stopping EdgeTestBase containers");
        toxiProxyContainer.stop();
        active.image.stop();
        fallback.image.stop();
    }

    protected static void resetImageConnectivity () {
        cutConnection(active, false);
        cutConnection(fallback, true);
    }

    protected static void cutConnection (EdgeImage img, boolean cut) {
        // Don't use the ToxiproxyContainer.setConnectionCut method - if the connection is already cut and this method is called, the proxy hangs forever.
        log.info("{} Image is currently {} cut.", img.getName(), img.isCurrentlyCut.get() ? "" : "not");
        try {
            if (cut && !img.isCurrentlyCut.get()) {
                log.info("Cutting connection on {} image.", img.getName());
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Adding CUT_CONNECTION_DOWNSTREAM toxic");
                        img.proxy.toxics().limitData("CUT_CONNECTION_DOWNSTREAM", ToxicDirection.DOWNSTREAM, 0L);
                        log.info("Adding CUT_CONNECTION_UPSTREAM toxic");
                        img.proxy.toxics().limitData("CUT_CONNECTION_UPSTREAM", ToxicDirection.UPSTREAM, 0L);
                    } catch (IOException e) {
                        log.error("Could not add Toxic, ", e);
                        throw new CompletionException(e);
                    }
                }).get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
                img.isCurrentlyCut.set(true);
            } else if (!cut && img.isCurrentlyCut.get()) {
                log.info("Uncutting connection on {} image.", img.getName());
                removeWithTimeout(img.proxy.toxics().get("CUT_CONNECTION_DOWNSTREAM"), 0);
                removeWithTimeout(img.proxy.toxics().get("CUT_CONNECTION_UPSTREAM"), 0);
                img.isCurrentlyCut.set(false);
            } else {
                log.info("{} Image is already in the desired state.", img.getName());
            }
        } catch (IOException | InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException("Could not control proxy", e);
        }
    }

    public static void removeWithTimeout (Toxic toxic, int counter) throws InterruptedException, TimeoutException, ExecutionException {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    toxic.remove();
                } catch (IOException e) {
                    log.error("Could not remove Toxic {} ", toxic.getName(), e);
                    throw new CompletionException(e);
                }
            }).get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            if (counter < 3) {
                log.error("Could not remove Toxic {}. Sleep and retry... ", toxic.getName(), e);
                TimeUnit.MILLISECONDS.sleep(4000L);
                removeWithTimeout(toxic, ++counter);
            }
            log.error("Could not remove Toxic {} ", toxic.getName(), e);
            throw e;
        }
    }

    protected static MessageSender.EquipmentState waitUntilRegistered (TestListeners.TestListener l, EdgeImage img, boolean cut) throws InterruptedException, ExecutionException, TimeoutException {
        log.info(cut ? "Cutting connection on {} image." : "Reestablishing connection on {} image.", img.getName());
        cutConnection(img, cut);
        return l.listen().thenApply(c -> {
            log.info(cut ? "Connection cut." : "Connection reestablished.");
            return c;
        }).get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    protected static <T> T waitUntilRegistered (CompletableFuture<T> future, EdgeImage img, boolean cut) throws InterruptedException, ExecutionException, TimeoutException {
        log.info(cut ? "Cutting connection on {} image." : "Reestablishing connection on {} image.", img.getName());
        cutConnection(img, cut);
        return future.thenApply(c -> {
            log.info(cut ? "Connection cut." : "Connection reestablished.");
            return c;
        }).get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
    }

    public static void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(TIMEOUT_IT, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(TIMEOUT_IT, TimeUnit.MILLISECONDS)) {
                    log.error("Server switch still running");
                }
            }
        } catch (InterruptedException ie) {
            log.error("Interrupted... ", ie);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Executor shut down");
    }

    public static class EdgeImage {

        public GenericContainer image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withExposedPorts(IOTEDGE);
        public ToxiproxyContainer.ContainerProxy proxy;

        @Getter
        private final String name;

        @Getter
        private String uri;

        public final AtomicBoolean isCurrentlyCut = new AtomicBoolean(false);

        public EdgeImage(ToxiproxyContainer proxyContainer, Network network, String name) {
            image.withNetwork(network).start();
            proxy = proxyContainer.getProxy(image, IOTEDGE);
            uri = "opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();
            log.info("Edge server ready at {}. ", uri);
            this.name = name;
        }
    }
}
