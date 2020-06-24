package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class FailoverBase implements FailoverMode {

    protected static UByte SERVICE_LEVEL_HEALTH_LIMIT = UByte.valueOf(200);
    protected static List<ItemDefinition> connectionMonitoringNodes = List.of(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));
    protected static final AtomicBoolean listening = new AtomicBoolean(true);


    @Autowired @Lazy protected Controller controller;
    @Autowired protected TagSubscriptionMapper mapper;
    @Autowired protected EndpointListener endpointListener;
    @Autowired protected RetryDelegate retryDelegate;

    @Autowired
    private ApplicationContext applicationContext;

    protected Endpoint nextEndpoint() {
        return applicationContext.getBean(Endpoint.class);
    }

    public void triggerServerSwitch() {
        log.info("switchServer");
        if (listening.getAndSet(false)) {
            currentEndpoint().monitorEquipmentState(false, (SessionActivityListener) endpointListener);
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

    public abstract void switchServers() throws OPCUAException;
}
