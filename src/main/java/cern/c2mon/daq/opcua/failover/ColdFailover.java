package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In Cold Failover mode a client can only connect to one server at a time. When the client loses connectivity with the
 * active server, it will attempt a connection to the redundant Server(s) which may or may not be available.  In this
 * situation the client may need to wait for the redundant server to become available. See UA Part 4 6.6.2.4.5.2.
 */
@Slf4j
@Lazy
@Component("coldFailover")
public class ColdFailover extends FailoverBase {

    private static final AtomicBoolean listening = new AtomicBoolean(false);
    List<String> redundantAddresses;
    String currentUri;
    Endpoint endpoint;
    private Collection<SessionActivityListener> listeners;
    @Value("${app.connectionMonitoringRate}")
    private int monitoringRate;

    /**
     * Connect to the healthy endpoint of the set which. Since in Cold Failover only one endpoint can be active and
     * healthy at a time, the first endpoint with a healthy service level that is encountered is deemed active.
     * @param server             A Map.Entry containing the currently connected server URI and the corresponding
     *                           endpoint.
     * @param redundantAddresses an array containing the URIs of the servers within the redundant server set
     * @param listeners          The SessionActivityListeners to subscribe to the client when created.
     * @throws OPCUAException in case there is no healthy server to connect to in the redundant server set.
     */
    @Override
    public void initialize(Map.Entry<String, Endpoint> server, String[] redundantAddresses, Collection<SessionActivityListener> listeners) throws OPCUAException {

        // Clients are expected to connect to the server with the highest service level.
        this.redundantAddresses = new ArrayList<>();
        Collections.addAll(this.redundantAddresses, redundantAddresses);
        this.listeners = listeners;
        this.endpoint = server.getValue();
        this.currentUri = server.getKey();
        // only one server can be healthy at a time in cold redundancy
        if (readServiceLevel(endpoint).compareTo(ServiceLevels.Healthy.lowerLimit) < 0) {
            disconnectAndProceed();
            connectToHealthiestServer();
            this.redundantAddresses.add(currentUri);
        } else {
            log.info("Connected to healthy server in cold redundancy mode.");
        }
        monitorConnection();
    }

    @Override
    public synchronized void disconnect() {
        listening.set(false);
        disconnectAndProceed();
    }

    @Override
    public Endpoint currentEndpoint() {
        return endpoint;
    }

    /**
     * Attempt to create a connection to one of the redundant Server(s). If no healthy redundant server is not
     * available, the client waits for the redundant servers to become available. Called when the client loses
     * connectivity with the active Server.
     */
    @Override
    public void switchServers() throws OPCUAException {
        if (listening.getAndSet(false)) {
            log.info("Attempt to switch to next healthy server.");
            disconnectAndProceed();
            redundantAddresses.add(currentUri);
            try {
                connectToHealthiestServer();
            } catch (OPCUAException e) {
                disconnectAndProceed();
                log.info("Server not yet available.");
                redundantAddresses.remove(currentUri);
                listening.set(true);
                throw e;
            }

            final boolean subscriptionError = mapper.getSubscriptionGroups().values()
                    .stream()
                    .noneMatch(e -> controller.subscribeToGroup(e, e.getTagIds().values()));
            if (subscriptionError) {
                log.error("Could not recreate any subscriptions. Connect to next server... ");
                throw new CommunicationException(ExceptionContext.NO_REDUNDANT_SERVER);
            }
            log.info("Recreated subscriptions on server {}.", currentUri);
            monitorConnection();
        } else {
            log.info("Server was manually shut down.");
        }
    }

    private void connectToHealthiestServer() throws OPCUAException {
        UByte maxServiceLevel = UByte.valueOf(0);
        String maxServiceUri = null;
        for (var iterator = redundantAddresses.iterator(); iterator.hasNext(); ) {
            final String nextUri = iterator.next();
            try {
                endpoint.initialize(nextUri, listeners);
                final UByte nextServiceLevel = readServiceLevel(endpoint);
                if (nextServiceLevel.compareTo(maxServiceLevel) > 0) {
                    maxServiceLevel = nextServiceLevel;
                    maxServiceUri = nextUri;
                    //don't disconnect from last server, if it is the active one
                    if (!iterator.hasNext() || nextServiceLevel.compareTo(ServiceLevels.Healthy.lowerLimit) < 0) {
                        currentUri = nextUri;
                        return;
                    }
                }
                endpoint.disconnect();
            } catch (OPCUAException ignored) {
                log.info("Could not connect to server at {}. Proceed with next server.", nextUri);
            }
        }
        if (maxServiceUri != null) {
            currentUri = maxServiceUri;
            endpoint.initialize(currentUri, listeners);
        }
        throw new CommunicationException(ExceptionContext.NO_REDUNDANT_SERVER);
    }

    private void monitorConnection() {
        try {
            endpoint.subscribeWithCreationCallback(monitoringRate, connectionMonitoringNodes, this::monitoringCallback);
            listening.set(true);
        } catch (OPCUAException e) {
            log.info("An error occurred when setting up connection monitoring.");
        }
    }

    private void disconnectAndProceed() {
        try {
            if (this.endpoint != null) {
                this.endpoint.disconnect();
            }
        } catch (OPCUAException ignored) {
        }
    }

    /**
     * See UA Part 4, 6.6.2.4.2, Table 10.
     * @param endpoint The endpoint whose serviceLevel to read
     * @return the endpoint's service level, or a service level of 0 if unavailable.
     */
    private UByte readServiceLevel(Endpoint endpoint) {
        try {
            return (UByte) endpoint.read(Identifiers.Server_ServiceLevel).getKey().getValue();
        } catch (OPCUAException e) {
            return UByte.valueOf(0);
        }
    }
}
