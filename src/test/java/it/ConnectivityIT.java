package it;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class ConnectivityIT extends OpcUaInfrastructureBase {

    @Test
    public void connectToRunningServer() {
        endpoint.initialize(false);
        Assertions.assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectToBadServer() throws URISyntaxException {
        wrapper = new MiloClientWrapperImpl("opc.tcp://somehost/somepath", SecurityPolicy.None);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        Assertions.assertThrows(OPCCommunicationException.class, () -> endpoint.initialize(false));
    }
}