package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;

import java.util.List;

@Slf4j
public abstract class FailoverBase implements FailoverMode {

    @Autowired @Lazy protected TagSubscriptionMapper mapper;
    @Autowired @Lazy protected Controller controller;
    @Autowired protected RetryDelegate retryDelegate;

    protected static List<ItemDefinition> connectionMonitoringNodes = List.of(
            ItemDefinition.of(Identifiers.Server_ServiceLevel),
            ItemDefinition.of(Identifiers.ServerState));

    enum ServiceLevels {
        Maintenance(0,0), NoData(1,1), Degraded(2,199), Healthy(200,255);
        UByte lowerLimit;
        UByte upperLimit;
        ServiceLevels(int l, int u) {
            lowerLimit = UByte.valueOf(l);
            upperLimit = UByte.valueOf(u);
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    protected Endpoint nextEndpoint() {
        return applicationContext.getBean(Endpoint.class);
    }

    @Override
    public void triggerServerSwitch() {
        try {
            retryDelegate.triggerServerSwitchRetry(this);
        } catch (OPCUAException e) {
            triggerServerSwitch();
        }
    }

    protected void monitoringCallback(UaMonitoredItem item, Integer integer) {
        final var nodeId = item.getReadValueId().getNodeId();
        if (nodeId.equals(Identifiers.Server_ServiceLevel)) {
            item.setValueConsumer(value -> {
                final var serviceLevel = (UByte) value.getValue().getValue();
                if (serviceLevel.compareTo(ServiceLevels.Healthy.lowerLimit) < 0) {
                    log.info("Service level is outside of the healthy range. Switching server...");
                    triggerServerSwitch();
                }
            });
        } else if (nodeId.equals(Identifiers.ServerState)) {
            item.setValueConsumer(value -> {
                final var state = (ServerState) value.getValue().getValue();
                if (!state.equals(ServerState.Running) && !state.equals(ServerState.Unknown)) {
                    log.info("Server entered state {}. Switching server...", state);
                    triggerServerSwitch();
                }
            });
        }
    }

    public abstract void switchServers() throws OPCUAException;

}
