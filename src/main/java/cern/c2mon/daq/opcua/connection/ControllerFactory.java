package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.address.AddressStringParser;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.security.*;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class ControllerFactory {

    public static Controller getController (IEquipmentConfiguration config) throws ConfigurationException {
        String uaTcpType = "opc.tcp";
        EquipmentAddress equipmentAddress = AddressStringParser.parse(config.getAddress());

        if (!equipmentAddress.supportsProtocol(uaTcpType)) {
            throw new ConfigurationException(ConfigurationException.Cause.ENDPOINT_TYPES_UNKNOWN);
        }
        EquipmentAddress.ServerAddress address = equipmentAddress.getServerAddressWithProtocol(uaTcpType);

        MiloClientWrapper wrapper = new MiloClientWrapperImpl(address.getUriString(), new NoSecurityCertifier());
        TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
        EventPublisher publisher = new EventPublisher();

        Endpoint endpoint = new EndpointImpl(wrapper, mapper, publisher);

        if (equipmentAddress.isAliveWriterEnabled()) {
            try {
                AliveWriter aliveWriter = createAliveWriter(config, endpoint);
                return new ControllerWithAliveWriter(endpoint, config, aliveWriter);
            } catch (ConfigurationException e) {
                log.error("Creating the AliveWriter skipped. ", e);
            }
        }
        return new ControllerImpl(endpoint, config);
    }

    private static AliveWriter createAliveWriter (IEquipmentConfiguration config, Endpoint endpoint) throws ConfigurationException {
        ISourceDataTag aliveTag = config.getSourceDataTag(config.getAliveTagId());
        return new AliveWriter(endpoint, config.getAliveTagInterval() / 2, aliveTag);
    }
}