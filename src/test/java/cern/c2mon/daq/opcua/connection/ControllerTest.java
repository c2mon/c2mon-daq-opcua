package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ControllerTest extends ControllerTestBase {

    @BeforeEach
    public void setupMocker() {
        mocker.replay();
    }

    @Test
    public void initializeShouldSubscribeTags() throws ConfigurationException {
        controller.initialize();
        sourceTags.values().forEach(dataTag -> Assertions.assertTrue(mapper.isSubscribed(dataTag)));
    }

    @Test
    public void stopShouldResetEndpoint() throws ConfigurationException {
        controller.initialize();
        controller.stop();
        Assertions.assertTrue(mapper.getGroups().isEmpty());
    }

}
