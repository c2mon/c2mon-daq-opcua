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
package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.exceptions.*;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class presents an interface in between an C2MON OPC UA DAQ and the OPC UA client stack. Handles all interaction
 * with a single server including the consolidation of security settings, and maintains a mapping for server-specific
 * identifiers.
 */
public interface Endpoint {

    /**
     * Connects to a server through OPC UA.
     * @param uri the server address to connect to.
     * @throws OPCUAException if the server is unreachable or the configuration is erroneous.
     */
    void initialize (String uri) throws OPCUAException;

    /**
     * Adds or removes {@link SessionActivityListener}s from the client.
     * @param add      whether to add or remove a listener.
     * @param listener the listener to add or to remove.
     */
    void manageSessionActivityListener (boolean add, SessionActivityListener listener);

    /**
     * If active, the {@link MessageSender} will be informed of changes to a Session's activation status.
     * @param active whether or not to inform the {@link MessageSender} of changes to a Session's activation status.
     */
    void setUpdateEquipmentStateOnSessionChanges (boolean active);

    /**
     * Get the address of the connected server.
     * @return the address of the connected server.
     */
    String getUri ();

    /**
     * Disconnect from the server.
     */
    void disconnect ();

    /**
     * Add a list of item definitions as monitored items to a subscription and apply the default callback.
     * @param group       The {@link SubscriptionGroup} to add the {@link ItemDefinition}s to
     * @param definitions the {@link ItemDefinition}s for which to create monitored items
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException if the server indicates an error in the configuration of the ItemDefinitions, is unreachable, or has been disconnected.
     */
    Map<Integer, SourceDataTagQuality> subscribe (SubscriptionGroup group, Collection<ItemDefinition> definitions) throws OPCUAException;


    /**
     * Recreate all subscriptions configured in {@link TagSubscriptionReader}.
     * @throws CommunicationException if not a single subscription could be recreated.
     */
    void recreateAllSubscriptions () throws CommunicationException;

    /**
     * Subscribes to a {@link NodeId} with the itemCreationCallback.
     * @param publishingInterval   the publishing interval of the subscription the definitions are added to.
     * @param definitions          the {@link ItemDefinition}s containing the {@link NodeId}s to subscribe to
     * @param itemCreationCallback the callback to apply upon creation of the items in the subscription
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     * @throws OPCUAException if the server is unreachable or has been disconnected.
     */
    Map<Integer, SourceDataTagQuality> subscribeWithCallback (int publishingInterval, Collection<ItemDefinition> definitions, Consumer<UaMonitoredItem> itemCreationCallback) throws OPCUAException;

    /**
     * Delete a monitored item from an OPC UA subscription.
     * @param clientHandle    the identifier of the monitored item to remove.
     * @param publishInterval the publishInterval of the subscription to remove the monitored item from.
     * @return whether the monitored item could be removed successfully
     */
    boolean deleteItemFromSubscription (int clientHandle, int publishInterval);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode} of the reading
     * @throws OPCUAException if the server is unreachable or has been disconnected.
     */
    Map.Entry<ValueUpdate, SourceDataTagQuality> read (NodeId nodeId) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException if the server is unreachable or has been disconnected.
     */
    boolean write (NodeId nodeId, Object value) throws OPCUAException;

    /**
     * Call the method node associated with the definition. It no method node is specified within the definition, it is
     * assumed that the primary node is the method node, and the first object node ID encountered during browse is used
     * as object node.
     * @param definition the definition containing the nodeId of Method which shall be called
     * @param arg        the input argument to pass to the methodId call.
     * @return whether the methodId was successful, and the output arguments of the called method (if applicable, else
     * null) in a Map Entry.
     * @throws OPCUAException if the server is unreachable or has been disconnected.
     */
    Map.Entry<Boolean, Object[]> callMethod (ItemDefinition definition, Object arg) throws OPCUAException;

    /**
     * Return the node containing the server's redundancy information. See OPC UA Part 5, 6.3.7
     * @return the server's {@link ServerRedundancyTypeNode}
     * @throws OPCUAException if the server is unreachable or has been disconnected.
     */
    ServerRedundancyTypeNode getServerRedundancyNode () throws OPCUAException;

}
