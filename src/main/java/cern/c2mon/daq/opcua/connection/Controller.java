package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EquipmentStateListener;
import cern.c2mon.daq.opcua.upstream.TagListener;
import cern.c2mon.shared.common.datatag.ISourceDataTag;

import java.util.List;
import java.util.Optional;

public interface Controller {

    String UA_TCP_TYPE = "opc.tcp";

    void initialize() throws ConfigurationException;
    void subscribe (TagListener listener);
    void subscribe (EquipmentStateListener listener);
    void stop();
    void checkConnection();
    void refreshAllDataTags();
    void refreshDataTag(ISourceDataTag sourceDataTag);
    String updateAliveWriterAndReport ();
    Endpoint getEndpoint();

    static EquipmentAddress getEquipmentAddress (List<EquipmentAddress> equipmentAddresses) throws ConfigurationException {
        Optional<EquipmentAddress> matchingAddress = equipmentAddresses.stream().filter(o -> o.getProtocol().equals(Controller.UA_TCP_TYPE)).findFirst();
        if (!matchingAddress.isPresent()) {
            throw new ConfigurationException(ConfigurationException.Cause.ENDPOINT_TYPES_UNKNOWN);
        }
        return matchingAddress.get();
    }
}
