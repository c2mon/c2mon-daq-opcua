package cern.c2mon.daq.opcua.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EndpointControllerTest extends EndpointControllerTestBase {

    private EndpointController controller;

    @BeforeEach
    public void setup() {
        super.setup();
        setupWithAliveTag(true, aliveTag);
        controller = EndpointControllerFactory.getController(config);
    }

    @AfterEach
    public void teardown() {
        super.teardown();
        controller.stop();
        controller = null;
    }

    @Test
    public void updateAliveWriterAndReportShouldReturnNotUpdated() {

    }


}
