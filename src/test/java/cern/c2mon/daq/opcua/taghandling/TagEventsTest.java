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
package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TagEventsTest extends TagHandlerTestBase {

    CompletableFuture<Long> tagUpdate;
    CompletableFuture<Long> tagInvalid;

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {
        super.setUp();
        proxy.connect(Collections.singleton(uri));
        tagUpdate = listener.getTagUpdate().get(0);
        tagInvalid = listener.getTagInvalid().get(0);
    }

    @Test
    public void subscribeWithInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.BAD, tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void subscribeWithValidTagShouldNotInformSender () throws ConfigurationException {
        subscribeTagsAndMockStatusCode(StatusCode.GOOD, tag1);
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void refreshInvalidTagShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        endpoint.setReturnGoodStatusCodes(false);
        tagHandler.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void refreshValidShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException {
        tagHandler.refreshDataTag(tag1);
        assertEquals(tag1.getId(), tagUpdate.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void validSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.GOOD, tag1);
        tagHandler.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagUpdate.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagInvalid.isDone());
    }

    @Test
    public void invalidSubscriptionEventShouldInformSender () throws ExecutionException, InterruptedException, TimeoutException, ConfigurationException {
        mocker.mockValueConsumerAndSubscribeTagAndReplay(StatusCode.BAD, tag1);
        tagHandler.subscribeTags(Collections.singletonList(tag1));
        assertEquals(tag1.getId(), tagInvalid.get(TestUtils.TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(tagUpdate.isDone());
    }
}
