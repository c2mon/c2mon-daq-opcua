package cern.c2mon.daq.opcua.exceptions;

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;

public abstract class OPCUAException extends EqIOException {
    public OPCUAException(String message) {
        super(message);
    }

    public OPCUAException(String message, Throwable e) {
        super(message, e);
    }
}
