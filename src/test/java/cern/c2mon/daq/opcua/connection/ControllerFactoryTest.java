package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cern.c2mon.daq.opcua.connection.ControllerTestBase.*;

public class ControllerFactoryTest {

    IEquipmentConfiguration config;

    ControllerProxy proxy;

    @BeforeEach
    public void setup() {
        config = createMockConfig();
        AppConfig config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .enableInsecureCommunication(true)
                .enableOnDemandCertification(true)
                .build();
        SecurityModule p = new SecurityModule(config, new CertificateLoader(config.getKeystore()), new CertificateGenerator(config), new NoSecurityCertifier());
        MiloClientWrapper wrapper = new MiloClientWrapperImpl(p);
        EndpointImpl endpoint = new EndpointImpl(wrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
        AliveWriter aliveWriter = new AliveWriter(endpoint);
        Controller controllerWithAliveWriter = new ControllerWithAliveWriter(endpoint, aliveWriter);

        proxy = new ControllerProxy(new ControllerImpl(endpoint), controllerWithAliveWriter, aliveWriter, wrapper);
    }

    @AfterEach
    public void teardown() {
        config = null;
    }

    @Test
    public void createControllerWithoutAliveWriter() throws ConfigurationException {
        setupWithAddressAndAliveTag(config, ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, false, aliveTag);
        Controller controller = proxy.getController(config);
        Assertions.assertTrue(controller instanceof ControllerImpl);
    }

    @Test
    public void createControllerWithAliveWriter() throws ConfigurationException {
        setupWithAddressAndAliveTag(config,ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, true, aliveTag);
        Controller controller = proxy.getController(config);
        Assertions.assertTrue(controller instanceof ControllerWithAliveWriter);
    }

    @Test
    public void withInvalidAliveSourceTagShouldCreateControllerWithoutAliveWriter () throws ConfigurationException {
        setupWithAddressAndAliveTag(config,ADDRESS_PROTOCOL_TCP + ADDRESS_BASE, true, null);
        Controller controller = proxy.getController(config);
        Assertions.assertTrue(controller instanceof ControllerImpl);
    }

    @Test
    public void nonOpcUaAddressShouldThrowError() {
        setupWithAddressAndAliveTag(config, ADDRESS_PROTOCOL_INVALID + ADDRESS_BASE, true, null);
        Assertions.assertThrows(ConfigurationException.class, () -> proxy.getController(config));

    }
}
