/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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
package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.OPCUANameSpaceIndex;
import cern.c2mon.daq.opcua.connection.MiloMapper;
import cern.c2mon.daq.opcua.control.ConcreteController;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link ItemDefinition} stores all values required to process an {@link ISourceDataTag} or an {@link
 * ISourceCommandTag} including the {@link NodeId} and Deadband values. Every {@link ItemDefinition} is identified by a
 * unique clientHandle which is used by the {@link TagSubscriptionMapper} to associate it with the tag.
 */
@Getter
@Slf4j
public class ItemDefinition {

    private static final AtomicInteger clientHandles = new AtomicInteger();

    /**
     * Extracted from the primary item name of an {@link OPCHardwareAddress}
     */
    private final NodeId nodeId;

    /**
     * This equals the redundant item name of a {@link OPCHardwareAddress}. If this value is set in an {@link
     * ISourceDataTag}, it is subscribed to equally with the primary nodeId. If it is set on an {@link
     * ISourceCommandTag} of type {@link cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress.COMMAND_TYPE}
     * METHOD, the primary {@link NodeId} is interpreted as referring to the containing object Node, and the
     * methodNodeId as referring to the method Node.
     */
    private final NodeId methodNodeId;
    private final int timeDeadband;
    private final float valueDeadband;
    private final DeadbandType valueDeadbandType;

    /**
     * The {@link ItemDefinition}'s unique identifier, equal to the client Handle of a monitoredItem returned by a Milo
     * subscription
     */
    private final int clientHandle;

    private ItemDefinition (NodeId nodeId, NodeId methodNodeId) {
        this(nodeId, methodNodeId, 0, 0, 0);
    }

    private ItemDefinition (final ISourceDataTag tag, final NodeId nodeId, final NodeId methodNodeId) {
        this(nodeId, methodNodeId, tag.getTimeDeadband(), tag.getValueDeadband(), tag.getValueDeadbandType());
    }

    private ItemDefinition (NodeId nodeId, NodeId methodNodeId, int timeDeadband, float valueDeadband, int valueDeadbandType) {
        this.nodeId = nodeId;
        this.methodNodeId = methodNodeId;
        this.clientHandle = clientHandles.getAndIncrement();
        this.timeDeadband = timeDeadband;
        this.valueDeadband = valueDeadband;
        this.valueDeadbandType = MiloMapper.toDeadbandType(valueDeadbandType);
    }

    /**
     * Returns the NodeId for an {@link ISourceCommandTag}'s primary address.
     * @param tag the {@link ISourceCommandTag} for which to return a NodeId
     * @return the {@link NodeId} for the tag's primary address
     * @throws ConfigurationException if the primary address has an incorrect hardware type.
     */
    public static NodeId getNodeIdForTag (final ISourceCommandTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new NodeId(opcAddress.getNamespaceId(), opcAddress.getOPCItemName());
    }

    /**
     * Creates a new {@link ItemDefinition} from a {@link NodeId}. Used by the {@link ConcreteController}s when
     * subscribing to default Nodes to supervise the server health
     * @param nodeId the {@link NodeId} for which to create an {@link ItemDefinition}
     * @return the newly created {@link ItemDefinition}
     */
    public static ItemDefinition of (NodeId nodeId) {
        return new ItemDefinition(nodeId, null);
    }

    /**
     * Stores the relevant information contained in an {@link ISourceDataTag} into a corresponding {@link
     * ItemDefinition} including the {@link NodeId}s and Deadband values.
     * @param tag the {@link ISourceDataTag} for which an associated {@link ItemDefinition} shall be created
     * @return the newly created {@link ItemDefinition}
     * @throws ConfigurationException if the tag has an address of incorrect type
     */
    public static ItemDefinition of (final ISourceDataTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new ItemDefinition(tag, fromAddress(opcAddress, false), fromAddress(opcAddress, true));
    }

    /**
     * Stores the relevant information contained in an {@link ISourceCommandTag} into a corresponding {@link
     * ItemDefinition} including primary and method {@link NodeId}s.
     * @param tag the {@link ISourceCommandTag} for which an associated {@link ItemDefinition} shall be created
     * @return the newly created {@link ItemDefinition}
     * @throws ConfigurationException if the tag has an address of incorrect type
     */
    public static ItemDefinition of (final ISourceCommandTag tag) throws ConfigurationException {
        OPCHardwareAddress opcAddress = extractOpcAddress(tag.getHardwareAddress());
        return new ItemDefinition(fromAddress(opcAddress, false), fromAddress(opcAddress, true));
    }

    private static OPCHardwareAddress extractOpcAddress (HardwareAddress address) throws ConfigurationException {
        if (!(address instanceof OPCHardwareAddress)) {
            throw new ConfigurationException(ExceptionContext.HARDWARE_ADDRESS_TYPE);
        }
        return (OPCHardwareAddress) address;
    }

    private static NodeId fromAddress(OPCHardwareAddress opcAddress, boolean redundant) {
        String itemName = opcAddress.getOpcRedundantItemName();
        if (redundant && (itemName == null || itemName.trim().isEmpty())) {
            return null;
        } else if (!redundant) {
            itemName = opcAddress.getOPCItemName();
        }
        
        int namespaceId = OPCUANameSpaceIndex.get().getIdByItemName(itemName);
        
        switch (opcAddress.getAddressType()) {
            case GUID:
                return new NodeId(namespaceId, UUID.fromString(itemName));
            case NUMERIC:
                return new NodeId(namespaceId, Integer.parseInt(itemName));
            default:
                return new NodeId(namespaceId, itemName);
        }    }
}
