package it;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

public class ConnectivityIT extends OpcUaInfrastructureBase {

    @Test
    public void connectToRunningServer() {
        endpoint.initialize(address);
        Assertions.assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectToBadServer() throws URISyntaxException {
        EquipmentAddress badAddress = new EquipmentAddress("opc.tcp://somehost/somepath", 500, 500);
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(badAddress));
    }

}
