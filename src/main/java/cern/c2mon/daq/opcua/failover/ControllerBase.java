package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ControllerBase implements Controller {

    protected abstract Endpoint currentEndpoint();

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    protected abstract List<Endpoint> passiveEndpoints();

    @Override
    public void stop() {
        currentEndpoint().disconnect();
        passiveEndpoints().forEach(Endpoint::disconnect);
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        passiveEndpoints().forEach(endpoint -> groupsWithDefinitions.entrySet()
                .forEach(e -> subscribeAndCatch(endpoint, e)));
        return groupsWithDefinitions.entrySet().stream()
                .flatMap(e -> subscribeAndCatch(currentEndpoint(), e))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public boolean unsubscribe(ItemDefinition definition) {
        passiveEndpoints().forEach(e -> e.deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband()));
        return currentEndpoint().deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        return currentEndpoint().read(nodeId);
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return currentEndpoint().callMethod(definition, arg);
    }

    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        return currentEndpoint().write(nodeId, value);
    }

    public static Stream<Map.Entry<UInteger, SourceDataTagQuality>> subscribeAndCatch(Endpoint e, Map.Entry<SubscriptionGroup, List<ItemDefinition>> groupWithDefinitions) {
        try {
            return e.subscribe(groupWithDefinitions.getKey(), groupWithDefinitions.getValue()).entrySet().stream();
        } catch (ConfigurationException configurationException) {
            return groupWithDefinitions.getValue().stream()
                    .map(d -> Map.entry(d.getClientHandle(), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)));
        }
    }
}