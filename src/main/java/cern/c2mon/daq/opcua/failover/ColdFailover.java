package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In Cold Failover mode a client can only connect to one server at a time. When the client loses connectivity with the
 * active server, it will attempt a connection to the redundant Server(s) which may or may not be available.  In this
 * situation the client may need to wait for the redundant server to become available. See UA Part 4 6.6.2.4.5.2.
 */
@Slf4j
@Lazy
@Component("coldFailover")
@NoArgsConstructor
public class ColdFailover extends FailoverBase {

    /** The endpoint at the head of the deque is one currently active. */
    protected final Deque<Map.Entry<String, Endpoint>> servers = new ConcurrentLinkedDeque<>();

    private Collection<SessionActivityListener> listeners;

    @Value("${app.connectionMonitoringRate}")
    private int monitoringRate;

    private static final AtomicBoolean listening = new AtomicBoolean(false);

    @Override
    public void initialize(Map.Entry<String, Endpoint> server, String[] redundantAddresses, Collection<SessionActivityListener> listeners) throws CommunicationException {
        // Clients are expected to connect to the server with the highest service level.
        this.listeners = listeners;
        servers.addFirst(server);
        disconnectAndProceed(server);
        Stream.of(redundantAddresses).forEach(s -> servers.addLast(Map.entry(s, nextEndpoint())));
        connectToHealthiestServer();
        monitorConnection();
    }

    @Override
    public synchronized void disconnect() {
        listening.set(false);
        disconnectAndProceed(servers.getFirst());
    }

    @Override
    public Endpoint currentEndpoint() {
        return servers.getFirst() != null ? servers.peek().getValue() : null;
    }

    /**
     * When the client loses connectivity with the active Server, it will attempt a connection to the redundant
     * Server(s). If those are not available, the client should wait for the redundant servers to become available.
     */
    @Override
    public void switchServers() {
        if (listening.getAndSet(false)) {
            final var oldServer = servers.removeFirst();
            disconnectAndProceed(oldServer);
            try {
                connectToHealthiestServer();
            } catch (CommunicationException e) {
                log.error("Wait for redundant servers to become available... ", e);
                try {
                    TimeUnit.MILLISECONDS.sleep(5000L);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                switchServers();
            }
            servers.addLast(oldServer);

            final boolean subscriptionError = mapper.getSubscriptionGroups().values().stream()
                    .noneMatch(e -> controller.subscribeToGroup(e, e.getTagIds().values()));
            if (subscriptionError) {
                log.error("Could not recreate any subscriptions. Connect to next server... ");
                switchServers();
            } else {
                log.info("Recreated subscriptions on server {}.", servers.getFirst().getKey());
                monitorConnection();
            }
        }
    }

    private void connectToHealthiestServer() throws CommunicationException {
        final var orderedByServiceLevel = servers.stream()
                .map(entry -> Map.entry(readServiceLevel(entry).intValue(), entry))
                .filter(e -> e.getKey() != 0)
                .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey()))
                .collect(Collectors.toList());
        for (var server : orderedByServiceLevel) {
            try {
                synchronized (this) {
                    server.getValue().getValue().initialize(server.getValue().getKey(), listeners);
                    servers.remove(server.getValue());
                    servers.addFirst(server.getValue());
                    log.info("Connected to server with URI {}.", server.getValue().getKey());
                    listening.set(true);
                }
                return;
            } catch (OPCUAException e) {
                log.error("Could not connect to server with URI {} and service level {}. Proceed with next server.", server.getValue().getKey(), server.getKey(), e);
            }
        }
        throw new CommunicationException(ExceptionContext.NO_REDUNDANT_SERVER);
    }

    /**
     * In cold failover a client can only connect to one server as a time.
     * @param serverEntry A Map.Entry containing the server's URI as key and the corresponding endpoint whose
     *                    serviceLevel to read
     * @return the server's current service level, or 0 if unavailable
     */
    @Override
    protected UByte readServiceLevel(Map.Entry<String, Endpoint> serverEntry) {
        UByte serviceLevel = UByte.valueOf(0);
        try {
            serverEntry.getValue().initialize(serverEntry.getKey(), listeners);
            serviceLevel = super.readServiceLevel(serverEntry);
            serverEntry.getValue().disconnect();
        } catch (OPCUAException e) {
            log.error("An error occurred upon reading the service level from a server. Consider server in maintenance", e);
        }
        return serviceLevel;
    }

    private void monitorConnection() {
        try {
            final var subscription = currentEndpoint().createSubscription(monitoringRate);
            currentEndpoint().subscribeItem(subscription, connectionMonitoringNodes, this::monitoringCallback);
            listening.set(true);
        } catch (OPCUAException e) {
            log.info("An error occurred when setting up connection monitoring.");
        }
    }

    private void disconnectAndProceed(Map.Entry<String, Endpoint> serverEntry) {
        try {
            if (currentEndpoint() != null) {
                currentEndpoint().disconnect();
            }
        } catch (OPCUAException e) {
            log.error("An error occurred disconnecting from the server at URI {}.", serverEntry.getKey(), e);
        }
    }
}
