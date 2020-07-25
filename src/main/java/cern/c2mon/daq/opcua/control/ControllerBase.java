package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract base  which offers functionality that is shared across all failover modes and the {@link NoFailover}
 * {@link Controller}.
 */
@Slf4j
public abstract class ControllerBase implements Controller {

    private static Stream<Map.Entry<UInteger, SourceDataTagQuality>> subscribeAndCatch(Endpoint e, Map.Entry<SubscriptionGroup, List<ItemDefinition>> groupWithDefinitions) {
        try {
            return e.subscribe(groupWithDefinitions.getKey(), groupWithDefinitions.getValue()).entrySet().stream();
        } catch (ConfigurationException ex) {
            log.info("Could not subscribe the ItemDefinitions with time deadband {} to the endpoint at URI {}.", groupWithDefinitions.getKey().getPublishInterval(), e.getUri(), ex);
            return groupWithDefinitions.getValue().stream()
                    .map(d -> new AbstractMap.SimpleEntry<>(d.getClientHandle(), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)));
        }
    }

    /**
     * Fetch the endpoint to use for active operations such as writing to or reading from a {@link NodeId} or calling a
     * method on the server.
     * @return the active Endpoint
     */
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
        passiveEndpoints().forEach(Endpoint::disconnect);
        currentEndpoint().disconnect();
    }

    /**
     * Subscribes the {@link NodeId}s in the {@link ItemDefinition}s to the corresponding {@link SubscriptionGroup} on
     * all servers in a redundant server set.
     * @param groupsWithDefinitions a Map of {@link SubscriptionGroup}s and the {@link ItemDefinition}s to subscribe to
     *                              the groups.
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call on the active server.
     */
    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        passiveEndpoints().forEach(endpoint -> groupsWithDefinitions.entrySet()
                .forEach(e -> subscribeAndCatch(endpoint, e)));
        return groupsWithDefinitions.entrySet().stream()
                .flatMap(e -> subscribeAndCatch(currentEndpoint(), e))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Unsubscribe from the {@link NodeId} in the {@link ItemDefinition} on the OPC UA server or on all servers in a
     * redundant server set.
     * @param definition the {@link ItemDefinition} to unsubscribe from.
     * @return whether the removal from subscription was completed successfully at the currently active server. False if
     * the {@link ItemDefinition} was not subscribed in the first place.
     */
    @Override
    public boolean unsubscribe(ItemDefinition definition) {
        passiveEndpoints().forEach(e -> e.deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband()));
        return currentEndpoint().deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
    }

    /**
     * Read the current value from a node on the currently active OPC UA server.
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        return currentEndpoint().read(nodeId);
    }

    /**
     * Call the method node associated with the definition within the address space of the currently active server. It
     * no method node is specified within the definition, it is assumed that the primary node is the method node, and
     * the first object node ID encountered during browse is used as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return currentEndpoint().callMethod(definition, arg);
    }

    /**
     * Write a value to a node on the currently active OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        return currentEndpoint().write(nodeId, value);
    }
}