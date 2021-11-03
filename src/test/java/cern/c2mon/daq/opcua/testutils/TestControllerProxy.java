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
package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.*;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.Setter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

public class TestControllerProxy extends ControllerProxy {
    @Setter
    private String[] redundantUris = new String[0];

    public void setController(ConcreteController controller) {
        this.controller = controller;
    }

    public TestControllerProxy(ApplicationContext appContext, AppConfigProperties configProperties, MessageSender messageSender, Endpoint endpoint) {
        super(new ControllerFactory(configProperties, null), configProperties, endpoint);
    }

    public void setFailoverMode(RedundancySupport mode) {
       controller = new ControllerFactory(config, null).getObject(mode);
    }

    @Override
    public void connect(Collection<String> serverAddresses) throws OPCUAException {
        if (controller == null) {
            controller = new NoFailover();
        }
        // ensure initialization is attempted for both
        if (endpoint instanceof TestEndpoint && ((TestEndpoint)endpoint).isThrowExceptions()) {
            controller.initialize(endpoint, redundantUris);
            endpoint.initialize(serverAddresses.iterator().next());
        } else {
            endpoint.initialize(serverAddresses.iterator().next());
            controller.initialize(endpoint, redundantUris);
        }
    }
}
