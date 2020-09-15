/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2020 CERN
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
package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.DIPHardwareAddressImpl;
import cern.c2mon.shared.common.datatag.util.JmsMessagePriority;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class DataTagHandlerTest extends TagHandlerTestBase {
    SourceDataTag badTag;

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        final DIPHardwareAddressImpl dip = new DIPHardwareAddressImpl("badAddress");
        DataTagAddress tagAddress = new DataTagAddress(dip, 0, ValueDeadbandType.NONE, 0, 0, JmsMessagePriority.PRIORITY_LOW, true);
        badTag = new SourceDataTag(70L, "test", false, (short) 0, null, tagAddress);

        super.setUp();
        endpoint.setReturnGoodStatusCodes(true);
    }

    @Test
    public void subscribedTagsShouldBeAddedToMapper() throws OPCUAException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        tagHandler.subscribeTags(sourceTags.values());
        sourceTags.values().forEach(dataTag -> assertTrue(mapper.getGroup(dataTag.getTimeDeadband()).contains(dataTag.getId())));
    }

    @Test
    public void tagsWithBadHardwareAddressesShouldNotBeSubscribed() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        sourceTags.put(7L, badTag);

        tagHandler.subscribeTags(sourceTags.values());

        assertFalse(mapper.getGroup(0).contains(badTag.getId()));
    }

    @Test
    public void tagsWithBadHardwareAddressesShouldSubscribeAllOtherTags() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        sourceTags.put(7L, badTag);

        tagHandler.subscribeTags(sourceTags.values());

        assertTrue(mapper.getGroup(0).contains(tagInSource1.getId()));
        assertTrue(mapper.getGroup(0).contains(tagInSource2.getId()));
    }

    @Test
    public void subscribeTagWithBadHardwareAddressShouldReturnFalse() {
        assertFalse(tagHandler.subscribeTag(badTag));
    }

    @Test
    public void subscribeTagWithGoodQualityShouldReturnTrue() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagInSource1);
        mocker.replay();
        assertTrue(tagHandler.subscribeTag(tagInSource1));
    }

    @Test
    public void subscribeTagWithBadQualityShouldReturnFalse() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.UNCERTAIN, tagInSource1);
        mocker.replay();
        assertFalse(tagHandler.subscribeTag(tagInSource1));
    }

    @Test
    public void subscribeTagWithBadQualityShouldNotifyListener() throws ConfigurationException, InterruptedException, ExecutionException, TimeoutException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.UNCERTAIN, tagInSource1);
        mocker.replay();
        listener.reset();
        final CompletableFuture<Long> tagInvalid = listener.getTagInvalid().get(0);
        tagHandler.subscribeTag(tagInSource1);
        assertEquals(tagInSource1.getId().longValue(), tagInvalid.get(100, TimeUnit.MILLISECONDS).longValue());
    }

    @Test
    public void subscribeTagInInconsistentStateShouldNotInformTag() throws ConfigurationException, InterruptedException {
        endpoint.setDelay(200);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();

        listener.reset();
        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> tagHandler.subscribeTags(sourceTags.values()));
        TimeUnit.MILLISECONDS.sleep(100);
        s.submit(() -> mapper.clear());
        s.shutdown();
        s.awaitTermination(1000, TimeUnit.MILLISECONDS);

        assertFalse(listener.getTagUpdate().get(0).isDone());
    }

    @Test
    public void removeSubscribedTagShouldReturnTrue() throws ConfigurationException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        assertTrue(tagHandler.removeTag(tagInSource1));
    }

    @Test
    public void unsuccessfulRemovalShouldReturnFalse() throws ConfigurationException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        endpoint.setReturnGoodStatusCodes(false);
        assertFalse(tagHandler.removeTag(tagInSource1));
    }

    @Test
    public void removeSubscribedTagShouldRemoveTagFromMapper() throws ConfigurationException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        tagHandler.removeTag(tagInSource1);
        assertNull(mapper.getDefinition(tagInSource1.getId()));
    }

    @Test
    public void removeUnsubscribedTagShouldReturnFalse() {
        assertFalse(tagHandler.removeTag(tagInSource1));
    }


    @Test
    public void removeUnsubscribedTagFromExistingGroupShouldReturnFalse() throws ConfigurationException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        assertFalse(tagHandler.removeTag(tagInSource2));
    }

    @Test
    public void refreshAllDataTagsWhenNothingIsSubscribedShouldDoNothing() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        final CompletableFuture<Long> f = listener.getTagUpdate().get(0);
        tagHandler.refreshAllDataTags();
        assertFalse(f.isDone());
    }

    @Test
    public void refreshAllDataTagsShouldInformListener() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        listener.reset();

        final CompletableFuture<Long> f = listener.getTagUpdate().get(0);
        tagHandler.refreshAllDataTags();
        assertEquals(tagInSource1.getId(), f.get(100, TimeUnit.MILLISECONDS).longValue());
    }

    @Test
    public void refreshAllDataTagsShouldInformListenerOfAllIDs() throws OPCUAException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();

        tagHandler.refreshAllDataTags();
        final List<Long> ids = listener.getTagUpdate().stream().filter(CompletableFuture::isDone).map(CompletableFuture::join).collect(Collectors.toList());
        assertTrue(ids.containsAll(sourceTags.keySet()));
    }

    @Test
    public void readUnknownDataTagShouldReturnErrorMsg() {
        assertTrue(((DataTagHandler)tagHandler).readDataTag(-1).contains("Refresh is not possible"));
    }

    @Test
    public void readDataTagShouldReturnValue() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagInSource1);
        mocker.replay();
        assertTrue(((DataTagHandler)tagHandler).readDataTag(tagInSource1.getId()).contains("ValueUpdate(value="));
    }

    @Test
    public void exceptionShouldBeReported() throws ConfigurationException {
        mapper.getOrCreateDefinition(tagInSource1);
        endpoint.setThrowExceptions(true);
        System.out.println(((DataTagHandler)tagHandler).readDataTag(tagInSource1.getId()));
        assertTrue(((DataTagHandler)tagHandler).readDataTag(tagInSource1.getId()).contains("Refreshing Tag with ID " + tagInSource1.getId() +" failed"));
    }

    @Test
    public void readDataTagShouldReturnQuality() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagInSource1);
        mocker.replay();
        assertTrue(((DataTagHandler)tagHandler).readDataTag(tagInSource1.getId()).contains("SourceDataTagQuality(qualityCode="));
    }

    @Test
    public void interruptingRefreshShouldNotInformListener() throws OPCUAException, InterruptedException {
        endpoint.setDelay(500);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        tagHandler.subscribeTags(sourceTags.values());

        listener.reset();
        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> tagHandler.refreshAllDataTags());
        s.shutdownNow();
        s.awaitTermination(500, TimeUnit.SECONDS);

        assertFalse(listener.getTagUpdate().get(0).isDone());
    }

    @Test
    public void badThrowExceptionsShouldNotInformListener() throws OPCUAException {
        endpoint.setDelay(500);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        tagHandler.subscribeTags(sourceTags.values());

        listener.reset();
        endpoint.setThrowExceptions(true);

        tagHandler.refreshAllDataTags();
        assertFalse(listener.getTagUpdate().get(0).isDone());
    }

    @Test
    public void refreshTagWithBadHardwareAddressShouldDoNothing() {
        mocker.replay();
        tagHandler.refreshDataTag(badTag);
        mocker.verify();
    }

    @Test
    public void resetShouldResetMapper() {
        tagHandler.reset();
        assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }

    @Test
    public void stopShouldResetEndpoint() {
        proxy.stop();
        assertTrue(mapper.getTagIdDefinitionMap().isEmpty());
    }

    private void mockAndSubscribe(StatusCode code, ISourceDataTag tag) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(code, tag);
        mocker.replay();
        tagHandler.subscribeTag(tagInSource1);
    }
}
