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
package cern.c2mon.daq.opcua.opcuamessagehandler;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.OPCUAMessageHandler;
import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionManager;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.scope.EquipmentScope;
import cern.c2mon.daq.opcua.taghandling.*;
import cern.c2mon.daq.opcua.testutils.TestEndpoint;
import cern.c2mon.daq.opcua.testutils.TestUtils;
import cern.c2mon.daq.test.GenericMessageHandlerTest;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

import static org.easymock.EasyMock.*;

public abstract class OPCUAMessageHandlerTestBase extends GenericMessageHandlerTest {

    protected OPCUAMessageHandler handler;
    protected Controller testController;
    protected ApplicationContext context;
    protected TagSubscriptionManager mapper;
    protected TestEndpoint testEndpoint;
    protected AppConfigProperties appConfigProperties;
    protected IDataTagHandler dataTagHandler;
    protected CommandTagHandler commandTagHandler;
    protected DataTagChanger dataTagChanger;
    protected AliveWriter writer;


    protected void beforeTest(MessageSender sender) {
        context = createMock(ApplicationContext.class);
        mapper = new TagSubscriptionMapper();
        testEndpoint = new TestEndpoint(sender, mapper);
        appConfigProperties = TestUtils.createDefaultConfig();
        testController = TestUtils.getFailoverProxy(testEndpoint, sender);
        dataTagHandler = new DataTagHandler(mapper, sender, testController);
        commandTagHandler = new CommandTagHandler(testController);
        dataTagChanger = new DataTagChanger(dataTagHandler);
        writer = new AliveWriter(testController, sender);

        handler = (OPCUAMessageHandler) msgHandler;
        handler.setContext(context);
    }


    protected void configureContext(MessageSender sender) {
        expect(context.getAutowireCapableBeanFactory()).andReturn(new DefaultListableBeanFactory()).anyTimes();
        expect(context.getBean(AppConfig.class)).andReturn(new AppConfig()).anyTimes();
        expect(context.getBean(EquipmentScope.class)).andReturn(new EquipmentScope()).anyTimes();
        expect(context.getBean(Controller.class)).andReturn(testController).anyTimes();
        expect(context.getBean(MessageSender.class)).andReturn(sender).anyTimes();
        expect(context.getBean(IDataTagHandler.class)).andReturn(dataTagHandler).anyTimes();
        expect(context.getBean(CommandTagHandler.class)).andReturn(commandTagHandler).anyTimes();
        expect(context.getBean(AppConfigProperties.class)).andReturn(appConfigProperties).anyTimes();
        expect(context.getBean(DataTagChanger.class)).andReturn(dataTagChanger).anyTimes();
        expect(context.getBean(AliveWriter.class)).andReturn(writer).anyTimes();
        replay(context);
    }

}
