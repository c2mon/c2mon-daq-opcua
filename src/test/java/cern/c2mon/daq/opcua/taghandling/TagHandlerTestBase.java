/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2021 CERN
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
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.metrics.MetricProxy;
import cern.c2mon.daq.opcua.testutils.*;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.util.ValueDeadbandType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TagHandlerTestBase {
    protected static String ADDRESS_PROTOCOL_TCP = "URI=opc.tcp://";
    protected static String ADDRESS_BASE = "test;serverRetryTimeout=123;serverTimeout=12;aliveWriter=";

    protected ISourceDataTag tag1 = EdgeTagFactory.RandomUnsignedInt32.createDataTag();
    protected ISourceDataTag tag2 = EdgeTagFactory.DipData.createDataTag();
    protected ISourceDataTag tagWithDeadband = EdgeTagFactory.AlternatingBoolean.createDataTag(5f, ValueDeadbandType.EQUIPMENT_ABSOLUTE, 800);




    protected Map<Long, ISourceDataTag> sourceTags;
    protected String uri;

    TagSubscriptionMapper mapper;
    TestEndpoint endpoint;
    TestListeners.TestListener listener;
    IDataTagHandler tagHandler;
    MiloMocker mocker;
    TestControllerProxy proxy;

    ISourceDataTag tagInSource1 = EdgeTagFactory.RandomUnsignedInt32.createDataTagWithID(10L);
    ISourceDataTag tagInSource2 = EdgeTagFactory.AlternatingBoolean.createDataTagWithID(20L);

    @BeforeEach
    public void setUp() throws OPCUAException, InterruptedException {

        uri = ADDRESS_PROTOCOL_TCP + ADDRESS_BASE + true;
        sourceTags = new ConcurrentHashMap<>();
        sourceTags.put(10L, tagInSource1);
        sourceTags.put(20L, tagInSource2);

        mapper = new TagSubscriptionMapper(new MetricProxy(new SimpleMeterRegistry()));
        listener = new TestListeners.TestListener();
        endpoint = new TestEndpoint(listener, mapper);
        proxy = TestUtils.getFailoverProxy(endpoint, listener);
        tagHandler = new DataTagHandler(mapper, listener, proxy);
        mocker = new MiloMocker(endpoint, mapper);
    }

    @AfterEach
    public void teardown() {
        tagHandler = null;
    }

    protected void subscribeTagsAndMockStatusCode (StatusCode code, ISourceDataTag... tags) throws ConfigurationException {
        mocker.mockStatusCodeAndClientHandle(code, tags);
        mocker.replay();
        tagHandler.subscribeTags(Arrays.asList(tags));
    }

    protected boolean isSubscribed(ISourceDataTag tag) {
        final SubscriptionGroup group = mapper.getGroup(tag.getTimeDeadband());
        return group != null && group.contains(tag.getId());
    }
}
