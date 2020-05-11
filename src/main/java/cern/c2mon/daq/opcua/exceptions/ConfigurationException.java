package cern.c2mon.daq.opcua.exceptions;

import lombok.AllArgsConstructor;

/**
 * Exception which is not solvable without a configuration change.
 *
 * @author Andreas Lang
 *
 */
public class ConfigurationException extends OPCUAException {

    @AllArgsConstructor
    public enum Cause {
        ADDRESS_URI("Cannot parse equipment address: Syntax of OPC URI is incorrect"),
        ADDRESS_MISSING_PROPERTIES("The address does not contain all required properties"),
        ADDRESS_INVALID_PROPERTIES("The address does contains invalid properties"),
        ENDPOINT_TYPES_UNKNOWN("No supported protocol found"),
        HARDWARE_ADDRESS_UNKNOWN("The hardware address is not of type OPCHardwareAddress and cannot be handled"),
        MISSING_URI("The equipment address does not have an URI"),
        MISSING_TARGET_TAG("TargetTag must not be null"),
        DATATAGS_EMPTY("No data tags to subscribe"),
        COMMAND_TYPE_UNKNOWN("The provided command type is unknown"),
        COMMAND_VALUE_ERROR("Provided command value could not be processed. Check data type and value."),
        SECURITY("Ensure your app security settings are valid."),
        OBJINVALID("Could not resolve an object node for the command tag."),
        DATATAG_UNKNOWN("Data tag is unknown.");

        public final String message;

    }

    public ConfigurationException(final Cause type, final Exception e) {
        super(type.message, e);
    }

    public ConfigurationException(final Cause type, final String message) {
        super(type.message + message);
    }

    public ConfigurationException(final Cause type) {
        super(type.message);
    }

    public ConfigurationException(ExceptionContext context, final Throwable cause) {
        super(context.getMessage(), cause);
    }
}
