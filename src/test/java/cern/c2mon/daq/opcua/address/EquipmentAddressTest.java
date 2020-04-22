package cern.c2mon.daq.opcua.address;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import static cern.c2mon.daq.opcua.address.EquipmentAddress.ServerAddress;
import static org.junit.jupiter.api.Assertions.*;

public class EquipmentAddressTest {
    ServerAddress serverAddress;
    EquipmentAddress equipmentAddress;

    @BeforeEach
    void setUp () throws URISyntaxException, ConfigurationException {
        serverAddress = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password");
        equipmentAddress = new EquipmentAddress(Collections.singletonList(serverAddress), 12, 123, true);
    }

    @Test
    public void equipmentAddressesWithSameValuesShouldBeEqual() throws URISyntaxException, ConfigurationException {
        ServerAddress secondSA = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password");

        EquipmentAddress second = new EquipmentAddress(Collections.singletonList(secondSA), 12, 123, true);

        assertEquals(equipmentAddress, second);
    }

    @Test
    public void equipmentAddressesWithSameValuesFromSameServerAddressShouldBeEqual() throws URISyntaxException, ConfigurationException {
        EquipmentAddress second = new EquipmentAddress(Collections.singletonList(serverAddress), 12, 123, true);
        assertEquals(equipmentAddress, second);
    }

    @Test
    public void equipmentAddressesWithDifferentValuesShouldNotBeEqual() throws ConfigurationException {
        EquipmentAddress second = new EquipmentAddress(Collections.singletonList(serverAddress), 12, 123, false);

        assertNotEquals(equipmentAddress, second);
    }

    @Test
    public void emptyServerAddressListShouldThrowException() {
        assertThrows(ConfigurationException.class,
                () -> new EquipmentAddress(Collections.emptyList(), 1, 2, true),
                ConfigurationException.Cause.MISSING_URI.message);
    }

    @Test
    public void getServerAddressWithUnknownProtocolShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> equipmentAddress.getServerAddressOfType("http"));
    }

    @Test
    public void getServerAddressWithMatchingProtocolShouldReturnServerAddress() throws URISyntaxException, ConfigurationException {
        ServerAddress secondSA = new ServerAddress(new URI("http://test"),"user", "domain", "password");
        EquipmentAddress equipmentAddress = new EquipmentAddress(Arrays.asList(serverAddress, secondSA), 1, 2, true);

        ServerAddress serverAddressWithProtocol = equipmentAddress.getServerAddressOfType("opc.tcp");

        assertEquals(serverAddress, serverAddressWithProtocol);
    }


    @Test
    public void supportsProtocolShouldReturnTrueForSupportedProtocol() throws ConfigurationException, URISyntaxException {
        ServerAddress secondSA = new ServerAddress(new URI("http://test"),"user", "domain", "password");
        EquipmentAddress equipmentAddress = new EquipmentAddress(Arrays.asList(serverAddress, secondSA), 1, 2, true);

        assertTrue(equipmentAddress.supportsProtocol("opc.tcp"));
    }

    @Test
    public void supportsProtocolShouldReturnFalseForUnknownProtocol() throws ConfigurationException, URISyntaxException {
        ServerAddress secondSA = new ServerAddress(new URI("http://test"),"user", "domain", "password");
        EquipmentAddress equipmentAddress = new EquipmentAddress(Arrays.asList(serverAddress, secondSA), 1, 2, true);

        assertFalse(equipmentAddress.supportsProtocol("test"));
    }

    @Test
    public void serverAddressFromURIShouldHaveNullValues() throws URISyntaxException {
        URI uri = new URI("opc.tcp://test");
        ServerAddress sa = new ServerAddress(uri);

        assertEquals(uri, sa.getUri());
        assertNull(sa.getUser());
        assertNull(sa.getDomain());
        assertNull(sa.getPassword());
    }

    @Test
    public void serverAddressFromBadURIShouldThrowException() {
        assertThrows(URISyntaxException.class, () -> new ServerAddress(new URI("  ")));
    }

    @Test
    public void getProtocolShouldReturnUriScheme() throws URISyntaxException {
        ServerAddress sa = new ServerAddress(new URI("opc.tcp://test"));
        assertEquals("opc.tcp", sa.getProtocol());
    }


    @Test
    public void getUriStringShouldReturnUriToString() throws URISyntaxException {
        String uri = "opc.tcp://test";
        ServerAddress sa = new ServerAddress(new URI(uri));
        assertEquals(uri, sa.getUriString());
    }

    @Test
    public void serverAddressesWithSameValuesShouldBeEqual() throws URISyntaxException {
        ServerAddress second = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password");
        assertEquals(serverAddress, second);
    }

    @Test
    public void serverAddressesWithDifferentURIShouldBeDifferent() throws URISyntaxException {
        ServerAddress uri = new ServerAddress(new URI("opc.tcp://test1"),"user", "domain", "password");
        assertNotEquals(serverAddress, uri);
    }
    @Test
    public void serverAddressesWithDifferentUserShouldBeDifferent() throws URISyntaxException {
        ServerAddress user = new ServerAddress(new URI("opc.tcp://test"),"user1", "domain", "password");
        assertNotEquals(serverAddress, user);
    }

    @Test
    public void serverAddressesWithDifferentDomainShouldBeEqual() throws URISyntaxException {
        ServerAddress domain = new ServerAddress(new URI("opc.tcp://test"),"user", "domain1", "password");
        assertNotEquals(serverAddress, domain);
    }

    @Test
    public void serverAddressesWithDifferentPasswordShouldBeEqual() throws URISyntaxException {
        ServerAddress password = new ServerAddress(new URI("opc.tcp://test"),"user", "domain", "password1");
        assertNotEquals(serverAddress, password);
    }


}
