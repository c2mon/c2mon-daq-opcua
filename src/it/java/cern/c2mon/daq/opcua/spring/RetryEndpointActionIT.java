package cern.c2mon.daq.opcua.spring;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:retry.properties")
@ExtendWith(SpringExtension.class)
public class RetryEndpointActionIT {

    @Autowired
    Endpoint endpoint;

    @Value("${app.maxRetryAttempts}")
    int maxRetryAttempts;

    OpcUaClient client = createMock(OpcUaClient.class);

    @BeforeEach
    public void setUp() {
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
        RetryDelegate delegate = (RetryDelegate) ReflectionTestUtils.getField(endpoint, "retryDelegate");
        ReflectionTestUtils.setField(delegate, "disconnectionInstant", 2L);
        verifyExceptionOnRead(new Exception(), 1);
        ReflectionTestUtils.setField(delegate, "disconnectionInstant", 0L);
    }

    private void verifyExceptionOnRead(Exception e, int numTimes) {
        final NodeId nodeId = new NodeId(1, 2);
        expect(client.readValue(0, TimestampsToReturn.Both, nodeId))
                .andReturn(CompletableFuture.failedFuture(e))
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
