package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

public class DataTagChangerTest extends ControllerTestBase {

    private IDataTagChanger tagChanger;
    ChangeReport changeReport;
    ISourceDataTag tag;

    @BeforeEach
    public void castToDataTagChanger() throws OPCUAException, InterruptedException {
        tag = ServerTagFactory.DipData.createDataTag();
        controller.connect(uri);
        tagChanger = new TagChanger(controller);
        changeReport = new ChangeReport();
    }

    @AfterEach
    public void cleanUp() {
        ReflectionTestUtils.setField(controller, "failover", TestUtils.getFailoverProxy(endpoint));
    }

    @Test
    public void validOnAddDataTagShouldReportSuccess () {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void invalidOnAddDataTagShouldReportFail () {
        ReflectionTestUtils.setField(controller, "failover", TestUtils.getFailoverProxy(new ExceptionTestEndpoint()));
        tagChanger.onAddDataTag(tag, changeReport);
        assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void onAddDataTagShouldSubscribeTag() {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        Assertions.assertTrue(mapper.isSubscribed(tag));
    }

    @Test
    public void onRemoveSubscribedDataTagShouldReportSuccess() {
        final ISourceDataTag tagToRemove = sourceTags.get(2L);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToRemove);
        mocker.replay();
        tagChanger.onRemoveDataTag(tagToRemove,changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void onRemoveSubscribedDataTagShouldUnsubscribeTag() {
        final ISourceDataTag tagToRemove = sourceTags.get(2L);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToRemove);
        mocker.replay();
        tagChanger.onRemoveDataTag(tagToRemove,changeReport);
        Assertions.assertFalse(mapper.isSubscribed(tagToRemove));
    }

    @Test
    public void onUpdateUnknownTagShouldReportSuccess () {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, sourceTags.get(2L), tag);
        mocker.replay();
        tagChanger.onUpdateDataTag(sourceTags.get(2L), tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void validOnUpdateUnknownTagShouldReportSuccess () {
        final ISourceDataTag tagToUpdate = sourceTags.get(2L);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToUpdate);
        mocker.replay();
        tagChanger.onUpdateDataTag(tagToUpdate, tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void validOnUpdateUnknownTagShouldReportWhichTagWasUpdated () {
        final ISourceDataTag newTag = sourceTags.get(2L);
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, newTag);
        mocker.replay();
        tagChanger.onUpdateDataTag(newTag, tag, changeReport);
        final String infoMessage = changeReport.getInfoMessage();
        assertTrue(infoMessage.contains(newTag.getId().toString()));
        assertTrue(infoMessage.contains("subscribed"));

    }

    @Test
    public void onUpdateSameTagShouldReportSuccess () {
        tagChanger.onUpdateDataTag(tag, tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void onUpdateSameTagShouldReportNoUpdateRequired () {
        tagChanger.onUpdateDataTag(tag, tag, changeReport);
        final String infoMessage = changeReport.getInfoMessage();
        assertTrue(infoMessage.contains("no update was required"));
    }

    @Test
    public void removeUnknownDataTagShouldReportSuccess() {
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void removeUnknownDataTagShouldReportNoChangeRequired() {
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertTrue(changeReport.getInfoMessage().contains("No change required."));
    }

    @Test
    public void removeDataTagShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD,tag1);
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertFalse(mapper.isSubscribed(tag1));
    }

    @Test
    public void removeOneOfTwoTagsShouldUnsubscribeDataTag() {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertTrue(!mapper.isSubscribed(tag1) && mapper.isSubscribed(tag2));
    }
}
