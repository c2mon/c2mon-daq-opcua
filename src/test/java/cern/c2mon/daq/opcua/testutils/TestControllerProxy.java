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

    public TestControllerProxy(ApplicationContext appContext, AppConfigProperties config, MessageSender messageSender, Endpoint endpoint) {
        super(new ControllerFactory(config), config, endpoint);
    }

    public void setFailoverMode(RedundancySupport mode) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                controller = new ColdFailover(config);
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