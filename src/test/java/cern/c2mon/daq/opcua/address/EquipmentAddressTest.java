package cern.c2mon.daq.opcua.address;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EquipmentAddressTest {

    @Test
    public void addressesWithSameValuesShouldBeEqual() throws URISyntaxException {
        EquipmentAddress first = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, true);
        EquipmentAddress second = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, true);

        assertEquals(first, second);
    }

    @Test
    public void addressesWithDifferentValuesShouldNotBeEqual() throws URISyntaxException {
        EquipmentAddress first = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, true);
        EquipmentAddress second = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, false);

        assertNotEquals(first, second);
    }
}
