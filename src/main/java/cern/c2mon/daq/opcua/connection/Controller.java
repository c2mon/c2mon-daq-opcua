package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

public interface Controller {

    String UA_TCP_TYPE = "opc.tcp";

    void initialize() throws ConfigurationException;
    void stop();
    void checkConnection() throws ConfigurationException;
    void refreshAllDataTags();
    void refreshDataTag(ISourceDataTag sourceDataTag);
    String updateAliveWriterAndReport ();

    // for dependency injection during testing
    Endpoint getEndpoint();
    EventPublisher getPublisher();
}
