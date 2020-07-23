package cern.c2mon.daq.opcua.exceptions;

/**
 * This exception is thrown when communicating with the OPC UA server fails for reasons that are not mapped to
 * misconfiguration. This exception can be due to a temporary reason (network down). It may be fruitful to retry an
 * action after this exception.
 */
public class CommunicationException extends OPCUAException {

    /**
     * Creates a new CommunicationException wrapping the throwable which caused an action to fail.
     * @param context The context the exception occurred in.
     * @param cause   the throwable to wrap as an OPCCommunicationException.
     */
    public CommunicationException(final ExceptionContext context, final Throwable cause) {
        super(context, cause);
    }

    /**
     * Create an new CommunicationException.
     * @param context gives more details about cause and context of the exception.
     */
    public CommunicationException(final ExceptionContext context) {
        super(context);
    }
}