package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.control.ContreteController;
import cern.c2mon.daq.opcua.control.NoFailover;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.ExceptionTestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestControllerProxy;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;
import static org.junit.jupiter.api.Assertions.*;

public class DataTagChangerTest extends TagHandlerTestBase {

    private DataTagChanger tagChanger;
    ChangeReport changeReport;
    ISourceDataTag tag;

    @BeforeEach
    public void setUpTagChanger() {
        tag = EdgeTagFactory.DipData.createDataTag();
        tagChanger = new DataTagChanger(controller);
        changeReport = new ChangeReport();
    }

    private void setup(Collection<ISourceDataTag> oldTags) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, oldTags);
        mocker.replay();
        controller.subscribeTags(oldTags);
    }

    @Test
    public void updateEqConfigShouldRemoveTagsNotInNewConfig() throws ConfigurationException {
        //setup
        final Collection<ISourceDataTag> oldTags = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> newTag = Collections.singletonList(EdgeTagFactory.StartStepUp.createDataTag());
        setup(oldTags);

        //action
        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);

        final Set<Long> actual = mapper.getTagIdDefinitionMap().keySet();
        final Collection<Long> expected = newTag.stream().map(ISourceDataTag::getId).collect(Collectors.toList());
        Assertions.assertTrue(expected.containsAll(actual));
        Assertions.assertTrue(actual.containsAll(expected));
    }

    @Test
    public void updateEqConfigShouldAddTagsNotInOldConfig() throws ConfigurationException {
        //setup
        final Collection<ISourceDataTag> newTag = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> oldTags = Collections.singletonList(EdgeTagFactory.StartStepUp.createDataTag());
        setup(oldTags);

        //action
        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);

        final Set<Long> actual = mapper.getTagIdDefinitionMap().keySet();
        final Collection<Long> expected = newTag.stream().map(ISourceDataTag::getId).collect(Collectors.toList());
        Assertions.assertTrue(expected.containsAll(actual));
        Assertions.assertTrue(actual.containsAll(expected));
    }

    @Test
    public void updateEqConfigShouldReportAddedTagsInChangeReport() throws ConfigurationException {
        //setup
        final Collection<ISourceDataTag> newTag = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> oldTags = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.AlternatingBoolean.createDataTag());
        setup(oldTags);

        //action
        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);

        changeReport.getInfoMessage().contains("Added Tags ");
    }

    @Test
    public void validOnAddDataTagShouldReportSuccess () {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void invalidOnAddDataTagShouldReportFail () throws OPCUAException {
        ContreteController controller = new NoFailover();
        controller.initialize(new ExceptionTestEndpoint(null, mapper), new String[0]);
        final TestControllerProxy proxy = TestUtils.getFailoverProxy(endpoint, listener);
        proxy.setController(controller);
        final DataTagChanger testTagChanger = new DataTagChanger(new DataTagHandler(mapper, listener, proxy));
        testTagChanger.onAddDataTag(tag, changeReport);
        assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void onAddDataTagShouldSubscribeTag() {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        Assertions.assertTrue(isSubscribed(tag));
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
        Assertions.assertFalse(isSubscribed(tagToRemove));
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
    public void removeDataTagShouldUnsubscribeDataTag() throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD,tag1);
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertFalse(isSubscribed(tag1));
    }

    @Test
    public void removeOneOfTwoTagsShouldUnsubscribeDataTag() throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1, tag2);
        tagChanger.onRemoveDataTag(tag1, changeReport);
        assertTrue(!isSubscribed(tag1) && isSubscribed(tag2));
    }
}
