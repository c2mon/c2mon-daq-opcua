package cern.c2mon.daq.opcua.spring;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.connection.RetryDelegate;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
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

import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SpringRetryTest {

    @Autowired
    RetryDelegate delegate;

    Endpoint endpoint = new MiloEndpoint(null, null, null, delegate);
    OpcUaClient client = createMock(OpcUaClient.class);

    @Value("${app.maxRetryAttempts}")
    int numAttempts;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(endpoint, "retryDelegate", delegate);
        ReflectionTestUtils.setField(endpoint, "client", client);

    }

    @Test
    public void communicationExceptionShouldBeCalledNTimes() {
        final NodeId nodeId = new NodeId(1, 2);
        expect(client.readValue(0, TimestampsToReturn.Both, nodeId))
                .andReturn(CompletableFuture.failedFuture(new CommunicationException(ExceptionContext.READ)))
                .times(numAttempts);

        replay(client);
        try {
            endpoint.read(nodeId);
        } catch (Exception e) {
            //expected
        }
        verify(client);
    }

}
