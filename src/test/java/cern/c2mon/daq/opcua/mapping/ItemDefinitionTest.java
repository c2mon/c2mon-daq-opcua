package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.address.impl.DBHardwareAddressImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemDefinitionTest extends MappingBase {

    @Test
    public void hardwareAddressOfTypeOPCDoesNotThrowError() {
        assertDoesNotThrow(() -> DataTagDefinition.of(tag));
    }

    @Test
    public void hardwareAddressOfDifferentTypeShouldReturnNull() {
        dataTagAddress.setHardwareAddress(new DBHardwareAddressImpl("Primary"));
        tag = makeSourceDataTag(1L, dataTagAddress);

        assertNull(DataTagDefinition.of(tag));
    }

    @Test
    public void hardwareAddressCreatesSameItemDefinitionSpecs() throws ConfigurationException {
        DataTagDefinition dataTagDefinition = DataTagDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOPCItemName(), dataTagDefinition.getNodeId().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getNodeId().getNamespaceIndex().intValue());
        assertEquals(tag.getTimeDeadband(), dataTagDefinition.getTimeDeadband());
        assertEquals(tag.getValueDeadband(), dataTagDefinition.getValueDeadband());
        assertEquals(tag.getValueDeadbandType(), dataTagDefinition.getValueDeadbandType());
    }

    @Test
    public void redundantHardwareAddressCreatesRedundantItemDefinition() {
        opcHardwareAddress.setOpcRedundantItemName("Redundant");
        dataTagAddress.setHardwareAddress(opcHardwareAddress);
        tag = makeSourceDataTag(1L, dataTagAddress);

        DataTagDefinition dataTagDefinition = DataTagDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOpcRedundantItemName(), dataTagDefinition.getMethodNodeId().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getMethodNodeId().getNamespaceIndex().intValue());
    }
}
