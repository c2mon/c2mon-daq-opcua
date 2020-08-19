package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.*;

public class MiloEndpointTest {

    Endpoint endpoint;
    OpcUaClient mockClient = createNiceMock(OpcUaClient.class);
    RetryDelegate retryDelegate = createNiceMock(RetryDelegate.class);
    SecurityModule securityModule = createNiceMock(SecurityModule.class);
    OpcUaSubscriptionManager subscriptionManager = createNiceMock(OpcUaSubscriptionManager.class);

    @BeforeEach
    public void setUp() {
        AppConfigProperties config = AppConfigProperties.builder().maxRetryAttempts(3).requestTimeout(300).retryDelay(1000).build();
        endpoint = new MiloEndpoint(securityModule, retryDelegate, null, null, config.exponentialDelayTemplate());
        expect(mockClient.getSubscriptionManager()).andReturn(subscriptionManager).anyTimes();
        replay(mockClient);
    }

    @Test
    public void exceptionInInitializeShouldBeEscalated() throws OPCUAException {
        expect(retryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andThrow(new CommunicationException(ExceptionContext.CONNECT))
                .anyTimes();
        replay(retryDelegate);
        Assertions.assertThrows(OPCUAException.class, () -> endpoint.initialize("test"));
    }
/*

    @Test
    public void localUrisShouldBePassedAsGlobalUris() throws OPCUAException {
        final EndpointDescription description = EndpointDescription.builder().endpointUrl("opc.tcp://local:200").build();
        Capture<Collection<EndpointDescription>> capturedDescriptions = Capture.newInstance(CaptureType.ALL);
        Capture<Supplier<CompletableFuture<OpcUaClient>>> captureSupplier = Capture.newInstance(CaptureType.ALL);
        expect(retryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(description))
                .once();
        expect(retryDelegate.completeOrRetry(capture(newCapture(CaptureType.ALL)), capture(newCapture(CaptureType.ALL)), capture(captureSupplier)))
                .andReturn(mockClient).once();
        expect(securityModule.createClient(capture(capturedDescriptions)))
                .andReturn(CompletableFuture.completedFuture(mockClient))
                .anyTimes();
        replay(retryDelegate, securityModule);
        endpoint.initialize("opc.tcp://test.cern.ch:200");
        captureSupplier.getValue().get();
        assertEquals("opc.tcp://local.cern.ch:200", capturedDescriptions.getValue().stream().map(EndpointDescription::getEndpointUrl).collect(Collectors.joining("")));
    }
*/

}
