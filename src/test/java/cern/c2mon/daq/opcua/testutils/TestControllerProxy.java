package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.tagHandling.MessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.control.ColdFailover;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.control.ControllerProxyImpl;
import cern.c2mon.daq.opcua.control.NoFailover;
import lombok.Setter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component(value = "testFailoverProxy")
public class TestControllerProxy extends ControllerProxyImpl {
    @Setter
    private String[] redundantUris = new String[0];

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public TestControllerProxy(ApplicationContext appContext, AppConfigProperties config, MessageSender messageSender, Endpoint endpoint) {
        super(appContext, config, endpoint);
    }

    public void setFailoverMode(RedundancySupport mode) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                controller = new ColdFailover(appContext, new RetryDelegate(config));
                break;
            default:
                controller = new NoFailover();
        }
    }

    @Override
    public void connect(String uri) throws OPCUAException {
        if (controller == null) {
            controller = new NoFailover();
        }
        endpoint.initialize(uri);
        controller.initialize(endpoint, redundantUris);
    }
}
