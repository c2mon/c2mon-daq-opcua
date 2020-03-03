package cern.c2mon.daq.opcua.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ControllerTest extends ControllerTestBase {

    private Controller controller;

    @BeforeEach
    public void setup() {
        super.setup();
        setupWithAliveTag(true, aliveTag);
        controller = ControllerFactory.getController(config);
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
