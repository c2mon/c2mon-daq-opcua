package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.connection.EndpointListener;
import cern.c2mon.daq.opcua.control.CommandRunner;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.FailoverMode;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.EdgeTestBase;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.connection.EndpointListener.EquipmentState.CONNECTION_LOST;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@Testcontainers
@TestPropertySource(locations = "classpath:opcua.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EdgeIT extends EdgeTestBase {

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final ISourceDataTag alreadySubscribedTag = EdgeTagFactory.DipData.createDataTag();

    @Autowired TestListeners.Pulse pulseListener;
    @Autowired Controller controller;
    @Autowired CommandRunner commandRunner;
    @Autowired FailoverMode noFailover;

    @BeforeEach
    public void setupEndpoint() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("############ SET UP ############");
        pulseListener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(controller, "endpointListener", pulseListener);
        ReflectionTestUtils.setField(noFailover.currentEndpoint(), "endpointListener", pulseListener);

        controller.connect(active.getUri());
        controller.subscribeTags(Collections.singletonList(alreadySubscribedTag));
        pulseListener.getTagUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS); // that tag is subscribed
        pulseListener.reset();
        log.info("Client ready");
        log.info("############ TEST ############");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
    }

    @Test
    public void connectToBadServerShouldThrowErrorAndSendFAIL() throws InterruptedException, ExecutionException, TimeoutException {
        final var stateUpdate = pulseListener.getStateUpdate();
        assertThrows(OPCUAException.class, () -> controller.connect("opc.tcp://somehost/somepath"));
        assertEquals(EndpointListener.EquipmentState.CONNECTION_FAILED, stateUpdate.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void executeCommandWithParentShouldReturnProperResult() throws EqCommandTagException {
        final ISourceCommandTag methodTag = EdgeTagFactory.StartStepUp.createMethodTag(true);
        final SourceCommandTagValue value = new SourceCommandTagValue();
        final String s = commandRunner.runCommand(methodTag, value);
        assertTrue(s.isEmpty());
    }

    @Test
    public void executeCommandShouldReturnProperResult() throws EqCommandTagException {
        final ISourceCommandTag methodTag = EdgeTagFactory.StartStepUp.createMethodTag(false);
        final SourceCommandTagValue value = new SourceCommandTagValue();
        final String s = commandRunner.runCommand(methodTag, value);
        assertTrue(s.isEmpty());
    }

    @Test
    public void restartServerShouldReconnectAndResubscribe() throws InterruptedException, ExecutionException, TimeoutException {
        final var connectionLost = pulseListener.listen();
        active.image.close();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        final var connectionRegained = pulseListener.listen();
        active.image.start();
        assertEquals(EndpointListener.EquipmentState.OK, connectionRegained.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));

        pulseListener.reset();
        pulseListener.setSourceID(alreadySubscribedTag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void regainedConnectionShouldContinueDeliveringSubscriptionValues() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        controller.subscribeTags(Collections.singletonList(tag));
        cutConnection(pulseListener, active);
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        uncutConnection(pulseListener, active);

        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void connectionCutServerShouldSendLOST() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(CONNECTION_LOST, cutConnection(pulseListener, active));
        uncutConnection(pulseListener, active); //cleanup
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() throws ConfigurationException {
        controller.subscribeTags(Collections.singletonList(tag));
        pulseListener.setSourceID(tag.getId());
        Object o = assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid() throws ConfigurationException {
        final ISourceDataTag tag = EdgeTagFactory.Invalid.createDataTag();
        pulseListener.setSourceID(tag.getId());
        pulseListener.setThreshold(0);
        controller.subscribeTags(Collections.singletonList(tag));
        assertDoesNotThrow(() -> pulseListener.getTagInvalid().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws ConfigurationException {
        var tagWithDeadband = EdgeTagFactory.RandomUnsignedInt32.createDataTag(10, (short) DeadbandType.Absolute.getValue(), 0);
        pulseListener.setSourceID(tagWithDeadband.getId());
        controller.subscribeTags(Collections.singletonList(tagWithDeadband));
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag() throws InterruptedException {
        pulseListener.setSourceID(tag.getId());
        controller.refreshDataTag(tag);
        Thread.sleep(2000);
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

}
