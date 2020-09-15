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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.retry.support.RetryTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An abstract base class of a controller that manages the connection to servers in a redundant server set and is
 * capable of failing over in between these servers in case of error. It offers redundancy-specific functionality that
 * is shared in between different failover modes of the OPC UA redundancy model. Concrete Failover modes should
 * implement specific behavior on initial connection by implementing the {@link ConcreteController}'s initialize()
 * method, and on failover by implementing  the {@link FailoverController}'s switchServers() method.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class FailoverBase extends ControllerBase implements FailoverController, SessionActivityListener {

    protected static final UByte serviceLevelHealthLimit = UByte.valueOf(200);
    protected static final List<ItemDefinition> connectionMonitoringNodes = Arrays.asList(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));
    /**
     * Setting this flat to true ends ongoing failover procedures after the controller was stopped.
     */
    protected final AtomicBoolean stopped = new AtomicBoolean(true);
    /**
     * This flag is used to ensure that only one failover procedure is in progress as a time. This is relevant since
     * there are different ways of triggering a failover, which may occur simultaneously.
     */
    protected final AtomicBoolean listening = new AtomicBoolean(true);
    protected final AppConfigProperties configProperties;
    private final RetryTemplate alwaysRetryTemplate;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> triggerFailoverFuture;

    /**
     * Initialize supervision and connection monitoring to the active server.
     * @param endpoint      the currently connected {@link Endpoint}
     * @param redundantUris the addresses of the servers in the redundant server set not including the active server
     *                      URI
     * @throws OPCUAException if an error occurred when setting up connection monitoring
     */
    public void initialize(Endpoint endpoint, String... redundantUris) throws OPCUAException {
        listening.set(true);
        stopped.set(false);
    }

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    @Override
    public void stop() {
        log.info("Disconnecting... ");
        stopped.set(true);
        listening.set(false);
        executor.shutdown();
        super.stop();
    }


    /**
     * Called when a session activates. This interrupts an active timeout to failover initiated in
     * onSessionInactive(session).
     * @param session the session that has activated.
     */
    @Override
    public void onSessionActive(UaSession session) {
        if (triggerFailoverFuture != null && !triggerFailoverFuture.isCancelled()) {
            triggerFailoverFuture.cancel(false);
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
        boolean readyForFailover = !listening.get() || triggerFailoverFuture == null || triggerFailoverFuture.isCancelled();
        if (readyForFailover && !stopped.get()) {
            log.info("Starting timeout on inactive session.");
            triggerFailoverFuture = executor.schedule(() -> {
                log.info("Trigger server switch due to long disconnection");
                triggerServerSwitch();
            }, configProperties.getFailoverDelay(), TimeUnit.MILLISECONDS);
        }
    }

    protected void triggerServerSwitch() {
        synchronized (listening) {
            if (listening.getAndSet(false) && !stopped.get()) {
                currentEndpoint().setUpdateEquipmentStateOnSessionChanges(false);
                try {
                    alwaysRetryTemplate.execute(retryContext -> {
                        log.info("Server switch attempt nr {}.", retryContext.getRetryCount());
                        switchServers();
                        return null;
                    });
                } catch (OPCUAException e) {
                    log.error("Retry logic is not correctly configured! Retries ceased.", e);
                }
                listening.set(true);
            } else if (!stopped.get()) {
                log.info("Failover is already in process.");
            }
        }
    }

    protected void monitoringCallback(UaMonitoredItem item) {
        if (!stopped.get()) {
            final NodeId nodeId = item.getReadValueId().getNodeId();
            Consumer<DataValue> valueConsumer = v -> {};
            if (nodeId.equals(Identifiers.Server_ServiceLevel)) {
                valueConsumer = v -> serverSwitchConsumer(v, b -> b.compareTo(serviceLevelHealthLimit) < 0, UByte.class);
            } else if (nodeId.equals(Identifiers.ServerState)) {
                valueConsumer = v -> serverSwitchConsumer(v, s -> !s.equals(ServerState.Running) && !s.equals(ServerState.Unknown), ServerState.class);
            }
            item.setValueConsumer(valueConsumer);
        }
    }

    protected <T> void serverSwitchConsumer(DataValue value, Predicate<T> predicate, Class<T> type) {
        final Object o = value.getValue().getValue();
        if (o.getClass().isAssignableFrom(type)) {
            final T update = type.cast(o);
            if (predicate.test(update)) {
                log.info("Update {} triggered a server switch...", update.toString());
                triggerServerSwitch();
            }
        } else {
            log.error("Received update for ServerSwitchConsumer for class {} with of class {}.", type.getName(), o.getClass().getName());
        }
    }

    /**
     * See UA Part 4, 6.6.2.4.2, Table 10.
     * @param endpoint The endpoint whose serviceLevel to read
     * @return the endpoint's service level, or a service level of 0 if unavailable.
     */
    protected UByte readServiceLevel(Endpoint endpoint) {
        try {
            final Object value = endpoint.read(Identifiers.Server_ServiceLevel).getKey().getValue();
            return UByte.class.isAssignableFrom(value.getClass()) ? (UByte) value : UByte.valueOf(0);
        } catch (OPCUAException e) {
            log.debug("Error reading service level from endpoint {}. ", endpoint.getUri(), e);
            return UByte.valueOf(0);
        }
    }

    /**
     * Subscribes to the ServerState and ServiceLevel nodes with a callback triggering a failover in case the nodes
     * report unhealthy states. Also listens to the session activity and triggers a failover after the configured
     * failoverDelay if the session has not reactivated. The latter is skipped if the the FailoverDelay is negative.
     * @throws OPCUAException if the subscription creation failed.
     */
    protected void monitorConnection() throws OPCUAException {
        if (stopped.get()) {
            log.info("The endpoint was stopped, skipping connection monitoring.");
        } else {
            log.info("Setting up monitoring");
            if (configProperties.getFailoverDelay() >= 0) {
                currentEndpoint().manageSessionActivityListener(true, this);
            }
            currentEndpoint().subscribeWithCallback(configProperties.getConnectionMonitoringRate(), connectionMonitoringNodes, this::monitoringCallback);
        }
    }
}
