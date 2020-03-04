package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;

public class ControllerWithAliveWriter extends ControllerImpl {

    AliveWriter aliveWriter;

    public ControllerWithAliveWriter (Endpoint endpoint,
                                      IEquipmentConfiguration config,
                                      EventPublisher publisher,
                                      AliveWriter aliveWriter) {
        super(endpoint, config, publisher);
        this.aliveWriter = aliveWriter;
    }

    public void initialize () throws ConfigurationException {
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
