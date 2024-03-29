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
package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import static org.easymock.EasyMock.*;

public class MiloMocker {

    TestEndpoint endpoint;
    TagSubscriptionReader mapper;

    public MiloMocker(Endpoint endpoint, TagSubscriptionReader mapper) {
        assert endpoint instanceof TestEndpoint;
        this.endpoint = (TestEndpoint) endpoint;
        this.mapper = mapper;
    }

    public void replay (){
        EasyMock.replay(endpoint.getMonitoredItem());
    }

    public void verify (){
        EasyMock.verify(endpoint.getMonitoredItem());
    }

    public void mockStatusCodeAndClientHandle(StatusCode code, Collection<ISourceDataTag> tags) throws ConfigurationException {
        UaMonitoredItem monitoredItem = endpoint.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(code).anyTimes();
        mockClientHandle(monitoredItem, tags);
    }

    public void mockStatusCodeAndClientHandle(StatusCode code, ISourceDataTag... tags) throws ConfigurationException {
        mockStatusCodeAndClientHandle(code, Arrays.asList(tags));
    }

    public void mockGoodAndBadStatusCodesAndReplay(ISourceDataTag[] goodTags, ISourceDataTag[] badTags) throws ConfigurationException {
        UaMonitoredItem monitoredItem = endpoint.getMonitoredItem();
        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).times(goodTags.length);
        mockClientHandle(monitoredItem, Arrays.asList(goodTags));

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.BAD).times(badTags.length);
        mockClientHandle(monitoredItem, Arrays.asList(badTags));
        replay();
    }

    public void mockValueConsumerAndSubscribeTagAndReplay(StatusCode statusCode, ISourceDataTag tag) throws ConfigurationException {
        UaMonitoredItem monitoredItem = endpoint.getMonitoredItem();
        int clientHandle = mapper.getOrCreateDefinition(tag).getClientHandle();
        expect(monitoredItem.getClientHandle()).andReturn(UInteger.valueOf(clientHandle)).anyTimes();

        expect(monitoredItem.getStatusCode()).andReturn(StatusCode.GOOD).once(); // subscription callback
        expect(monitoredItem.getStatusCode()).andReturn(statusCode).once(); // item creation callback

        Capture<Consumer<DataValue>> capturedConsumer = newCapture();
        monitoredItem.setValueConsumer(capture(capturedConsumer));
        expectLastCall().andAnswer(() -> {
            Consumer<DataValue> consumer = capturedConsumer.getValue();
            consumer.accept(new DataValue(new Variant(1), statusCode));
            return consumer;
        });
        replay();
    }

    protected void mockClientHandle (UaMonitoredItem monitoredItem, Collection<ISourceDataTag> tags) {
        if(tags.size() == 0) return;
        for (ISourceDataTag tag : tags) {
            try {
                int clientHandle = mapper.getOrCreateDefinition(tag).getClientHandle();
                expect(monitoredItem.getClientHandle()).andReturn(UInteger.valueOf(clientHandle)).once();
            } catch (ConfigurationException ignored) {

            }
        }
    }
}
