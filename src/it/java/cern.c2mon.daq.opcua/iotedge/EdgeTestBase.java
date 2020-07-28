package cern.c2mon.daq.opcua.iotedge;

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

import static cern.c2mon.daq.opcua.testutils.TestUtils.TIMEOUT_IT;

@Slf4j
public abstract class EdgeTestBase {

    public static final int IOTEDGE  = 50000;
    private static final Network network = Network.newNetwork();
    private static final ToxiproxyContainer toxiProxyContainer;
    public static EdgeImage active;
    public static EdgeImage fallback;

    static {
        toxiProxyContainer = new ToxiproxyContainer().withNetwork(network);
    }

    @BeforeAll
    public static void setupContainers() {
        log.info("Starting EdgeTestBase containers");
        toxiProxyContainer.start();
        active = new EdgeImage(toxiProxyContainer, network);
        fallback = new EdgeImage(toxiProxyContainer, network);
        doWithTimeout(fallback, true);
    }

    @AfterAll
    public static void stopContainers() {
        log.info("Stopping EdgeTestBase containers");
        toxiProxyContainer.stop();
        active.image.stop();
        fallback.image.stop();

    }

    protected static boolean doWithTimeout(EdgeImage img, boolean cut) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (cut) {
                        // if timeout is 0, data is be delayed until the toxic is removed
                        img.proxy.toxics().timeout("TIMEOUT_UPSTREAM", ToxicDirection.UPSTREAM, 0);
                        img.proxy.toxics().timeout("TIMEOUT_DOWNSTREAM", ToxicDirection.DOWNSTREAM, 0);
                    } else {
                        for (Toxic toxic : img.proxy.toxics().getAll()) {
                            toxic.remove();
                        }
                    }
                    return true;
                } catch (IOException e) {
                    log.error("Error {} connection.", cut ? "cutting" : "restoring", e);
                    return false;
                }
            }).get(TIMEOUT_IT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Error {} connection.", cut ? "cutting" : "restoring", e);
        }
        return false;
    }

    protected static <T> T doAndWait(CompletableFuture<T> future, EdgeImage img, boolean cut) throws InterruptedException, ExecutionException, TimeoutException {
        log.info(cut ? "Cutting connection." : "Reestablishing connection.");
       doWithTimeout(img, cut);
        return future.thenApply(c -> {
            log.info(cut ? "Connection cut." : "Connection reestablished.");
            return c;
        }).get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES);
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
}