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
    public void hardwareAddressOfDifferentTypeShouldThrowError() {
        dataTagAddress.setHardwareAddress(new DBHardwareAddressImpl("Primary"));
        tag = makeSourceDataTag(1L, dataTagAddress);

        assertThrows(ConfigurationException.class, () -> DataTagDefinition.of(tag));
    }

    @Test
    public void hardwareAddressCreatesSameItemDefinitionSpecs() throws ConfigurationException {
        DataTagDefinition dataTagDefinition = DataTagDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOPCItemName(), dataTagDefinition.getAddress().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getAddress().getNamespaceIndex().intValue());
        assertEquals(tag, dataTagDefinition.getTag());
    }

    @Test
    public void redundantHardwareAddressCreatesRedundantItemDefinition() {
        opcHardwareAddress.setOpcRedundantItemName("Redundant");
        dataTagAddress.setHardwareAddress(opcHardwareAddress);
        tag = makeSourceDataTag(1L, dataTagAddress);

        DataTagDefinition dataTagDefinition = DataTagDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOpcRedundantItemName(), dataTagDefinition.getRedundantAddress().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), dataTagDefinition.getRedundantAddress().getNamespaceIndex().intValue());
    }

    @Test
    public void sameTagShouldResultInSameDefinition() throws ConfigurationException {
        assertEquals(DataTagDefinition.of(tag), DataTagDefinition.of(tag));
    }

    @Test
    public void differentTagIdShouldResultInDifferentDefinitions() throws ConfigurationException {
        assertNotEquals(DataTagDefinition.of(tag), DataTagDefinition.of(tagWithSameDeadband));
    }

}
