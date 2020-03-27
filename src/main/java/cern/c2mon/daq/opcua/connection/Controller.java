package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;

public interface Controller {

    String UA_TCP_TYPE = "opc.tcp";

    void initialize() throws ConfigurationException;
    void stop();
    void checkConnection() throws ConfigurationException;
    void refreshAllDataTags();
    void refreshDataTag(ISourceDataTag sourceDataTag);
    String updateAliveWriterAndReport ();
    void subscribe (EndpointListener listener);
    void runCommand(ISourceCommandTag tag, SourceCommandTagValue value) throws ConfigurationException;

    // for dependency injection during testing
    Endpoint getEndpoint();
}
