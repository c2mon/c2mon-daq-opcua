package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.CONNECTION_LOST;
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
    @Autowired IDataTagHandler tagHandler;
    @Autowired CommandTagHandler commandTagHandler;

    @BeforeEach
    public void setupEndpoint() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("############ SET UP ############");
        pulseListener.setSourceID(tag.getId());
        ReflectionTestUtils.setField(tagHandler, "messageSender", pulseListener);
        final Endpoint e = (Endpoint) ReflectionTestUtils.getField(controller, "endpoint");
        ReflectionTestUtils.setField(e, "messageSender", pulseListener);

        controller.connect(Collections.singleton(active.getUri()));
        tagHandler.subscribeTags(Collections.singletonList(alreadySubscribedTag));
        pulseListener.getTagUpdate().get(0).get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        pulseListener.reset();
        log.info("Client ready");
    }

    @AfterEach
    public void cleanUp() {
        log.info("############ CLEAN UP ############");
        controller.stop();
    }

    @Test
    public void executeCommandWithParentShouldReturnProperResult() throws EqCommandTagException {
        log.info("############ executeCommandWithParentShouldReturnProperResult ############");
        final ISourceCommandTag methodTag = EdgeTagFactory.StartStepUp.createMethodTag(true);
        final SourceCommandTagValue value = new SourceCommandTagValue();
        final String s = commandTagHandler.runCommand(methodTag, value);
        assertTrue(s.isEmpty());
    }

    @Test
    public void executeCommandShouldReturnProperResult() throws EqCommandTagException {
        log.info("############ executeCommandShouldReturnProperResult ############");
        final ISourceCommandTag methodTag = EdgeTagFactory.StartStepUp.createMethodTag(false);
        final SourceCommandTagValue value = new SourceCommandTagValue();
        final String s = commandTagHandler.runCommand(methodTag, value);
        assertTrue(s.isEmpty());
    }

    @Test
    public void restartServerShouldReconnectAndResubscribe() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("############ restartServerShouldReconnectAndResubscribe ############");
        final CompletableFuture<MessageSender.EquipmentState> connectionLost = pulseListener.listen();
        active.image.stop();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);

        final CompletableFuture<MessageSender.EquipmentState> connectionRegained = pulseListener.listen();
        active.image.start();
        assertEquals(MessageSender.EquipmentState.OK, connectionRegained.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));

        pulseListener.reset();
        pulseListener.setSourceID(alreadySubscribedTag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void regainedConnectionShouldContinueDeliveringSubscriptionValues() throws InterruptedException, ExecutionException, TimeoutException, ConfigurationException {
        log.info("############ regainedConnectionShouldContinueDeliveringSubscriptionValues ############");
        tagHandler.subscribeTags(Collections.singletonList(tag));
        doAndWait(pulseListener.listen(), active, true);
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        doAndWait(pulseListener.listen(), active, false);

        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void connectionCutServerShouldSendLOST() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("############ connectionCutServerShouldSendLOST ############");
        assertEquals(CONNECTION_LOST, doAndWait(pulseListener.listen(), active, true));
        doAndWait(pulseListener.listen(), active, false); //cleanup
    }

    @Test
    public void subscribingProperDataTagShouldReturnValue() {
        log.info("############ subscribingProperDataTagShouldReturnValue ############");
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.setSourceID(tag.getId());
        Object o = assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
        assertNotNull(o);
    }

    @Test
    public void subscribingImproperDataTagShouldReturnOnTagInvalid() {
        log.info("############ subscribingImproperDataTagShouldReturnOnTagInvalid ############");
        final ISourceDataTag tag = EdgeTagFactory.Invalid.createDataTag();
        pulseListener.setSourceID(tag.getId());
        pulseListener.setThreshold(0);
        final CompletableFuture<Long> f = pulseListener.getTagInvalid().get(0);
        tagHandler.subscribeTags(Collections.singletonList(tag));
        assertDoesNotThrow(() -> f.get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithDeadband() throws ConfigurationException {
        log.info("############ subscribeWithDeadband ############");
        final ISourceDataTag tagWithDeadband = EdgeTagFactory.RandomUnsignedInt32.createDataTag(10, (short) DeadbandType.Absolute.getValue(), 0);
        pulseListener.setSourceID(tagWithDeadband.getId());
        tagHandler.subscribeTags(Collections.singletonList(tagWithDeadband));
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshProperTag() throws InterruptedException {
        log.info("############ refreshProperTag ############");
        pulseListener.setSourceID(tag.getId());
        tagHandler.refreshDataTag(tag);
        Thread.sleep(2000);
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS));
    }

}
