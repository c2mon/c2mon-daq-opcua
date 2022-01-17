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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.util.SourceDataTagQualityCode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract base  which offers functionality that is shared across all failover modes and the {@link NoFailover}
 * {@link ConcreteController}.
 */
@Slf4j
public abstract class ControllerBase implements ConcreteController {

    private static Stream<Map.Entry<Integer, SourceDataTagQuality>> subscribeAndCatch (Endpoint e, Map.Entry<SubscriptionGroup, List<ItemDefinition>> groupWithDefinitions) {
        try {
            return e.subscribe(groupWithDefinitions.getKey(), groupWithDefinitions.getValue()).entrySet().stream();
        } catch (OPCUAException ex) {
            log.info("Could not subscribe the ItemDefinitions with time deadband {} to the endpoint at URI {}.", groupWithDefinitions.getKey().getPublishInterval(), e.getUri(), ex);
            return groupWithDefinitions.getValue().stream()
                    .map(d -> new AbstractMap.SimpleEntry<>(d.getClientHandle(), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)));
        }
    }

    /**
     * Fetch the endpoint to use for active operations such as writing to or reading from a {@link NodeId} or calling a
     * method on the server.
     * @return the active Endpoint
     */
    protected abstract Endpoint currentEndpoint ();

    /**
     * Called when creating or modifying subscriptions, or disconnecting to fetch all those endpoints on which action
     * shall be taken according to the failover mode. Sampling and publishing is set directly in the endpoint.
     * @return all endpoints on which subscriptions shall created or modified, or from which it is necessary to
     * disconnect. If the connected server is not within a redundant server set, an empty list is returned.
     */
    protected abstract List<Endpoint> passiveEndpoints ();

    @Override
    public void stop () {
        passiveEndpoints().forEach(Endpoint::disconnect);
        if (currentEndpoint() != null) {
            currentEndpoint().disconnect();
        }
    }

    @Override
    public Map<Integer, SourceDataTagQuality> subscribe (Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        passiveEndpoints().forEach(endpoint -> groupsWithDefinitions.entrySet().forEach(e -> subscribeAndCatch(endpoint, e)));
        return groupsWithDefinitions.entrySet().stream()
                .flatMap(e -> subscribeAndCatch(currentEndpoint(), e))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    @Override
    public boolean unsubscribe (ItemDefinition definition) {
        passiveEndpoints().forEach(e -> e.deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband()));
        return currentEndpoint().deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
    }

    @Override
    public Map.Entry<ValueUpdate, SourceDataTagQuality> read (NodeId nodeId) throws OPCUAException {
        return currentEndpoint().read(nodeId);
    }


    @Override
    public Map.Entry<Boolean, Object[]> callMethod (ItemDefinition definition, Object arg) throws OPCUAException {
        return currentEndpoint().callMethod(definition, arg);
    }

    @Override
    public boolean write (NodeId nodeId, Object value) throws OPCUAException {
        return currentEndpoint().write(nodeId, value);
    }
}
