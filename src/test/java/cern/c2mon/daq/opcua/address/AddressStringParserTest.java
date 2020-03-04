package cern.c2mon.daq.opcua.address;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AddressStringParserTest {

    @Test
    public void redundantAddress() throws URISyntaxException, ConfigurationException {
        String addressString = "URI=opc.tcp://test,opc.tcp://test2;user=user@domain,user2@domain2;password=password,password2;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";
        EquipmentAddress firstExpected = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, true);
        EquipmentAddress secondExpected = new EquipmentAddress(
                new URI("opc.tcp://test2"), "user2", "domain2", "password2", 12, 123, true);

        List<EquipmentAddress> parsedAddresses = AddressStringParser.parse(addressString);

        assertEquals(firstExpected, parsedAddresses.get(0));
        assertEquals(secondExpected, parsedAddresses.get(1));
    }
    @Test
    public void createFromAllFields() throws Exception {
        String addressString = "URI=opc.tcp://test;user=user@domain;password=password;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";
        EquipmentAddress expected = new EquipmentAddress(new URI("opc.tcp://test"),
                "user", "domain", "password", 12, 123, true);

        assertEquals(expected, addressFromNewParser(addressString));
    }

    @Test
    public void createRequiredFieldsOnly() throws Exception {
        String addressString = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";
        EquipmentAddress expected = new EquipmentAddress(new URI("opc.tcp://test"),
                null, null, null, 12, 123, true);

        assertEquals(expected, addressFromNewParser(addressString));
    }

    @Test
    public void createInvalidUriShouldThrowException() {
        String addressString = "URI=1 2;serverRetryTimeout=123;serverTimeout=12";
        assertThrows(ConfigurationException.class, () -> addressFromNewParser(addressString));
    }

    @Test
    public void createWithMissingPropertiesShouldThrowException() {
        String addressString = "URI=opc.tcp://test;aliveWriter=true";
        assertThrows(ConfigurationException.class, () -> addressFromNewParser(addressString));
    }

    @Test
    public void createWithInvalidPropertiesShouldThrowException() {
        String addressString = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true;a=1";
        assertThrows(ConfigurationException.class, () -> addressFromNewParser(addressString));
    }

    private EquipmentAddress addressFromNewParser(String addressString) throws ConfigurationException {
        List<EquipmentAddress> parsedAddressed = AddressStringParser.parse(addressString);
        return parsedAddressed.get(0);

    }

}