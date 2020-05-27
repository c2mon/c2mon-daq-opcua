package cern.c2mon.daq.opcua.exceptions;

import cern.c2mon.daq.tools.equipmentexceptions.EqIOException;
import com.google.common.collect.ImmutableSet;
import org.eclipse.milo.opcua.stack.core.UaException;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_CertificateIssuerRevoked;

public abstract class OPCUAException extends EqIOException {

    /**
     * Status Codes representing states which impact further interaction with the server
     */
    static final ImmutableSet<Long> TERMINAL = ImmutableSet.<Long>builder().add(Bad_InternalError, Bad_OutOfMemory, Bad_Shutdown, Bad_ServerHalted, Bad_NonceInvalid, Bad_TooManySessions, Bad_TcpInternalError, Bad_DeviceFailure, Bad_SensorFailure, Bad_OutOfService).build();

    /**
     * Status Codes representing states that could be solved by disconnecting and reconnecting
     */
    static final ImmutableSet<Long> RECONNECT = ImmutableSet.<Long>builder().add(Bad_ServerNotConnected, Bad_SessionIdInvalid, Bad_SessionClosed, Bad_SessionNotActivated, Bad_NoCommunication, Bad_NotConnected, Bad_Disconnect).build();

    /**
     * Status Codes representing states where retrying an operation again may work
     */
    static final ImmutableSet<Long> RETRY_LATER = ImmutableSet.<Long>builder().add(Bad_ResourceUnavailable, Uncertain_NoCommunicationLastUsableValue, Uncertain_SensorNotAccurate, Bad_TooManyPublishRequests).build();

    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    static final ImmutableSet<Long> CONFIG = ImmutableSet.<Long>builder().add(Bad_NodeIdUnknown, Bad_ServerUriInvalid, Bad_FilterNotAllowed, Bad_ServerNameMissing, Bad_DiscoveryUrlMissing, Bad_DeadbandFilterInvalid, Bad_ConfigurationError, Bad_TcpEndpointUrlInvalid, Bad_MethodInvalid, Bad_ArgumentsMissing, Bad_WriteNotSupported, Bad_HistoryOperationUnsupported, Bad_HistoryOperationInvalid, Bad_NoDeleteRights, Bad_TargetNodeIdInvalid, Bad_SourceNodeIdInvalid, Bad_NodeIdRejected, Bad_FilterOperandInvalid).build();


    /**
     * Status Codes returned by the server hinting at misconfigurations.
     */
    static final ImmutableSet<Long> SECURITY_CONFIG = ImmutableSet.<Long>builder().add(Bad_UserSignatureInvalid, Bad_UserAccessDenied, Bad_CertificateHostNameInvalid, Bad_ApplicationSignatureInvalid, Bad_CertificateIssuerUseNotAllowed, Bad_CertificateIssuerTimeInvalid, Bad_CertificateIssuerRevoked).build();


    public OPCUAException(String message) {
        super(message);
    }

    public OPCUAException(String message, Throwable e) {
        super(message, e);
    }


    public static boolean reconnectRequired(Exception e) {
        if (e instanceof UaException) {
            final var code = ((UaException) e).getStatusCode().getValue();
            return RECONNECT.contains(code);
        }
        return false;
    }

    public static boolean reconfigurationRequired(CommunicationException e) {
        final var cause = e.getCause();
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            return CONFIG.contains(code);
        }
        return false;
    }

    public static boolean isTerminal(CommunicationException e) {
        final var cause = e.getCause();
        if (cause instanceof UaException) {
            final var code = ((UaException) cause).getStatusCode().getValue();
            return TERMINAL.contains(code);
        }
        return false;
    }

}
