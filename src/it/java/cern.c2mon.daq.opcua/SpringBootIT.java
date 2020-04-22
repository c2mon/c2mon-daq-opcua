package cern.c2mon.daq.opcua;

import cern.c2mon.daq.opcua.configuration.AppConfig;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.connection.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.security.CertificateGenerator;
import cern.c2mon.daq.opcua.security.CertificateLoader;
import cern.c2mon.daq.opcua.security.NoSecurityCertifier;
import cern.c2mon.daq.opcua.security.SecurityModule;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@SpringBootTest(classes = {AppConfig.class, EndpointImpl.class, EventPublisher.class, TagSubscriptionMapperImpl.class, MiloClientWrapperImpl.class, SecurityModule.class, NoSecurityCertifier.class, CertificateLoader.class, CertificateGenerator.class})
@TestPropertySource(locations = "classpath:opcua.properties")
@RunWith(SpringRunner.class)
public class SpringBootIT {

    @Autowired
    Endpoint endpoint;


    @Test
    public void testSpring() {
        endpoint.initialize(true);

    }
}
