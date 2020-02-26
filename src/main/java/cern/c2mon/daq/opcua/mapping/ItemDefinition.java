/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 * 
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 * 
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.AddressException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.Getter;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class ItemDefinition {

    /**
     * the c2mon tag
     */
    private final ISourceDataTag tag;

    /**
     * equal to the client Handle of a monitoredItem returned by a Milo subscription
     */
    private final UInteger clientHandle;
    private final NodeId address;
    private final NodeId redundantAddress;

    private static final AtomicInteger clientHandles = new AtomicInteger();

    private ItemDefinition(final ISourceDataTag tag, final NodeId address) {
        this(tag, address, null);
    }

    private ItemDefinition(final ISourceDataTag tag, final NodeId address, final NodeId redundantAddress) {
        this.tag = tag;
        this.address = address;
        this.redundantAddress = redundantAddress;
        this.clientHandle = UInteger.valueOf(clientHandles.getAndIncrement());
    }

    public static ItemDefinition of(final ISourceDataTag tag) {
        HardwareAddress hardwareAddress = tag.getHardwareAddress();

        if (!(hardwareAddress instanceof OPCHardwareAddress)) {
            throw new AddressException("The hardware address is not of type OPCHardwareAddress and cannot be handled");
        }
        OPCHardwareAddress opcAddress = (OPCHardwareAddress) hardwareAddress;

        //TODO: before, if getNamespaceId = 0, namespaceIndex would be 2. Why?
        int namespaceIndex = opcAddress.getNamespaceId();
        String redundantOPCItemName = opcAddress.getOpcRedundantItemName();

        NodeId nodeId = new NodeId(namespaceIndex, opcAddress.getOPCItemName());
        if (redundantOPCItemName != null && !redundantOPCItemName.trim().equals("")) {
            NodeId redundantNodeId = new NodeId(namespaceIndex, redundantOPCItemName);
            return new ItemDefinition(tag, nodeId, redundantNodeId);
        } else {
            return new ItemDefinition(tag, nodeId);
        }
    }

    /**
     * The objects are considered equal if their tags have the same id.
     * 
     * @param o The object to compare to.
     * @return true if the objects equal else false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemDefinition that = (ItemDefinition) o;
        return tag.getId().equals(that.getTag().getId());
    }

    /**
     * Returns the hash code of this object.
     * 
     * @return The hash code of this object which equals the hash code of its
     * ID.
     */
    @Override
    public int hashCode() {
        return tag.getId().hashCode();
    }
}
