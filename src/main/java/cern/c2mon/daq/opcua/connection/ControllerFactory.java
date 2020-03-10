package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.address.AddressStringParser;
import cern.c2mon.daq.opcua.address.EquipmentAddress;
import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapper;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EndpointListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

@Slf4j
public abstract class ControllerFactory {

    public static Controller getController (IEquipmentConfiguration config, IEquipmentMessageSender sender) throws ConfigurationException {

        EquipmentAddress equipmentAddress = AddressStringParser.parse(config.getAddress());
        if (!equipmentAddress.supportsProtocol(Controller.UA_TCP_TYPE)) {
            throw new ConfigurationException(ConfigurationException.Cause.ENDPOINT_TYPES_UNKNOWN);
        }
        EquipmentAddress.ServerAddress address = equipmentAddress.getServerAddressWithProtocol(Controller.UA_TCP_TYPE);

        MiloClientWrapper wrapper = new MiloClientWrapperImpl(address.getUriString(), SecurityPolicy.None);
        TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
        EventPublisher publisher = createPublisherWithListeners(sender);

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

    public static EventPublisher createPublisherWithListeners (IEquipmentMessageSender sender) {
        EventPublisher publisher = new EventPublisher();
        EndpointListener endpointListener = new EndpointListener(sender);
        publisher.subscribeToTagEvents(endpointListener);
        publisher.subscribeToEquipmentStateEvents(endpointListener);
        return  publisher;
    }

}