package cern.c2mon.daq.opcua.exceptions;

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import com.google.common.collect.ImmutableSet;
import org.eclipse.milo.opcua.stack.core.UaException;

import java.net.UnknownHostException;
import java.util.Collection;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * An abstract EqIOException occurring in the context of communication with an OPC UA server.
 */
public abstract class OPCUAException extends EqIOException {

    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    private static final Collection<Long> CONFIG = ImmutableSet.<Long>builder().add(
            Bad_NodeIdUnknown,
            Bad_ServerUriInvalid,
            Bad_FilterNotAllowed,
            Bad_ServerNameMissing,
            Bad_DiscoveryUrlMissing,
            Bad_DeadbandFilterInvalid,
            Bad_ConfigurationError,
            Bad_TcpEndpointUrlInvalid,
            Bad_MethodInvalid,
            Bad_ArgumentsMissing,
            Bad_WriteNotSupported,
            Bad_HistoryOperationUnsupported,
            Bad_HistoryOperationInvalid,
            Bad_NoDeleteRights,
            Bad_TargetNodeIdInvalid,
            Bad_SourceNodeIdInvalid,
            Bad_NodeIdRejected,
            Bad_FilterOperandInvalid,
            Bad_ServiceUnsupported).build();

    /**
     * Status Codes returned by the server hinting at misconfigurations of security settings.
     */
    private static final Collection<Long> SECURITY_CONFIG = ImmutableSet.<Long>builder().add(
            Bad_UserSignatureInvalid,
            Bad_UserAccessDenied,
            Bad_CertificateHostNameInvalid,
            Bad_ApplicationSignatureInvalid,
            Bad_CertificateIssuerUseNotAllowed,
            Bad_CertificateIssuerTimeInvalid,
            Bad_CertificateIssuerRevoked).build();


    private static final Collection<Long> ENDPOINT_DISCONNECTED = ImmutableSet.<Long>builder().add(
            Bad_SessionClosed,
            Bad_SecureChannelClosed).build();

    protected OPCUAException (final ExceptionContext context) {
        super(context.getMessage());
    }

    protected OPCUAException (final ExceptionContext context, final Throwable e) {
        super(context.getMessage(), e);
    }

    /**
     * A static factory method called when the execution of an action on the server failed. The throwable causing this
     * failure is wrapped in a subclass of OPCUAException best describing the context of the exception, and the
     * prospects of retrying the action.
     *
     * @param context The context that the  throwable e occurred in, used to form an informative error
     *                message.
     * @param e       The throwable which caused the action on the server to fail originally.
     * @return A concrete OPCUAException of type {@link ConfigurationException}, if the original error hints at an issue
     * with the equipment configuration, of type {@link EndpointDisconnectedException} if the endpoint has been connected by the DAQ. In all other cases a {@link
     * CommunicationException} is Thrown.
     */
    public static OPCUAException of(ExceptionContext context, Throwable e, boolean disconnectionTooLong) {
        if (isConfigIssue(e)) {
            return new ConfigurationException(context, e);
        } else if (isEndpointDisconnectedIssue(e)) {
            return new EndpointDisconnectedException(context, e);
        } else if (disconnectionTooLong) {
            return new LongLostConnectionException(context, e.getCause());
        }
        return new CommunicationException(context, e);
    }

    /**
     * Checks whether the UaException e was throws due to a general security issue.
     *
     * @param e the UaException to classify.
     * @return true is the exception e is due to a security general issue.
     */
    public static boolean isSecurityIssue (UaException e) {
        return SECURITY_CONFIG.contains(e.getStatusCode().getValue());
    }

    private static boolean isConfigIssue (Throwable e) {
        return e instanceof UnknownHostException || e instanceof UaException && CONFIG.contains(((UaException) e).getStatusCode().getValue());
    }

    private static boolean isEndpointDisconnectedIssue (Throwable e) {
        return e instanceof UaException && ENDPOINT_DISCONNECTED.contains(((UaException) e).getStatusCode().getValue());
    }

}