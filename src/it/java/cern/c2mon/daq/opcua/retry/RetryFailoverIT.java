package cern.c2mon.daq.opcua.retry;

import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.EndpointDisconnectedException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.testutils.TestController;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:retry.properties")
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RetryFailoverIT {

    @Autowired AppConfigProperties config;
    @Autowired TestController testController;

    @BeforeEach
    public void setUp() {
        config.setFailoverDelay(1L);
        testController.setStopped(false);
        testController.setListening(true);
        testController.setTo(2, null);
    }

    @Test
    public void successfulSwitchShouldNotBeRetried() {
        config.setRetryMultiplier(1);
        config.setRetryDelay(1L);
        testController.triggerRetryFailover();
        Assertions.assertTrue(testController.getSwitchDone().isDone());
    }

    @Test
    public void endpointDisconnectedShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new EndpointDisconnectedException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        Assertions.assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void communicationExceptionShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        Assertions.assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void configurationExceptionShouldBeRetried() throws InterruptedException {
        testController.setToThrow(new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION));
        CompletableFuture.runAsync(() -> testController.triggerRetryFailover());
        Assertions.assertTrue(testController.getServerSwitchLatch().await(2000L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void stoppedEndpointShouldNotBeRetried() {
        testController.setToThrow(new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION));
        testController.setStopped(true);
        testController.triggerRetryFailover();
        Assertions.assertTrue(testController.getSwitchDone().isDone());
    }
}
