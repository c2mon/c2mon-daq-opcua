package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.address.AddressStringParser;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@Setter
@Getter
public class ControllerProxy {

    @Autowired
    @Qualifier("controller")
    private final Controller controller;

    @Autowired
    @Qualifier("controllerWithAliveWriter")
    private final Controller controllerWithAliveWriter;

    @Autowired
    private final AliveWriter aliveWriter;

    @Getter
    @Autowired
    private final MiloClientWrapper wrapper;

    public Controller getController(IEquipmentConfiguration config) throws ConfigurationException {
        String uaTcpType = "opc.tcp";
        EquipmentAddress equipmentAddress = AddressStringParser.parse(config.getAddress());

        if (!equipmentAddress.supportsProtocol(uaTcpType)) {
            throw new ConfigurationException(ConfigurationException.Cause.ENDPOINT_TYPES_UNKNOWN);
        }
        EquipmentAddress.ServerAddress address = equipmentAddress.getServerAddressWithProtocol(uaTcpType);
        wrapper.initialize(address.getUriString());

        if (equipmentAddress.isAliveWriterEnabled()) {
            try {
                ISourceDataTag aliveTag = config.getSourceDataTag(config.getAliveTagId());
                aliveWriter.initialize(config.getAliveTagInterval() / 2, aliveTag);
                return controllerWithAliveWriter;
            } catch (ConfigurationException e) {
                log.error("Creating the AliveWriter skipped. ", e);
            }
        }
        return controller;
    }
}