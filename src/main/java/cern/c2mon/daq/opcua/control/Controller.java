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
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Classes implementing this interface present a specific kind handler for redundancy modes with the responsibility of
 * monitoring the connection state to the active server and of handling failover when needed. Currently, only {@link
 * ColdFailover} is supported of the OPC UA redundancy model. To add support for custom or vendor-specific redundancy
 * models, a class implementing FailoverMode should be created and references in {@link AppConfigProperties}.
 */
public interface Controller {

    /**
     * Connect to the server at one of the given addresses, and setup redundancy and failover monitoring according to
     * configuration and the information on the server
     * @param serverAddresses the addresses of all servers in a redundant server set. Contains a single address of the
     *                        server is not part of a redundant setup.
     * @throws OPCUAException if connecting to the server(s) failed.
     */
    void connect (Collection<String> serverAddresses) throws OPCUAException;

    /**
     * Disconnect from the OPC UA server and reset the controller to a neutral state.
     */
    void stop ();

    /**
     * Subscribes the {@link NodeId}s in the {@link ItemDefinition}s to the corresponding {@link SubscriptionGroup} on
     * the OPC UA server or on all servers in a redundant server set.
     * @param groupsWithDefinitions a Map of {@link SubscriptionGroup}s and the {@link ItemDefinition}s to subscribe to
     *                              the groups.
     * @return the client handles of the subscribed {@link ItemDefinition}s and the associated quality of the service
     * call.
     */
    Map<Integer, SourceDataTagQuality> subscribe (Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions);

    /**
     * Unsubscribe from the {@link NodeId} in the {@link ItemDefinition} on the OPC UA server or on all servers in a
     * redundant server set.
     * @param definition the {@link ItemDefinition} to unsubscribe from.
     * @return whether the removal from subscription was completed successfully at the currently active server. False if
     * the {@link ItemDefinition} was not subscribed in the first place.
     */
    boolean unsubscribe (ItemDefinition definition);

    /**
     * Read the current value from a node on the currently connected OPC UA server
     * @param nodeId the nodeId of the node whose value to read.
     * @return the {@link ValueUpdate} and associated {@link cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode} of the reading
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    Map.Entry<ValueUpdate, SourceDataTagQuality> read (NodeId nodeId) throws OPCUAException;

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
    Map.Entry<Boolean, Object[]> callMethod (ItemDefinition definition, Object arg) throws OPCUAException;

    /**
     * Write a value to a node on the currently connected OPC UA server.
     * @param nodeId the nodeId of the node to write a value to.
     * @param value  the value to write to the node.
     * @return whether the write command finished successfully
     * @throws OPCUAException of type {@link CommunicationException} or {@link LongLostConnectionException}.
     */
    boolean write (NodeId nodeId, Object value) throws OPCUAException;
}
