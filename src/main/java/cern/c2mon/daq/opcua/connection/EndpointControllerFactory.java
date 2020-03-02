package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.address.AddressStringParser;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.exceptions.AddressException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

import java.util.List;

@Slf4j
public class EndpointControllerFactory {

    public static EndpointController getController (IEquipmentConfiguration config) throws AddressException {
        List<EquipmentAddress> equipmentAddresses = AddressStringParser.parse(config.getAddress());
        EquipmentAddress address = EndpointController.getEquipmentAddress(equipmentAddresses);

        MiloClientWrapper wrapper = new MiloClientWrapperImpl(address.getUriString(), SecurityPolicy.None);
        Endpoint endpoint = new EndpointImpl(new TagSubscriptionMapperImpl(), wrapper);

        if (equipmentAddresses.get(0).isAliveWriterEnabled()) {
            try {
                AliveWriter aliveWriter = createAliveWriter(config, endpoint);
                return new EndpointControllerWithAliveWriter(endpoint, config, aliveWriter);
            } catch (IllegalArgumentException e) {
                log.error("Creating the AliveWriter skipped. ", e);
            }
        }
        return new EndpointControllerImpl(endpoint, config);
    }

    private static AliveWriter createAliveWriter (IEquipmentConfiguration config, Endpoint endpoint) throws IllegalArgumentException {
        ISourceDataTag aliveTag = config.getSourceDataTag(config.getAliveTagId());
        return new AliveWriter(endpoint, config.getAliveTagInterval() / 2, aliveTag);
    }

}