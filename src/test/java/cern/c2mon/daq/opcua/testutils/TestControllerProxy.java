package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailoverDecorator;
import cern.c2mon.daq.opcua.failover.Controller;
import cern.c2mon.daq.opcua.failover.ControllerImpl;
import cern.c2mon.daq.opcua.failover.ControllerProxyImpl;
import lombok.Setter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component(value = "testFailoverProxy")
public class TestControllerProxy extends ControllerProxyImpl {
    @Setter
    private String[] redundantUris = new String[0];

    public TestControllerProxy(ApplicationContext appContext, AppConfigProperties config, MessageSender messageSender, Controller singleServerController) {
        super(appContext, config, messageSender, singleServerController);
    }

    public void setFailoverMode(RedundancySupport mode) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                currentController = new ColdFailoverDecorator(appContext, new RetryDelegate(config), (ControllerImpl) singleServerController);
                break;
            default:
                currentController = singleServerController;
        }
    }

    @Override
    public void connect(String uri) throws OPCUAException {
        if (currentController == null) {
            currentController = singleServerController;
        }
        singleServerController.connect(uri);
        currentController.initializeMonitoring(redundantUris);
    }
}
