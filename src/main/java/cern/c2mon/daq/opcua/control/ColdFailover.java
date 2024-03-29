/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.retry.support.RetryTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In Cold Failover mode a client can only connect to one server at a time. When the client loses connectivity with the
 * active server, it will attempt a connection to the redundant Server(s) which may or may not be available.  In this
 * situation the client may need to wait for the redundant server to become available. See UA Part 4 6.6.2.4.5.2.
 */
@Slf4j
@ManagedResource(objectName = "ColdFailover", description = "A handler redundant server sets in cold failover mode.")
public class ColdFailover extends FailoverBase {

    private final Queue<String> redundantServers = new ConcurrentLinkedDeque<>();
    private String currentUri;
    private Endpoint activeEndpoint;


    /**
     * Creates a new instance of ColdFailover
     * @param configProperties the application properties
     * @param alwaysRetryTemplate a retry template to continuously execute a method call until successful termination.
     */
    public ColdFailover(AppConfigProperties configProperties, RetryTemplate alwaysRetryTemplate) {
        super(configProperties, alwaysRetryTemplate);
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
        if (endpoint.getUri() == null) {
            throw new IllegalArgumentException("The Endpoint must be initialized!");
        }
        activeEndpoint = endpoint;
        this.currentUri = activeEndpoint.getUri();
        redundantServers.addAll(Arrays.asList(redundantAddresses));
        log.info("Initialized endpoint {} with redundant addresses {}.", endpoint.getUri(), redundantServers);
        redundantServers.add(currentUri);
        // Only one server can be healthy at a time in cold redundancy
        final UByte serviceLevel = readServiceLevel(activeEndpoint);
        log.info("Currently connected to endpoint with service level {}.", serviceLevel.intValue());
        if (serviceLevel.compareTo(serviceLevelHealthLimit) < 0) {
            log.info("Endpoint not healthy, attempt next URI.");
            activeEndpoint.disconnect();
            if (!connectToHealthiestServerSucceeded()) {
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
        super.stop();
        log.info("Stopping ColdFailover");
        redundantServers.clear();
    }

    /**
     * Called periodically  when the client loses connectivity with the active Server until connection can be
     * reestablished. Attempt to create a connection a healthy redundant Server. Connection is attempted also to the
     * previously active server in case it starts back up and connection loss was temporary.
     * @throws OPCUAException if the no connection could be established to any server
     */
    @Override
    @ManagedOperation(description = "Manually trigger a failover from the currently active server to the next. If no fallback servers are active, the currently active server will be reconnected. If no server is available, the DAQ will remain disconnected after this operation.")
    public void switchServers() throws OPCUAException {
        if (currentEndpoint() == null) {
            log.error("Cannot switch server, the Endpoint must be initialized first.");
        } else if (!stopped.get()) {
            log.info("Attempt to switch to next healthy server.");
            activeEndpoint.disconnect();
            //move current URI to end of list of servers to try
            redundantServers.remove(currentUri);
            redundantServers.add(currentUri);
            try {
                if (connectToHealthiestServerSucceeded()) {
                    activeEndpoint.recreateAllSubscriptions();
                    monitorConnection();
                }
            } catch (OPCUAException e) {
                log.info("No server currently available.", e);
                activeEndpoint.disconnect();
                throw e;
            }
        } else {
            log.info("Server was manually shut down.");
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

    private boolean connectToHealthiestServerSucceeded() throws OPCUAException {
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
                log.info("Server at {} has service level of {}.", nextUri, nextServiceLevel.intValue());
                if (nextServiceLevel.compareTo(serviceLevelHealthLimit) >= 0) {
                    log.info("Server is healthy!");
                    currentUri = nextUri;
                    return true;
                } else if (nextServiceLevel.compareTo(maxServiceLevel) >= 0) {
                    maxServiceLevel = nextServiceLevel;
                    maxServiceUri = nextUri;
                }
                activeEndpoint.disconnect();
            } catch (OPCUAException e) {
                log.info("Could not connect to server at {}. Proceeding with next server. ", nextUri, e);
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

    @Override
    protected void monitorConnection() throws OPCUAException {
        if (!stopped.get()) {
            activeEndpoint.setUpdateEquipmentStateOnSessionChanges(true);
        }
        super.monitorConnection();
    }
}
