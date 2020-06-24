package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.LongLostConnectionException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;
import static org.eclipse.milo.opcua.stack.core.StatusCodes.Bad_NodeIdUnknown;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MiloEndpointTest {

    Endpoint endpoint;
    OpcUaClient mockClient = createMock(OpcUaClient.class);

    @BeforeEach
    public void setUp() {
        AppConfig config = AppConfig.builder().maxRetryAttempts(3).timeout(300).retryDelay(1000).build();
        endpoint = new MiloEndpoint(null, new RetryDelegate(config), null, null);
        ReflectionTestUtils.setField(endpoint, "client", mockClient);
    }

    private void setUpDeleteSubscriptionMocks(Exception e) {
        expect(mockClient.writeValue(anyObject(), anyObject()))
                .andReturn(CompletableFuture.failedFuture(e))
                .anyTimes();
        replay(mockClient);
    }

    @Test
    public void uaExceptionOrTypeConfigShouldThrowConfigurationException() {
        setUpDeleteSubscriptionMocks(new UaException(Bad_NodeIdUnknown));
        assertThrows(ConfigurationException.class, () -> endpoint.write(NodeId.NULL_GUID, 1));
    }

    @Test
    public void unknownHostShouldThrowConfigurationException() {
        setUpDeleteSubscriptionMocks(new UnknownHostException());
        assertThrows(ConfigurationException.class, () -> endpoint.write(NodeId.NULL_GUID, 1));
    }

    @Test
    public void anyOtherExceptionShouldThrowCommunicationException() {
        setUpDeleteSubscriptionMocks(new IllegalArgumentException());
        assertThrows(CommunicationException.class, () -> endpoint.write(NodeId.NULL_GUID, 1));
    }

    @Test
    public void alreadyAttemptingResubscriptionTooLongThrowsLongLostConnectionException() {
        ReflectionTestUtils.setField(endpoint, "disconnectedOn", 20);
        setUpDeleteSubscriptionMocks(new LongLostConnectionException(ExceptionContext.DELETE_SUBSCRIPTION, new IllegalArgumentException()));
        assertThrows(LongLostConnectionException.class, () -> endpoint.write(NodeId.NULL_GUID, 1));
    }
}
