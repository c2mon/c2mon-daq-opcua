package cern.c2mon.daq.opcua.exceptions;

/**
 * An exception which is unsolvable without a configuration change.
 */
public class ConfigurationException extends OPCUAException {

    /**
     * Creates a new ConfigurationException.
     * @param context describes the misconfiguration what triggered the exception.
     */
    public ConfigurationException (final ExceptionContext context) {
        super(context);
    }

    /**
     * Called Creates a new LongLostConnectionException wrapping the throwable which caused an action to fail.
     * @param context describes the context of the action which triggered the throwable cause.
     * @param cause   the throwable causing the action to fail.
     */
    public ConfigurationException (ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }
}