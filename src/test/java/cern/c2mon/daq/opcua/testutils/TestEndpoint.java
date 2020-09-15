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
package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloMapper;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionReader;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerRedundancyTypeNode;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.TransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;

@Getter
@Setter
@Slf4j
public class TestEndpoint implements Endpoint {
    private MessageSender messageSender;
    private TagSubscriptionReader mapper;
    UaMonitoredItem monitoredItem = createNiceMock(UaMonitoredItem.class);
    UaSubscription subscription = createNiceMock(UaSubscription.class);
    NonTransparentRedundancyTypeNode nonTransparentRedundancyTypeNode = createMock(NonTransparentRedundancyTypeNode.class);
    TransparentRedundancyTypeNode transparentRedundancyTypeNode = createMock(TransparentRedundancyTypeNode.class);
    private boolean returnGoodStatusCodes = true;
    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    String uri;
    boolean throwExceptions = false;
    OPCUAException toThrow;
    long delay = 0;
    boolean transparent = false;
    Object readValue = 0;
    CountDownLatch initLatch = new CountDownLatch(1);
    CountDownLatch readLatch = new CountDownLatch(2);


    public TestEndpoint(MessageSender sender, TagSubscriptionReader mapper) {
        this.messageSender = sender;
        this.mapper = mapper;
    }

    @Override
    public void initialize(String uri) throws OPCUAException {
        this.uri = uri;
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (initLatch) {
            if (initLatch != null) {
                initLatch.countDown();
            }
            if (throwExceptions) {
                throw toThrow == null ? new CommunicationException(CONNECT) : toThrow;
            }
        }
    }

    @Override
    public void manageSessionActivityListener(boolean add, SessionActivityListener listener) {

    }

    @Override
    public void setUpdateEquipmentStateOnSessionChanges(boolean active) {

    }

    @Override
    public void disconnect () {}

    @Override
    public Map<Integer, SourceDataTagQuality> subscribe(SubscriptionGroup group, Collection<ItemDefinition> definitions) throws OPCUAException {
        Consumer<UaMonitoredItem> itemCreationCallback = item -> item.setValueConsumer(value -> {
            SourceDataTagQuality tagQuality = MiloMapper.getDataTagQuality((value.getStatusCode() == null) ? StatusCode.BAD : value.getStatusCode());
            final Object updateValue = MiloMapper.toObject(value.getValue());
            ValueUpdate valueUpdate = (value.getSourceTime() == null) ?
                    new ValueUpdate(updateValue) :
                    new ValueUpdate(updateValue, value.getSourceTime().getJavaTime());
            final Long tagId = mapper.getTagId(item.getClientHandle().intValue());
            messageSender.onValueUpdate(tagId, tagQuality, valueUpdate);
        });
        return subscribeWithCallback(group.getPublishInterval(), definitions,  itemCreationCallback);
    }

    @Override
    public Map<Integer, SourceDataTagQuality> subscribeWithCallback(int publishingInterval, Collection<ItemDefinition> definitions, Consumer<UaMonitoredItem> itemCreationCallback) throws OPCUAException {
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (throwExceptions) {
            throw toThrow == null ? new CommunicationException(CREATE_SUBSCRIPTION) : toThrow;
        }
        for (int i = 1; i <= definitions.size(); i++) {
            executor.schedule(() -> itemCreationCallback.accept(monitoredItem), i * (delay == 0 ? 100 : delay), TimeUnit.MILLISECONDS);
        }
        return definitions.stream().collect(Collectors.toMap(ItemDefinition::getClientHandle, c -> {
            final StatusCode statusCode = monitoredItem.getStatusCode();
            return MiloMapper.getDataTagQuality(statusCode == null ? StatusCode.BAD : statusCode);
        }));
    }

    @Override
    public boolean deleteItemFromSubscription(int clientHandle, int publishInterval) {
        return returnGoodStatusCodes;
    }

    @Override
    public void recreateAllSubscriptions() {

    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read(NodeId nodeId) throws OPCUAException {
        final Object r = readValue;
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (readLatch) {
            if (readLatch != null) {
                readLatch.countDown();
            }
            if (throwExceptions) {
                throw toThrow == null ? new CommunicationException(READ) : toThrow;
            }
            final SourceDataTagQuality quality = new SourceDataTagQuality(returnGoodStatusCodes ? SourceDataTagQualityCode.OK : SourceDataTagQualityCode.VALUE_CORRUPTED);
            return new AbstractMap.SimpleEntry<>(new ValueUpdate(r), quality);
        }
    }

    @Override
    public boolean write(NodeId nodeId, Object value) throws OPCUAException {
        if (throwExceptions) {
            throw toThrow == null ? new CommunicationException(WRITE) : toThrow;
        }
        return returnGoodStatusCodes;
    }

    @Override
    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) {
        return new AbstractMap.SimpleEntry<>(returnGoodStatusCodes, new Object[] {arg});
    }

    @Override
    public ServerRedundancyTypeNode getServerRedundancyNode() {
        if (transparent) {
            return  transparentRedundancyTypeNode;
        } else {
            return nonTransparentRedundancyTypeNode;
        }
    }

}

