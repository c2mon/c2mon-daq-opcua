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

import cern.c2mon.daq.opcua.connection.MiloMapper;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.shared.common.datatag.address.impl.DBHardwareAddressImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemDefinitionTest extends MappingBase {

    @Test
    public void hardwareAddressOfTypeOPCDoesNotThrowError() {
        assertDoesNotThrow(() -> ItemDefinition.of(tag));
    }

    @Test
    public void hardwareAddressOfDifferentTypeShouldThrowConfigurationException() {
        dataTagAddress.setHardwareAddress(new DBHardwareAddressImpl("Primary"));
        tag = makeSourceDataTag(1L, dataTagAddress);
        Assertions.assertThrows(ConfigurationException.class,
                () -> ItemDefinition.of(tag),
                ExceptionContext.HARDWARE_ADDRESS_TYPE.getMessage());
    }

    @Test
    public void hardwareAddressCreatesSameItemDefinitionSpecs() throws ConfigurationException {
        ItemDefinition dataTagDefinition = ItemDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOPCItemName(), dataTagDefinition.getNodeId().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getNodeId().getNamespaceIndex().intValue());
        assertEquals(tag.getTimeDeadband(), dataTagDefinition.getTimeDeadband());
        assertEquals(tag.getValueDeadband(), dataTagDefinition.getValueDeadband());
        assertEquals(MiloMapper.toDeadbandType(tag.getValueDeadbandType()), dataTagDefinition.getValueDeadbandType());
    }

    @Test
    public void redundantHardwareAddressCreatesRedundantItemDefinition() throws ConfigurationException {
        opcHardwareAddress.setOpcRedundantItemName("Redundant");
        dataTagAddress.setHardwareAddress(opcHardwareAddress);
        tag = makeSourceDataTag(1L, dataTagAddress);

        ItemDefinition dataTagDefinition = ItemDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOpcRedundantItemName(), dataTagDefinition.getMethodNodeId().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getMethodNodeId().getNamespaceIndex().intValue());
    }
}
