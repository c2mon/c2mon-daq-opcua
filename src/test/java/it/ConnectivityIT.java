package it;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

public class ConnectivityIT extends OpcUaInfrastructureBase {

    @Test
    public void connectToRunningServer() {
        endpoint.initialize();
        Assertions.assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectToBadServer() throws URISyntaxException {
        EquipmentAddress badAddress = new EquipmentAddress("opc.tcp://somehost/somepath", 500, 500);

        wrapper = new MiloClientWrapperImpl(badAddress.getUriString(), SecurityPolicy.None);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize());
    }
}