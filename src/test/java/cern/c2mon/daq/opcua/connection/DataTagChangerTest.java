package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.testutils.MiloExceptionTestClientWrapper;
import cern.c2mon.daq.opcua.testutils.ServerTagFactory;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

public class DataTagChangerTest extends ControllerTestBase {

    private IDataTagChanger tagChanger;
    ChangeReport changeReport;
    ISourceDataTag tag;

    @BeforeEach
    public void castToDataTagChanger() throws ConfigurationException {
        tag = ServerTagFactory.DipData.createDataTag();
        mocker.mockClientHandle(Collections.singletonList(tag));
        mocker.replay();
        controller.initialize(config);

        assert super.controller instanceof IDataTagChanger;
        tagChanger = (IDataTagChanger) super.controller;
        changeReport = new ChangeReport();
    }

    @Test
    public void validOnAddDataTagShouldReportSuccess () {
        tagChanger.onAddDataTag(tag, changeReport);
        Assertions.assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void invalidOnAddDataTagShouldReportFail () {
        ((Controller) tagChanger).getEndpoint().setWrapper(new MiloExceptionTestClientWrapper());
        tagChanger.onAddDataTag(tag, changeReport);
        Assertions.assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void onAddDataTagShouldSubscribeTag() {
        tagChanger.onAddDataTag(tag, changeReport);
        Assertions.assertTrue(mapper.isSubscribed(tag));
    }

    @Test
    public void onRemoveSubscribedDataTagShouldReportSuccess() {
        tagChanger.onRemoveDataTag(sourceTags.get(2L),changeReport);
        Assertions.assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void onRemoveSubscribedDataTagShouldUnsubscribeTag() {
        ISourceDataTag tagToRemove = sourceTags.get(2L);
        tagChanger.onRemoveDataTag(tagToRemove,changeReport);
        Assertions.assertFalse(mapper.isSubscribed(tagToRemove));
    }

    @Test
    public void onRemoveUnknownDataTagShouldReportFail() {
        tagChanger.onRemoveDataTag(tag,changeReport);
        Assertions.assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void onUpdateUnknownTagShouldReportFail () {
        tagChanger.onUpdateDataTag(sourceTags.get(2L), tag, changeReport);
        Assertions.assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void validOnUpdateUnknownTagShouldReportSuccess () {
        tagChanger.onUpdateDataTag(tag, sourceTags.get(2L), changeReport);
        Assertions.assertEquals(SUCCESS, changeReport.getState());
        Assert.assertEquals(ReportMessages.TAG_UPDATED.message, changeReport.getInfoMessage());
    }

    @Test
    public void onUpdateSameTagShouldReportNoUpdateRequired () {
        tagChanger.onUpdateDataTag(tag, tag, changeReport);
        Assertions.assertEquals(SUCCESS, changeReport.getState());
        Assertions.assertEquals(ReportMessages.NO_UPDATE_REQUIRED.message, changeReport.getInfoMessage());
    }

}
