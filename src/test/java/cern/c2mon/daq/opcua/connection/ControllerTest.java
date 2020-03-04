package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ControllerTest extends ControllerTestBase {

    private Controller controller;
    private IEquipmentMessageSender sender = EasyMock.createMock(IEquipmentMessageSender.class);

    @BeforeEach
    public void setup() throws ConfigurationException {
        super.setup();
        setupWithAliveTag(true, aliveTag);
        controller = ControllerFactory.getController(config, sender);
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
