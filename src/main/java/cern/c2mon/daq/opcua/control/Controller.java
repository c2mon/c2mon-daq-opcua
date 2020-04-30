package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.EndpointListener;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;

public interface Controller {

    void initialize(IEquipmentConfiguration config) throws ConfigurationException;
    void connect(boolean connectionLost) throws ConfigurationException;
    void stop();
    void checkConnection() throws ConfigurationException;
    void refreshAllDataTags();
    void refreshDataTag(ISourceDataTag sourceDataTag);
    String updateAliveWriterAndReport ();
    void subscribe (EndpointListener listener);
    String runCommand(ISourceCommandTag tag, SourceCommandTagValue value) throws ConfigurationException;

    // for dependency injection during testing
    Endpoint getEndpoint();
    void setConfig(IEquipmentConfiguration config);
}
