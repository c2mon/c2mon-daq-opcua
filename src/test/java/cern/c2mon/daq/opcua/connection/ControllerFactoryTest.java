package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cern.c2mon.daq.opcua.connection.ControllerTestBase.*;
import static org.easymock.EasyMock.createMock;

public class ControllerFactoryTest {

    IEquipmentConfiguration config;
    IEquipmentMessageSender sender = createMock(IEquipmentMessageSender.class);

    @BeforeEach
    public void setup() {
        config = createMockConfig();
    }

    @AfterEach
    public void teardown() {
        config = null;
    }

    @Test
    public void createControllerWithoutAliveWriter() throws ConfigurationException {
        setupWithAddressAndAliveTag(config, ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, false, aliveTag);
        Controller controller = ControllerFactory.getController(config, sender);
        Assertions.assertTrue(controller instanceof ControllerImpl);
    }

    @Test
    public void createControllerWithAliveWriter() throws ConfigurationException {
        setupWithAddressAndAliveTag(config,ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, true, aliveTag);
        Controller controller = ControllerFactory.getController(config, sender);
        Assertions.assertTrue(controller instanceof ControllerWithAliveWriter);
    }

    @Test
    public void withInvalidAliveSourceTagShouldCreateControllerWithoutAliveWriter () throws ConfigurationException {
        setupWithAddressAndAliveTag(config,ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, true, null);
        Controller controller = ControllerFactory.getController(config, sender);
        Assertions.assertTrue(controller instanceof ControllerImpl);
    }

    @Test
    public void nonOpcUaAddressShouldThrowError() throws ConfigurationException {
        setupWithAddressAndAliveTag(config, ADDRESS_PROTOCOL_INVALID + ADDRESS_BASE, true, null);
        Assertions.assertThrows(ConfigurationException.class, () -> ControllerFactory.getController(config, sender));

    }
}
