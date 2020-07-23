package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class presents an interface in between an C2MON OPC UA DAQ and the OPC UA client stack. Handles all interaction
 * with a single server including * the the consolidation of security settings, and maintains a mapping for
 * server-specific identifiers. {@link OPCUAException}s are given in the JavaDoc of the concrete implementation
 * classes.
 */
public interface Endpoint {

    /**
     * Connects to a server through OPC UA.
     * @param uri the server address to connect to.
     * @throws OPCUAException of type {@link CommunicationException} or {@link ConfigurationException}.
     */
    void initialize(String uri) throws OPCUAException;

    /**
     * Adds or removes {@link SessionActivityListener}s from the client.
     * @param add      whether to add or remove a listener.
     * @param listener the listener to add or to remove.
     */
    void manageSessionActivityListener(boolean add, SessionActivityListener listener);

    /**
     * Returns the address of the connected server.
     * @return the address of the connected server.
     */
    String getUri();

    /**
     * Disconnect from the server.
     */
    void disconnect();

    /**
     * Add a list of item definitions as monitored items to a subscription and apply the default callback.
     * @param group       The {@link SubscriptionGroup} to add the {@link ItemDefinition}s to
     * @param definitions the {@link ItemDefinition}s for which to create monitored items
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws ConfigurationException if the server returned an error code indicating a misconfiguration.
     */
    Map<UInteger, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) throws ConfigurationException;

    /**
     * Called when a subscription could not be automatically transferred in between sessions. In this case, the
     * subscription is recreated from scratch. If the controller received the command to stop, the subscription is not
     * recreated and the method exists silently.
     * @param subscription the session which must be recreated.
     * @throws OPCUAException of type {@link CommunicationException} if the subscription could not be recreated due to
     *                        communication difficulty, and of type {@link ConfigurationException} if the subscription
     *                        can not be mapped to current DataTags, and therefore cannot be recreated.
     */
    void recreateSubscription(UaSubscription subscription) throws OPCUAException;

    /**
     * Recreate all subscriptions configured in {@link TagSubscriptionReader}. This
     * method is usually called by the {@link cern.c2mon.daq.opcua.control.Controller} after a failover.
     * @throws CommunicationException if no subscription could be recreated.
     */
    void recreateAllSubscriptions() throws CommunicationException;

    /**
     * Subscribes to a {@link NodeId} with the passed callback. Usually called by the {@link
     * cern.c2mon.daq.opcua.control.Controller} to monitor the server health.
     * @param publishingInterval   the
     * @param definitions          the {@link ItemDefinition}s containing the {@link NodeId}s to subscribe to
     * @param itemCreationCallback the callback to apply upon creation of the items in the subscription
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException if the definitions could not be subscribed.
     */
    Map<UInteger, SourceDataTagQuality> subscribeWithCallback(int publishingInterval, Collection<ItemDefinition> definitions, Consumer<UaMonitoredItem> itemCreationCallback) throws OPCUAException;

    /**
     * Delete a monitored item from an OPC UA subscription.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishInterval of the subscription to remove the monitored item from.
     * @return whether the monitored item could be removed successfully
     */
    boolean deleteItemFromSubscription(UInteger clientHandle, int publishInterval);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    boolean write(NodeId nodeId, Object value) throws OPCUAException;

    /**
     * Call the method node associated with the definition. It no method node is specified within the definition, it is
     * assumed that the primary node is the method node, and the first object node ID encountered during browse is used
     * as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException;

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    ServerRedundancyTypeNode getServerRedundancyNode() throws OPCUAException;

}
