package cern.c2mon.daq.opcua.mapping;

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
        assertEquals(ValueDeadbandType.of(tag.getValueDeadbandType()), dataTagDefinition.getValueDeadbandType());
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
