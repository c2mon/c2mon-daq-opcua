package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * In Cold Failover mode a client can only connect to one server at a time. When the client loses connectivity with the
 * active server, it will attempt a connection to the redundant Server(s) which may or may not be available.  In this
 * situation the client may need to wait for the redundant server to become available. See UA Part 4 6.6.2.4.5.2.
 */
@Slf4j
@Component("coldFailover")
public class ColdFailover extends FailoverBase implements SessionActivityListener {

    private List<String> redundantServers = new ArrayList<>();
    private String currentUri;
    private Endpoint activeEndpoint;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;

    /**
     * Creates a new instance of ColdFailover
     * @param retryTemplate the retryTemplate to use to trigger failovers
     * @param config        the application properties
     */
    public ColdFailover(RetryTemplate retryTemplate, AppConfigProperties config) {
        super(config, retryTemplate);
    }


    /**
     * Connect to the healthiest endpoint of the redundant server set. Since in Cold Failover only one endpoint can be
     * active and healthy at a time, the first endpoint with a healthy service level that is encountered is deemed
     * active.
     * @param redundantAddresses an array containing the URIs of the servers within the redundant server set
     * @throws OPCUAException in case there is no healthy server to connect to in the redundant server set.
     */
    @Override
    public void initialize(Endpoint endpoint, String... redundantAddresses) throws OPCUAException {
        super.initialize(endpoint, redundantAddresses);
        activeEndpoint = endpoint;
        this.currentUri = activeEndpoint.getUri();
        executor = Executors.newSingleThreadScheduledExecutor();
        this.redundantServers = new ArrayList<>(Arrays.asList(redundantAddresses));
        this.redundantServers.add(currentUri);
        log.info("Initialized endpoint {} with redundant addresses {}.", endpoint.getUri(), this.redundantServers);
        // Only one server can be healthy at a time in cold redundancy
        if (readServiceLevel(activeEndpoint).compareTo(serviceLevelHealthLimit) < 0) {
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
        log.info("Stopping ColdFailover");
        executor.shutdown();
        redundantServers.clear();
        super.stop();
    }

    /**
     * Called periodically  when the client loses connectivity with the active Server until connection can be
     * reestablished. Attempt to create a connection a healthy redundant Server. Connection is attempted also to the
     * previously active server in case it starts back up and connection loss was temporary.
     * @throws OPCUAException if the no server could be connected to
     */
    @Override
    public void switchServers() throws OPCUAException {
        if (!stopped.get()) {
            log.info("Attempt to switch to next healthy server.");
            synchronized (stopped) {
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

    /**
     * Called when a session activates. This interrupts an active timeout to failover initiated in
     * onSessionInactive(session).
     * @param session the session that has activated.
     */
    @Override
    public void onSessionActive(UaSession session) {
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * The session becoming inactive means that the connection to the server has been lost. A timeout is started to see
     * it the connection loss is only momentary, otherwise it is assumed that the active servers have switches and a
     * failover process is initiated.
     * @param session the session that has become inactive
     */
    @Override
    public void onSessionInactive(UaSession session) {
        if (!stopped.get()) {
            log.info("Starting timeout on inactive session.");
            future = executor.schedule(() -> {
                log.info("Trigger server switch due to long disconnection");
                triggerServerSwitch();
            }, config.getTimeout(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected Endpoint currentEndpoint() {
        return activeEndpoint;
    }

    @Override
    protected List<Endpoint> passiveEndpoints() {
        return Collections.emptyList();
    }

    private boolean reconnectionSucceeded() throws OPCUAException {
        try {
            //move current URI to end of list of servers to try
            redundantServers.remove(currentUri);
            redundantServers.add(currentUri);
            return connectToHealthiestServerFinished();
        } catch (OPCUAException e) {
            log.info("Server not yet available.");
            disconnectAndProceed();
            throw e;
        }
    }

    private boolean connectToHealthiestServerFinished() throws OPCUAException {
        log.info("Current uri: {}, redundantAddressList, {}.", currentUri, redundantServers);
        UByte maxServiceLevel = UByte.valueOf(0);
        String maxServiceUri = null;
        for (final String nextUri : redundantServers) {
            if (stopped.get()) {
                return false;
            }
            log.info("Attempt connection to server at {}", nextUri);
            try {
                activeEndpoint.initialize(nextUri);
                final UByte nextServiceLevel = readServiceLevel(activeEndpoint);
                if (nextServiceLevel.compareTo(serviceLevelHealthLimit) >= 0) {
                    log.info("Connected to healthy server at {} with service level: {}.", nextUri, nextServiceLevel);
                    currentUri = nextUri;
                    return true;
                } else if (nextServiceLevel.compareTo(maxServiceLevel) > 0) {
                    maxServiceLevel = nextServiceLevel;
                    maxServiceUri = nextUri;
                }
                activeEndpoint.disconnect();
            } catch (OPCUAException e) {
                log.info("Could not connect to server at {}. Proceeding with next server.", nextUri);
                log.debug("Connection failed with exception: ", e);
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

    private void monitorConnection() {
        if (!stopped.get()) {
            activeEndpoint.manageSessionActivityListener(true, null);
            activeEndpoint.manageSessionActivityListener(true, this);
            try {
                activeEndpoint.subscribeWithCallback(config.getConnectionMonitoringRate(), connectionMonitoringNodes, this::monitoringCallback);
            } catch (EndpointDisconnectedException e) {
                log.debug("Setting up connection monitoring failed with exception: ", e);
                log.info("Session was closed, abort setting up connection monitoring.");
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
