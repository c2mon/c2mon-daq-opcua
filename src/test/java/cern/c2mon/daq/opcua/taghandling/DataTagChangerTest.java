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
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.daq.config.ChangeReport;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
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
        tagChanger = new DataTagChanger(tagHandler);
        changeReport = new ChangeReport();
    }

    private void setup(StatusCode code, Collection<ISourceDataTag> oldTags) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(code, oldTags);
        mocker.replay();
        tagHandler.subscribeTags(oldTags);
    }

    @Test
    public void updateEqConfigShouldRemoveTagsNotInNewConfig() throws ConfigurationException {
        final Collection<ISourceDataTag> oldTags = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> newTag = Collections.singletonList(EdgeTagFactory.StartStepUp.createDataTag());
        setup(StatusCode.GOOD, oldTags);

        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);

        final Set<Long> actual = mapper.getTagIdDefinitionMap().keySet();
        final Collection<Long> expected = newTag.stream().map(ISourceDataTag::getId).collect(Collectors.toList());
        assertTrue(expected.containsAll(actual));
        assertTrue(actual.containsAll(expected));
    }

    @Test
    public void updateEqConfigShouldAddTagsNotInOldConfig() throws ConfigurationException {
        final Collection<ISourceDataTag> newTag = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> oldTags = Collections.singletonList(EdgeTagFactory.StartStepUp.createDataTag());
        setup(StatusCode.GOOD, oldTags);

        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);

        final Set<Long> actual = mapper.getTagIdDefinitionMap().keySet();
        final Collection<Long> expected = newTag.stream().map(ISourceDataTag::getId).collect(Collectors.toList());
        assertTrue(expected.containsAll(actual));
        assertTrue(actual.containsAll(expected));
    }

    @Test
    public void updateEqConfigShouldReportAddedTagsInChangeReport() throws ConfigurationException {
        final Collection<ISourceDataTag> newTag = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> oldTags = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.AlternatingBoolean.createDataTag());
        setup(StatusCode.GOOD, oldTags);

        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);
        assertTrue(changeReport.getInfoMessage().contains("Subscribed to Tags "));
    }

    @Test
    public void updateEqConfigShouldReportFailedTags() throws ConfigurationException {
        final Collection<ISourceDataTag> newTag = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.PositiveTrendData.createDataTag(), tag);
        final Collection<ISourceDataTag> oldTags = Arrays.asList(EdgeTagFactory.StartStepUp.createDataTag(), EdgeTagFactory.AlternatingBoolean.createDataTag());
        setup(StatusCode.BAD, oldTags);

        tagChanger.onUpdateEquipmentConfiguration(newTag, oldTags, changeReport);
        assertTrue(changeReport.getInfoMessage().contains("Could not remove "));
    }

    @Test
    public void validOnAddDataTagShouldReportSuccess () throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void invalidOnAddDataTagShouldReportFail () {
        endpoint.setThrowExceptions(true);
        tagChanger.onAddDataTag(tag, changeReport);
        assertEquals(FAIL, changeReport.getState());
    }

    @Test
    public void onAddDataTagShouldSubscribeTag() throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tag);
        mocker.replay();
        tagChanger.onAddDataTag(tag, changeReport);
        assertTrue(isSubscribed(tag));
    }

    @Test
    public void onRemoveSubscribedDataTagShouldReportSuccess() throws ConfigurationException {
        final ISourceDataTag tagToRemove = tagInSource1;
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToRemove);
        mocker.replay();
        tagChanger.onRemoveDataTag(tagToRemove,changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void onRemoveSubscribedDataTagShouldUnsubscribeTag() throws ConfigurationException {
        final ISourceDataTag tagToRemove = tagInSource1;
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToRemove);
        mocker.replay();
        tagChanger.onRemoveDataTag(tagToRemove,changeReport);
        assertFalse(isSubscribed(tagToRemove));
    }

    @Test
    public void onUpdateUnknownTagShouldReportSuccess () throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagInSource1, tag);
        mocker.replay();
        tagChanger.onUpdateDataTag(tagInSource1, tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }


    @Test
    public void validOnUpdateUnknownTagShouldReportSuccess () throws ConfigurationException {
        final ISourceDataTag tagToUpdate = tagInSource1;
        mocker.mockStatusCodeAndClientHandle(StatusCode.GOOD, tagToUpdate);
        mocker.replay();
        tagChanger.onUpdateDataTag(tagToUpdate, tag, changeReport);
        assertEquals(SUCCESS, changeReport.getState());
    }

    @Test
    public void validOnUpdateUnknownTagShouldReportWhichTagWasUpdated () throws ConfigurationException {
        final ISourceDataTag newTag = tagInSource1;
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
