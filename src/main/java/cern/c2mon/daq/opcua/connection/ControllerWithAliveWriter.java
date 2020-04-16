package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;

public class ControllerWithAliveWriter extends ControllerImpl {

    final AliveWriter aliveWriter;

    public ControllerWithAliveWriter (Endpoint endpoint, AliveWriter aliveWriter) {
        super(endpoint);
        this.aliveWriter = aliveWriter;
    }

    @Override
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
