package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.DataTagAddress;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.address.impl.DIPHardwareAddressImpl;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class DataTagHandlerTest extends TagHandlerTestBase {
    SourceDataTag badTag;

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        final DIPHardwareAddressImpl dip = new DIPHardwareAddressImpl("badAddress");
        DataTagAddress tagAddress = new DataTagAddress(dip, 0, (short) 0, 0, 0, 2, true);
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
        tagHandler.subscribeTag(tagInSource1);
        final CompletableFuture<Long> tagInvalid = listener.getTagInvalid();
        assertEquals(tagInSource1.getId().longValue(), tagInvalid.get(100, TimeUnit.MILLISECONDS).longValue());
    }

    @Test
    public void subscribeTagInInconsistentStateShouldNotInformTag() throws ConfigurationException, InterruptedException, ExecutionException, TimeoutException {
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

        assertFalse(listener.getTagUpdate().isDone());
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
    public void refreshAllDataTagsShouldInformListener() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        mockAndSubscribe(StatusCode.GOOD, tagInSource1);
        listener.reset();

        tagHandler.refreshAllDataTags();
        assertEquals(tagInSource1.getId(), listener.getTagUpdate().get(100, TimeUnit.MILLISECONDS).longValue());
    }

    @Test
    public void interruptingRefreshShouldNotInformListener() throws OPCUAException, InterruptedException, ExecutionException, TimeoutException {
        endpoint.setDelay(500);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.values());
        mocker.replay();
        tagHandler.subscribeTags(sourceTags.values());

        listener.reset();
        ExecutorService s = Executors.newFixedThreadPool(2);
        s.submit(() -> tagHandler.refreshAllDataTags());
        s.shutdownNow();
        s.awaitTermination(500, TimeUnit.SECONDS);

        assertFalse(listener.getTagUpdate().isDone());
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
        assertFalse(listener.getTagUpdate().isDone());
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