package cern.c2mon.daq.opcua.connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EndpointControllerFactoryTest extends EndpointControllerTestBase {

    @Test
    public void createControllerWithoutAliveWriter() {
        setupWithAliveTag(false, aliveTag);

        EndpointController controller = EndpointControllerFactory.getController(config);

        Assertions.assertTrue(controller instanceof EndpointControllerImpl);
    }

    @Test
    public void createControllerWithAliveWriter() {
        setupWithAliveTag(true, aliveTag);
        EndpointController controller = EndpointControllerFactory.getController(config);
        Assertions.assertTrue(controller instanceof EndpointControllerWithAliveWriter);
    }

    @Test
    public void withInvalidAliveSourceTagShouldCreateControllerWithoutAliveWriter () {
        setupWithAliveTag(true, null);
        EndpointController controller = EndpointControllerFactory.getController(config);
        Assertions.assertTrue(controller instanceof EndpointControllerImpl);
    }
}
