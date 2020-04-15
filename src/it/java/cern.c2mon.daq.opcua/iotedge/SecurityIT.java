package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapper;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.security.*;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    private TagSubscriptionMapper mapper = new TagSubscriptionMapperImpl();
    private MiloClientWrapper wrapper;
    private EventPublisher publisher = new EventPublisher();
    private static GenericContainer image;
    private static String uri;

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

    @AfterEach
    public void cleanUp() {
        endpoint.reset();
        endpoint = null;
    }

    private void initializeEndpointWithCertificate(Certifier certifier) {
        wrapper = new MiloClientWrapperImpl(uri, certifier);
        endpoint = new EndpointImpl(wrapper, mapper, publisher);
        endpoint.initialize(false);
    }

    @Test
    public void connectWithoutCertificate() {
        initializeEndpointWithCertificate(new NoSecurityCertifier());
        assertDoesNotThrow(()-> endpoint.isConnected());
    }

    @Test
    public void connectWithSelfSignedCertificateShouldThrowAuthenticationError() {
        //certificate should be rejected
        assertThrows(OPCCommunicationException.class,
                ()->initializeEndpointWithCertificate(new SelfSignedCertifier()),
                "Certificate is not trusted.");
    }
    @Test
    public void trustedCertificateShouldAllowConnection() throws IOException, InterruptedException {
        SelfSignedCertifier cert = new SelfSignedCertifier();
        //should be rejected
        assertThrows(OPCCommunicationException.class,
                ()->initializeEndpointWithCertificate(cert),
                "Certificate is not trusted.");

        //move certificate to trusted
        image.execInContainer("mkdir", "pki/trusted");
        image.execInContainer("cp", "-r", "pki/rejected/certs", "pki/trusted");

        assertDoesNotThrow(()->initializeEndpointWithCertificate(cert));
    }
}
