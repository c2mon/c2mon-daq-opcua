package cern.c2mon.daq.opcua.connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ControllerFactoryTest extends ControllerTestBase {

    @Test
    public void createControllerWithoutAliveWriter() {
        setupWithAliveTag(false, aliveTag);

        Controller controller = ControllerFactory.getController(config);

        Assertions.assertTrue(controller instanceof ControllerImpl);
    }

    @Test
    public void createControllerWithAliveWriter() {
        setupWithAliveTag(true, aliveTag);
        Controller controller = ControllerFactory.getController(config);
        Assertions.assertTrue(controller instanceof ControllerWithAliveWriter);
    }

    @Test
    public void withInvalidAliveSourceTagShouldCreateControllerWithoutAliveWriter () {
        setupWithAliveTag(true, null);
        Controller controller = ControllerFactory.getController(config);
        Assertions.assertTrue(controller instanceof ControllerImpl);
    }
}
