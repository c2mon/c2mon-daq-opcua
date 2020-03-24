package cern.c2mon.daq.opcua.exceptions;

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import lombok.AllArgsConstructor;

public class ConfigurationException extends EqIOException {

    @AllArgsConstructor
    public enum Cause {
        ADDRESS_URI("Cannot parse equipment address: Syntax of OPC URI is incorrect"),
        ADDRESS_MISSING_PROPERTIES("The address does not contain all required properties"),
        ADDRESS_INVALID_PROPERTIES("The address does contains invalid properties"),
        ENDPOINT_TYPES_UNKNOWN("No supported protocol found"),
        HARDWARE_ADDRESS_UNKNOWN("The hardware address is not of type OPCHardwareAddress and cannot be handled"),
        MISSING_URI("The equipment address does not have an URI"),
        MISSING_TARGET_TAG("TargetTag must not be null"),
        DATATAGS_EMPTY("No data tags to subscribe");

        public final String message;

    }

    public ConfigurationException(final Cause type, final Exception e) {
        super(type.message, e);
    }

    public ConfigurationException(final Cause type) {
        super(type.message);
    }
}