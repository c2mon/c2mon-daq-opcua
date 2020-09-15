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

import cern.c2mon.daq.opcua.SpringTestBase;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;

@Slf4j
public abstract class EdgeTestBase extends SpringTestBase {

    public static final int IOTEDGE = 50000;
    private static final Network network = Network.newNetwork();
    private static final ToxiproxyContainer toxiProxyContainer;
    public static EdgeImage active;
    public static EdgeImage fallback;
    public static Collection<Toxic> activeCutToxics = new ArrayList<>();
    public static Collection<Toxic> fallbackCutToxics = new ArrayList<>();

    public static int counter = 0;

    static {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
    }

    @BeforeAll
    public static void setupContainers () {
        log.info("Starting EdgeTestBase containers");
        toxiProxyContainer.start();
        active = new EdgeImage(toxiProxyContainer, network);
        fallback = new EdgeImage(toxiProxyContainer, network);
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
        log.info("Disabling Fallback, enabling Active");
        uncut(active);
        cut(fallback);
    }

    protected static void cut(EdgeImage img) {
        Collection<Toxic> toxics = img == active ? activeCutToxics : fallbackCutToxics;
        String imgname = img == active ? "active" : "fallback";
        String name;
        if (toxics.size() < 2) {
            for (ToxicDirection dir : ToxicDirection.values()) {
                name = imgname + "_" + dir.name() + "_Cut_" + counter++;
                try {
                    toxics.add(img.proxy.toxics().limitData(name, dir, 0));
                    log.info("Successfully added toxic {} to {}.", name, imgname);
                } catch (IOException e) {
                    log.error("Could not add toxic {} to {}.", name, imgname, e);
                }
            }
        }
    }

    protected static void uncut(EdgeImage img) {
        Collection<Toxic> toxics = img == active ? activeCutToxics : fallbackCutToxics;
        if (toxics.size() == 0) {
            return;
        }
        for (Toxic t : toxics) {
            try {
                t.remove();
                log.info("Successfully removed toxic {}.", t.getName());
            } catch (IOException e) {
                log.error("Could not remove toxic! {}.", t.getName(), e);
            }
        }
        toxics.clear();
    }

    protected static <T> T waitUntilRegistered (CompletableFuture<T> future, EdgeImage img, boolean cut) throws InterruptedException, ExecutionException, TimeoutException {
        log.info(cut ? "Cutting connection." : "Reestablishing connection.");
        if (cut) {
            cut(img);
        } else {
            uncut(img);
        }
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
        public String uri;

        public EdgeImage(ToxiproxyContainer proxyContainer, Network network) {
            image.withNetwork(network).start();
            proxy = proxyContainer.getProxy(image, IOTEDGE);
            uri = "opc.tcp://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort();
            log.info("Edge server ready at {}. ", uri);
        }
    }
}
