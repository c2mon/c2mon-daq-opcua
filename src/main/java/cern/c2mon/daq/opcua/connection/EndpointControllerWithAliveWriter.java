package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.EndpointTypesUnknownException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;

public class EndpointControllerWithAliveWriter extends EndpointControllerImpl {

    AliveWriter aliveWriter;

    public EndpointControllerWithAliveWriter (Endpoint endpoint,
                                              IEquipmentConfiguration config,
                                              EventPublisher publisher,
                                              AliveWriter aliveWriter) {
        super(endpoint, config, publisher);
        this.aliveWriter = aliveWriter;
    }

    public void initialize () throws EndpointTypesUnknownException, OPCCommunicationException {
        super.initialize();
        aliveWriter.startWriter();
    }

    @Override
    public String updateAliveWriterAndReport () {
        aliveWriter.startWriter();
        aliveWriter.stopWriter();
        return "Alive Writer updated";
    }
}
