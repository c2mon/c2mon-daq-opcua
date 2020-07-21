package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An abstract base class of a controller managing the connection to servers in a redundant server set and capable of
 * failing over in between these servers in case of error. It offers between redundancy-specific functionality that is
 * shared in between different failover modes. Concrete Failover modes should implement specific behavior on initial
 * connection by implementing the {@link Controller}'s initialize() method, and on failover by implementing  the {@link
 * FailoverMode}'s switchServers() method.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class FailoverBase extends ControllerBase implements FailoverMode {

    protected static final AtomicBoolean listening = new AtomicBoolean(true);
    protected static UByte SERVICE_LEVEL_HEALTH_LIMIT = UByte.valueOf(200);
    protected static List<ItemDefinition> connectionMonitoringNodes = List.of(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));
    /** A flag indicating whether the controller was stopped. */
    protected final AtomicBoolean stopped = new AtomicBoolean(true);
    protected final RetryDelegate retryDelegate;
    private final ApplicationContext applicationContext;

    public void initialize(Endpoint endpoint, String[] redundantUris) throws OPCUAException {
        listening.set(true);
        stopped.set(false);
    }

    protected Endpoint nextEndpoint() {
        return applicationContext.getBean(Endpoint.class);
    }

    @Override
    public void stop() {
        log.info("Disconnecting... ");
        stopped.set(true);
        listening.set(false);
        super.stop();
    }

    public void triggerServerSwitch() {
        log.info("enter switchServer. Listening is {}, stopped is {}.", listening.get(), stopped.get());
        if (listening.getAndSet(false) && !stopped.get()) {
            log.info("switchServer");
            currentEndpoint().manageSessionActivityListener(false, null);
            try {
                retryDelegate.triggerServerSwitchRetry(this);
                listening.set(true);
            } catch (OPCUAException e) {
                //should not happen - only after Integer.MAX_VALUE failures
                triggerServerSwitch();
            }
        }
    }

    protected void monitoringCallback(UaMonitoredItem item, Integer integer) {
        final var nodeId = item.getReadValueId().getNodeId();
        if (nodeId.equals(Identifiers.Server_ServiceLevel)) {
            item.setValueConsumer(this::serviceLevelConsumer);
        } else if (nodeId.equals(Identifiers.ServerState)) {
            item.setValueConsumer(this::serverStateConsumer);
        }
    }

    protected void serviceLevelConsumer(DataValue value) {
        final var serviceLevel = (UByte) value.getValue().getValue();
        if (serviceLevel.compareTo(SERVICE_LEVEL_HEALTH_LIMIT) < 0) {
            log.info("Service level is outside of the healthy range at {}. Switching server...", serviceLevel.intValue());
            triggerServerSwitch();
        }
    }

    protected void serverStateConsumer(DataValue value) {
        final var state = (ServerState) value.getValue().getValue();
        if (!state.equals(ServerState.Running) && !state.equals(ServerState.Unknown)) {
            log.info("Server entered state {}. Switching server...", state);
            triggerServerSwitch();
        }
    }

    /**
     * See UA Part 4, 6.6.2.4.2, Table 10.
     * @param endpoint The endpoint whose serviceLevel to read
     * @return the endpoint's service level, or a service level of 0 if unavailable.
     */
    protected UByte readServiceLevel(Endpoint endpoint) {
        try {
            return (UByte) endpoint.read(Identifiers.Server_ServiceLevel).getKey().getValue();
        } catch (OPCUAException e) {
            return UByte.valueOf(0);
        }
    }
}
