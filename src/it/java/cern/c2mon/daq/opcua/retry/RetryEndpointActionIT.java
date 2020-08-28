package cern.c2mon.daq.opcua.retry;

import cern.c2mon.daq.opcua.SpringTestBase;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.easymock.EasyMock.*;

@TestPropertySource(locations = "classpath:retry.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RetryEndpointActionIT extends SpringTestBase {

    Endpoint endpoint;

    @Value("${c2mon.daq.opcua.maxRetryAttempts}")
    int maxRetryAttempts;

    OpcUaClient client = createMock(OpcUaClient.class);


    @BeforeEach
    public void setUp() throws ConfigurationException {
        super.setUp();
        endpoint = ctx.getBean(Endpoint.class);
        ReflectionTestUtils.setField(endpoint, "client", client);
    }

    @Test
    public void communicationExceptionShouldBeRepeatedNTimes() {
        //default exception thrown as CommunicationException
        verifyExceptionOnRead(new Exception(), maxRetryAttempts);
    }

    @Test
    public void configurationExceptionShouldNotBeRepeated() {
        //unknown host is thrown as configuration exception
        verifyExceptionOnRead(new UnknownHostException(), 1);
    }


    @Test
    public void longLostConnectionExceptionShouldNotBeRepeated() {
        //results in longLostConnectionException
        // scope prototype on delegate -> fetch proper instance
        ReflectionTestUtils.setField(endpoint, "disconnectedOn", new AtomicLong(2L));
        verifyExceptionOnRead(new Exception(), 1);
        ReflectionTestUtils.setField(endpoint, "disconnectedOn", new AtomicLong(0L));
    }

    private void verifyExceptionOnRead(Exception e, int numTimes) {
        final CompletableFuture<DataValue> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        final NodeId nodeId = new NodeId(1, 2);
        expect(client.readValue(0, TimestampsToReturn.Both, nodeId))
                .andReturn(f)
                .times(numTimes);
        replay(client);
        try {
            endpoint.read(nodeId);
        } catch (Exception ex) {
            //expected
        }
        verify(client);
    }

}
