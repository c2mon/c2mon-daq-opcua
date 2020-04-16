package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.configuration.AuthConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.SecurityProvider;
import cern.c2mon.daq.opcua.security.SelfSignedCertifier;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@SpringBootTest(classes = {AppConfig.class})
@TestPropertySource(locations = "classpath:opcua.properties")
@ExtendWith(SpringExtension.class)
public class SecurityIT {

    private Endpoint endpoint;
    private static GenericContainer image;
    private static String uri;

    SecurityProvider p = new SecurityProvider();
    AppConfig config;

    @BeforeAll
    public static void startServer() {
       image = new GenericContainer<>("mcr.microsoft.com/iotedge/opc-plc")
                .waitingFor(Wait.forLogMessage(".*OPC UA Server started.*\\n", 1))
                .withCommand("--unsecuretransport")
                .withNetworkMode("host");
        image.start();
        int PORT = 50000;
        uri = "opc.tcp://" + image.getContainerIpAddress() + ":" + PORT;
    }

    @AfterAll
    public static void stopServer() {
        image.stop();
        image.close();
    }

    @BeforeEach
    public void setUp() {
        config = AppConfig.builder()
                .appName("c2mon-opcua-daq")
                .applicationUri("urn:localhost:UA:C2MON")
                .productUri("urn:cern:ch:UA:C2MON")
                .organization("CERN")
                .organizationalUnit("C2MON team")
                .localityName("Geneva")
                .stateName("Geneva")
                .countryCode("CH")
                .build();
        AuthConfig auth = AuthConfig.builder().build();
        config.setAuth(auth);
    }

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
    }

    private void initializeEndpoint() {
        MiloClientWrapper wrapper = new MiloClientWrapperImpl();
        wrapper.initialize(uri);
        p.setConfig(config);
        wrapper.setProvider(p);
        wrapper.setConfig(config);
        endpoint = new EndpointImpl(wrapper, new TagSubscriptionMapperImpl(), new EventPublisher());
        endpoint.initialize(false);
    }

    @Test
    public void connectWithoutCertificate() {
        config.getAuth().setCommunicateWithoutSecurity(true);
        initializeEndpoint();
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectWithSelfSignedCertificateShouldThrowAuthenticationError() {
        //certificate should be rejected
        assertThrows(OPCCommunicationException.class,
                this::initializeEndpoint,
                "Certificate is not trusted.");
    }
    @Test
    public void trustedCertificateShouldAllowConnection() throws IOException, InterruptedException {
        SelfSignedCertifier cert = new SelfSignedCertifier();
        //should be rejected
        assertThrows(OPCCommunicationException.class,
                this::initializeEndpoint,
                "Certificate is not trusted.");

        //move certificate to trusted
        image.execInContainer("mkdir", "pki/trusted");
        image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");

        assertDoesNotThrow(this::initializeEndpoint);
    }
}
