package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.address.impl.DBHardwareAddressImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemDefinitionTest extends MappingBase {

    @Test
    public void hardwareAddressOfTypeOPCDoesNotThrowError() {
        assertDoesNotThrow(() -> ItemDefinition.of(tag));
    }

    @Test
    public void hardwareAddressOfDifferentTypeShouldThrowError() {
        dataTagAddress.setHardwareAddress(new DBHardwareAddressImpl("Primary"));
        tag = makeSourceDataTag(1L, dataTagAddress);

        assertThrows(ConfigurationException.class, () -> ItemDefinition.of(tag));
    }

    @Test
    public void hardwareAddressCreatesSameItemDefinitionSpecs() throws ConfigurationException {
        ItemDefinition itemDefinition = ItemDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOPCItemName(), itemDefinition.getAddress().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), itemDefinition.getAddress().getNamespaceIndex().intValue());
        assertEquals(tag, itemDefinition.getTag());
    }

    @Test
    public void redundantHardwareAddressCreatesRedundantItemDefinition() throws ConfigurationException {
        opcHardwareAddress.setOpcRedundantItemName("Redundant");
        dataTagAddress.setHardwareAddress(opcHardwareAddress);
        tag = makeSourceDataTag(1L, dataTagAddress);

        ItemDefinition itemDefinition = ItemDefinition.of(tag);

        assertEquals(opcHardwareAddress.getOpcRedundantItemName(), itemDefinition.getRedundantAddress().getIdentifier());
        assertEquals(opcHardwareAddress.getNamespaceId(), itemDefinition.getRedundantAddress().getNamespaceIndex().intValue());
    }

    @Test
    public void sameTagShouldResultInSameDefinition() throws ConfigurationException {
        assertEquals(ItemDefinition.of(tag), ItemDefinition.of(tag));
    }

    @Test
    public void differentTagIdShouldResultInDifferentDefinitions() throws ConfigurationException {
        assertNotEquals(ItemDefinition.of(tag), ItemDefinition.of(tagWithSameDeadband));
    }

}
