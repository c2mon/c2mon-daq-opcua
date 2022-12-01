/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package cern.c2mon.daq.opcua.iotedge;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.taghandling.CommandTagHandler;
import cern.c2mon.daq.opcua.taghandling.DataTagHandler;
import cern.c2mon.daq.opcua.taghandling.IDataTagHandler;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
@Testcontainers
@TestPropertySource(locations = "classpath:application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class EdgeIT extends EdgeTestBase {

    private final ISourceDataTag tag = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    private final ISourceDataTag alreadySubscribedTag = EdgeTagFactory.DipData.createDataTag();

    TestListeners.Pulse pulseListener;
    Controller controller;
    IDataTagHandler tagHandler;
    CommandTagHandler commandTagHandler;

    @BeforeEach
    public void setupEndpoint() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        log.info("############ SET UP ############");
        super.setupEquipmentScope();
        pulseListener = new TestListeners.Pulse();
        controller = ctx.getBean(Controller.class);
        tagHandler = ctx.getBean(DataTagHandler.class);
        commandTagHandler = ctx.getBean(CommandTagHandler.class);

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
        final CompletableFuture<MessageSender.EquipmentState> connectionLost = pulseListener.getStateUpdate().get(0);
        active.image.stop();
        connectionLost.get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS);
        log.info("Lost connection.");
        pulseListener.reset();
        pulseListener.setSourceID(alreadySubscribedTag.getId());
        CompletableFuture<MessageSender.EquipmentState> completed = pulseListener.getStateUpdate().get(0);
        active.image.start();
        assertEquals(MessageSender.EquipmentState.OK, completed.get(TestUtils.TIMEOUT_REDUNDANCY, TimeUnit.MINUTES));
        log.info("Regained connection.");

        log.info("Waiting for ValueUpdate for Tag with ID {}.", alreadySubscribedTag.getId());
        assertDoesNotThrow(() -> pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void regainedConnectionShouldContinueDeliveringSubscriptionValues() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("############ regainedConnectionShouldContinueDeliveringSubscriptionValues ############");
        tagHandler.subscribeTags(Collections.singletonList(tag));
        waitUntilRegistered(() -> pulseListener.getStateUpdate().get(0), active, true);
        waitUntilRegistered(() -> pulseListener.getStateUpdate().get(0), active, false);

        assertDoesNotThrow(() -> pulseListener.getTagUpdate().get(0).get(TestUtils.TIMEOUT_TOXI, TimeUnit.SECONDS));
    }

    @Test
    public void connectionCutServerShouldSendLOST() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("############ connectionCutServerShouldSendLOST ############");
        assertEquals(CONNECTION_LOST, waitUntilRegistered(() -> pulseListener.getStateUpdate().get(0), active, true));
        TimeUnit.MILLISECONDS.sleep(1000L);
        waitUntilRegistered(() -> pulseListener.getStateUpdate().get(0), active, false); //cleanup
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
    public void removeItemFromSubscriptionShouldNotUpdateIt() throws InterruptedException, ExecutionException, TimeoutException {
        log.info("############ removeItemFromSubscriptionShouldNotUpdateIt ############");
        tagHandler.subscribeTags(Collections.singletonList(tag));
        pulseListener.reset();
        pulseListener.setSourceID(tag.getId());
        pulseListener.getTagValUpdate().get(TestUtils.TIMEOUT_IT, TimeUnit.MILLISECONDS);
        tagHandler.removeTag(tag);
        pulseListener.setSourceID(tag.getId());
        pulseListener.reset();
        tagHandler.refreshAllDataTags();
        assertThrows(TimeoutException.class, () -> pulseListener.getTagValUpdate().get(100L, TimeUnit.MILLISECONDS));
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
    public void subscribeWithDeadband() {
        log.info("############ subscribeWithDeadband ############");
        final ISourceDataTag tagWithDeadband = EdgeTagFactory.RandomUnsignedInt32.createDataTag(10, ValueDeadbandType.NONE, 0);
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
