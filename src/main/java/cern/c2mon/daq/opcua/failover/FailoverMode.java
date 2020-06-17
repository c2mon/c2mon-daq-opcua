package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;

import java.util.Collection;
import java.util.Map;

public interface FailoverMode {

    void initialize(Map.Entry<String, Endpoint> server, String[] redundantAddresses, Collection<SessionActivityListener> listeners) throws CommunicationException;

    void disconnect() throws OPCUAException;

    Endpoint currentEndpoint();

    void switchServers();
}
