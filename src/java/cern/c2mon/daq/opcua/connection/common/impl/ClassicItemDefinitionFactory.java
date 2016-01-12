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
package cern.c2mon.daq.opcua.connection.common.impl;

import cern.c2mon.daq.opcua.connection.common.IItemDefinitionFactory;
import cern.c2mon.shared.common.datatag.address.HardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;

/**
 * Base class for the classic item definitions. This means all those which need
 * no additional information beside the address String.
 * 
 * @author Andreas Lang
 *
 * @param <ID> The item definition.
 */
public abstract class ClassicItemDefinitionFactory<ID extends ItemDefinition< ? > >
        implements IItemDefinitionFactory<ID> {

    /**
     * Creates a new ItemDefintion.
     * 
     * @param id The id of the new item definition.
     * @param hardwareAddress The hardware address with the information for the
     * new item definition.
     * @return The new item definition.
     */
    @Override
    public ID createItemDefinition(
            final long id, final HardwareAddress hardwareAddress) {
        ID definition;
        if (hardwareAddress instanceof OPCHardwareAddress) {
            OPCHardwareAddress opcHardwareAddress =
                (OPCHardwareAddress) hardwareAddress;
            String opcItemName = opcHardwareAddress.getOPCItemName();
            String redundantOpcItemName =
                opcHardwareAddress.getOpcRedundantItemName();
            if (redundantOpcItemName != null 
                    && !redundantOpcItemName.equals("")) {
                definition = createItemDefinition(
                        id, opcItemName, redundantOpcItemName);
            }
            else {
                definition = createItemDefinition(id, opcItemName);
            }
        }
        else {
            definition = null;
        }
        return definition;
    }

    /**
     * Creates a new item definition from the provided address string.
     * 
     * @param id The id of the item definition.
     * @param address The address of the item definition.
     * @return The new item definition.
     */
    public abstract ID createItemDefinition(
            final long id, final String address);

    /**
     * Creates a new item definition from the provided address string.
     * 
     * @param id The id of the item definition.
     * @param address The address of the item definition.
     * @param redundantAddress The redundant address of the item definiton.
     * @return The new item definition.
     */
    public abstract ID createItemDefinition(
            final long id, final String address,
            final String redundantAddress);
}
