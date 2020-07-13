package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class FailoverDecorator implements Controller {

    protected static UByte SERVICE_LEVEL_HEALTH_LIMIT = UByte.valueOf(200);
    protected static List<ItemDefinition> connectionMonitoringNodes = List.of(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));
    protected static final AtomicBoolean listening = new AtomicBoolean(true);

    private final ApplicationContext applicationContext;
    protected final RetryDelegate retryDelegate;
    protected final ControllerImpl singleServerController;

    public FailoverDecorator(ApplicationContext applicationContext, RetryDelegate retryDelegate, ControllerImpl singleServerController) {
        this.singleServerController = singleServerController;
        this.applicationContext = applicationContext;
        this.retryDelegate = retryDelegate;
    }

    @Override
    public void connect(String uri) throws OPCUAException {
        singleServerController.connect(uri);
    }

    @Override
    public Endpoint currentEndpoint() {
        return singleServerController.currentEndpoint();
    }

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    protected abstract List<Endpoint> passiveEndpoints();

    protected Endpoint nextEndpoint() {
        return applicationContext.getBean(Endpoint.class);
    }

    @Override
    public void stop() {
        singleServerController.stop();
        passiveEndpoints().forEach(e -> {
            try {
                e.disconnect();
            } catch (OPCUAException ex) {
                log.error("Error disconnecting from passive endpoint with uri {}: ", e.getUri(), ex);
            }
        });
    }

    @Override
    public boolean unsubscribe(ItemDefinition definition) {
        passiveEndpoints().forEach(e -> e.deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband()));
        return singleServerController.unsubscribe(definition);
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        passiveEndpoints().forEach(endpoint -> groupsWithDefinitions.entrySet()
                        .forEach(e -> ControllerImpl.subscribeOnEndpoint(endpoint, e)));
        return singleServerController.subscribe(groupsWithDefinitions);
    }

    public void triggerServerSwitch() {
        log.info("switchServer");
        if (listening.getAndSet(false)) {
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

    protected void serviceLevelConsumer (DataValue value) {
        final var serviceLevel = (UByte) value.getValue().getValue();
        if (serviceLevel.compareTo(SERVICE_LEVEL_HEALTH_LIMIT) < 0) {
            log.info("Service level is outside of the healthy range at {}. Switching server...", serviceLevel.intValue());
            triggerServerSwitch();
        }
    }

    protected void serverStateConsumer (DataValue value) {
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

    /**
     * Called periodically  when the client loses connectivity with the active Server until connection can be
     * reestablished.
     * @throws OPCUAException if the no server could be connected to
     */
    public abstract void switchServers() throws OPCUAException;
}
