package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EquipmentStateListener;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ControllerTest extends ControllerTestBase {

    private Controller controller;
    private IEquipmentMessageSender sender = EasyMock.createMock(IEquipmentMessageSender.class);
    EquipmentStateListener equipmentStateListener;
    EventPublisher publisher;

    @BeforeEach
    public void setup() throws ConfigurationException {
        super.setup();
        setupWithAliveTag(true, aliveTag);

        publisher = new EventPublisher();

        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        controller = new ControllerImpl(endpoint, config, publisher);
    }


    @AfterEach
    public void teardown() {
        super.teardown();
        controller.stop();
        controller = null;
    }

    @Test
    public void initializeShouldInformListenerOfSuccess() throws ConfigurationException, ExecutionException, InterruptedException {
        CompletableFuture<Object> future = listenForEquipmentState();
        controller.initialize();
        assertEquals(EquipmentStateListener.EquipmentState.OK, future.get());

    }

    private CompletableFuture<Object> listenForEquipmentState () {
        CompletableFuture<Object> future = new CompletableFuture<>();
        publisher.subscribeToEquipmentStateEvents(future::complete);
        return future;
    }

}
