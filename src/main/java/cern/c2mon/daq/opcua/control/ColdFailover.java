package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * In Cold Failover mode a client can only connect to one server at a time. When the client loses connectivity with the
 * active server, it will attempt a connection to the redundant Server(s) which may or may not be available.  In this
 * situation the client may need to wait for the redundant server to become available. See UA Part 4 6.6.2.4.5.2.
 */
@Slf4j
@Component("coldFailover")
public class ColdFailover extends FailoverBase implements SessionActivityListener {

    private final List<String> redundantAddresses = new ArrayList<>();
    private String currentUri;
    private Endpoint activeEndpoint;

    @Value("#{@appConfigProperties.getConnectionMonitoringRate()}")
    private int monitoringRate;

    @Value("#{@appConfigProperties.getTimeout()}")
    private int timeout;

    private CompletableFuture<Void> reconnected = new CompletableFuture<>();

    public ColdFailover(ApplicationContext applicationContext, RetryDelegate retryDelegate) {
        super(retryDelegate, applicationContext);
    }


    /**
     * Connect to the healthiest endpoint of the redundant server set. Since in Cold Failover only one endpoint can be
     * active and healthy at a time, the first endpoint with a healthy service level that is encountered is deemed
     * active.
     * @param redundantAddresses an array containing the URIs of the servers within the redundant server set
     * @throws OPCUAException in case there is no healthy server to connect to in the redundant server set.
     */
    @Override
    public void initialize(Endpoint endpoint, String[] redundantAddresses) throws OPCUAException {
        super.initialize(endpoint, redundantAddresses);
        activeEndpoint = endpoint;
        this.currentUri = activeEndpoint.getUri();
        Collections.addAll(this.redundantAddresses, redundantAddresses);
        this.redundantAddresses.add(currentUri);
        // Only one server can be healthy at a time in cold redundancy
        if (readServiceLevel(activeEndpoint).compareTo(SERVICE_LEVEL_HEALTH_LIMIT) < 0) {
            disconnectAndProceed();
            if (!connectToHealthiestServerFinished()) {
                log.info("Controller stopped. Stopping initialization process.");
                return;
            }
        } else {
            log.info("Connected to healthy server in cold redundancy mode.");
        }
        monitorConnection();
    }

    @Override
    public void stop() {
        reconnected.complete(null);
        super.stop();
    }

    @Override
    protected Endpoint currentEndpoint() {
        return activeEndpoint;
    }

    @Override
    protected List<Endpoint> passiveEndpoints() {
        return Collections.emptyList();
    }

    /**
     * Called periodically  when the client loses connectivity with the active Server until connection can be
     * reestablished. Attempt to create a connection a healthy redundant Server. Connection is attempted also to the
     * previously active server in case it starts back up and connection loss was temporary.
     * @throws OPCUAException if the no server could be connected to
     */
    @Override
    public synchronized void switchServers() throws OPCUAException {
        if (!stopped.get()) {
            synchronized (this) {
                log.info("Attempt to switch to next healthy server.");
                disconnectAndProceed();
                if (reconnectionSucceeded()) {
                    activeEndpoint.recreateAllSubscriptions();
                    monitorConnection();
                }
            }
        } else {
            log.info("Server was manually shut down.");
        }
    }

    @Override
    public void onSessionInactive(UaSession session) {
        if (!stopped.get()) {
            log.info("Starting timeout on inactive session.");
            reconnected = new CompletableFuture<Void>()
                    .orTimeout(timeout, TimeUnit.MILLISECONDS)
                    .exceptionally(t -> {
                        log.info("Trigger server switch due to long disconnection");
                        triggerServerSwitch();
                        return null;
                    });
        }
    }

    @Override
    public void onSessionActive(UaSession session) {
        reconnected.complete(null);
    }

    private boolean reconnectionSucceeded() throws OPCUAException {
        try {
            //move current URI to end of list of servers to try
            redundantAddresses.remove(currentUri);
            redundantAddresses.add(currentUri);
            return connectToHealthiestServerFinished();
        } catch (OPCUAException e) {
            log.info("Server not yet available.");
            disconnectAndProceed();
            throw e;
        }
    }

    private boolean connectToHealthiestServerFinished() throws OPCUAException {
        log.info("Current uri: {}, redundantAddressList, {}.", currentUri, redundantAddresses);
        UByte maxServiceLevel = UByte.valueOf(0);
        String maxServiceUri = null;
        for (final String nextUri : redundantAddresses) {
            if (stopped.get()) {
                return false;
            }
            log.info("Attempt connection to server at {}", nextUri);
            try {
                activeEndpoint.initialize(nextUri);
                final UByte nextServiceLevel = readServiceLevel(activeEndpoint);
                if (nextServiceLevel.compareTo(SERVICE_LEVEL_HEALTH_LIMIT) >= 0) {
                    log.info("Connected to healthy server at {} with service level: {}.", nextUri, nextServiceLevel);
                    currentUri = nextUri;
                    return true;
                } else if (nextServiceLevel.compareTo(maxServiceLevel) > 0) {
                    maxServiceLevel = nextServiceLevel;
                    maxServiceUri = nextUri;
                }
                activeEndpoint.disconnect();
            } catch (OPCUAException ignored) {
                log.info("Could not connect to server at {}. Proceeding with next server.", nextUri);
            }
        }
        if (maxServiceUri != null) {
            currentUri = maxServiceUri;
            activeEndpoint.initialize(currentUri);
            log.info("Connected to server at {} with highest but still unhealthy service level: {}.", currentUri, maxServiceLevel);
            return true;
        } else {
            throw new CommunicationException(ExceptionContext.NO_REDUNDANT_SERVER);
        }
    }

    private synchronized void monitorConnection() {
        if (!stopped.get()) {
            activeEndpoint.manageSessionActivityListener(true, null);
            activeEndpoint.manageSessionActivityListener(true, this);
            try {
                activeEndpoint.subscribeWithCallback(monitoringRate, connectionMonitoringNodes, this::monitoringCallback);
            } catch (OPCUAException e) {
                log.info("An error occurred when setting up connection monitoring.", e);
            }
        }
    }

    private void disconnectAndProceed() {
        if (activeEndpoint != null) {
            activeEndpoint.disconnect();
        }
    }
}
