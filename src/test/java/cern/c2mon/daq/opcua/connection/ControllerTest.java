package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class ControllerTest extends ControllerTestBase {

    protected Controller controller;
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



}
