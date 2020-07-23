package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.IMessageSender;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerProxy;
import cern.c2mon.daq.opcua.control.NoFailover;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.Setter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component(value = "testFailoverProxy")
public class TestControllerProxy extends ControllerProxy {
    @Setter
    private String[] redundantUris = new String[0];

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public TestControllerProxy(ApplicationContext appContext, AppConfigProperties config, IMessageSender messageSender, Endpoint endpoint) {
        super(appContext, config, endpoint);
    }

    public void setFailoverMode(RedundancySupport mode) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                controller = new ColdFailover(config.exponentialDelayTemplate(), config);
                break;
            default:
                controller = new NoFailover();
        }
    }

    @Override
    public void connect(Collection<String> serverAddresses) throws OPCUAException {
        if (controller == null) {
            controller = new NoFailover();
        }
        endpoint.initialize(serverAddresses.iterator().next());
        controller.initialize(endpoint, redundantUris);
    }
}
