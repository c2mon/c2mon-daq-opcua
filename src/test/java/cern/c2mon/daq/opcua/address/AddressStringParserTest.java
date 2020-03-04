package cern.c2mon.daq.opcua.address;

import cern.c2mon.daq.opcua.address.EquipmentAddress.ServerAddress;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AddressStringParserTest {

    @Test
    public void redundantAddress() throws URISyntaxException, ConfigurationException {
        String addressString = "URI=opc.tcp://test,opc.tcp://test2;user=user@domain,user2@domain2;password=password,password2;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";

        ServerAddress firstExpected = new ServerAddress(new URI("opc.tcp://test"),
                "user", "domain", "password");
        ServerAddress secondExpected = new ServerAddress(new URI("opc.tcp://test2"),
                "user2", "domain2", "password2");

        EquipmentAddress address = AddressStringParser.parse(addressString);

        assertEquals(firstExpected, address.getAddresses().get(0));
        assertEquals(secondExpected, address.getAddresses().get(1));
    }
    @Test
    public void createFromAllFields() throws Exception {
        String addressString = "URI=opc.tcp://test;user=user@domain;password=password;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";
        ServerAddress serverAddress = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password");
        EquipmentAddress expected = new EquipmentAddress(Collections.singletonList(serverAddress), 12, 123, true);

        assertEquals(expected, AddressStringParser.parse(addressString));
    }

    @Test
    public void createRequiredFieldsOnly() throws Exception {
        String addressString = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";
        ServerAddress serverAddress = new ServerAddress(new URI("opc.tcp://test"));

        EquipmentAddress expected = new EquipmentAddress(Collections.singletonList(serverAddress), 12, 123, true);

        assertEquals(expected, AddressStringParser.parse(addressString));
    }

    @Test
    public void createInvalidUriShouldThrowException() {
        String addressString = "URI=1 2;serverRetryTimeout=123;serverTimeout=12";
        assertThrows(ConfigurationException.class, () -> AddressStringParser.parse(addressString));
    }

    @Test
    public void createWithMissingPropertiesShouldThrowException() {
        String addressString = "URI=opc.tcp://test;aliveWriter=true";
        assertThrows(ConfigurationException.class, () -> AddressStringParser.parse(addressString));
    }

    @Test
    public void createWithInvalidPropertiesShouldThrowException() {
        String addressString = "URI=opc.tcp://test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true;a=1";
        assertThrows(ConfigurationException.class, () -> AddressStringParser.parse(addressString));
    }

    @Test
    public void createMoreUrisThanUsers() throws URISyntaxException, ConfigurationException {
        String addressString = "URI=opc.tcp://test,opc.tcp://test2;user=user@domain;password=password;serverRetryTimeout=123;serverTimeout=12;aliveWriter=true";

        ServerAddress sa1 = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password");
        ServerAddress sa2 = new ServerAddress(new URI("opc.tcp://test2"));
        EquipmentAddress expected = new EquipmentAddress(Arrays.asList(sa1, sa2), 12, 123, true);

        assertEquals(expected, AddressStringParser.parse(addressString));
    }
}