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
package cern.c2mon.daq.opcua.retry;

import cern.c2mon.daq.opcua.SpringTestBase;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MiloEndpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

import static org.easymock.EasyMock.*;

@TestPropertySource(properties = {"c2mon.daq.opcua.retryDelay=1000", "c2mon.daq.opcua.maxRetryAttempts=3"}, locations = "classpath:application.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RetryEndpointActionIT extends SpringTestBase {

    Endpoint endpoint;

    @Value("${c2mon.daq.opcua.maxRetryAttempts}")
    int maxRetryAttempts;

    OpcUaClient client = createMock(OpcUaClient.class);


    @BeforeEach
    public void setupEquipmentScope() throws ConfigurationException {
        super.setupEquipmentScope();
        endpoint = ctx.getBean(Endpoint.class);
        ReflectionTestUtils.setField(endpoint, "client", client);
        ((MiloEndpoint)endpoint).onSessionActive(null);
    }

    @Test
    public void communicationExceptionShouldBeRepeatedNTimes() {
        //default exception thrown as CommunicationException
        verifyExceptionOnRead(new Exception(), maxRetryAttempts);
    }

    @Test
    public void configurationExceptionShouldNotBeRepeated() {
        //unknown host is thrown as configuration exception
        verifyExceptionOnRead(new UnknownHostException(), 1);
    }

    private void verifyExceptionOnRead(Exception e, int numTimes) {
        final CompletableFuture<DataValue> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        final NodeId nodeId = new NodeId(1, 2);
        expect(client.readValue(0, TimestampsToReturn.Both, nodeId))
                .andReturn(f)
                .times(numTimes);
        replay(client);
        try {
            endpoint.read(nodeId);
        } catch (Exception ex) {
            //expected
        }
        verify(client);
    }

}
