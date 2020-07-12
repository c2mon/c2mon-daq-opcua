package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.AppConfigProperties;
import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.failover.ColdFailover;
import cern.c2mon.daq.opcua.failover.FailoverProxyImpl;
import cern.c2mon.daq.opcua.failover.NoFailover;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import lombok.Setter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component(value = "testFailoverProxy")
public class TestFailoverProxy extends FailoverProxyImpl {
    @Setter
    private String[] redundantUris = new String[0];

    public TestFailoverProxy(ApplicationContext appContext, AppConfigProperties config, MessageSender messageSender, NoFailover noFailover) {
        super(appContext, config, messageSender, noFailover);
    }

    public void setFailoverMode(RedundancySupport mode, TagSubscriptionMapper mapper, RetryDelegate delegate, ApplicationContext context) {
        switch (mode) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                failoverMode = new ColdFailover(mapper, messageSender, delegate, context);
                break;
            default:
                failoverMode = noFailover;
        }
    }

    @Override
    public void connect(String uri) throws OPCUAException {
        if (failoverMode == null) {
            failoverMode = noFailover;
        }
        noFailover.currentEndpoint().initialize(uri);
        failoverMode.initializeMonitoring(uri, noFailover.currentEndpoint(), redundantUris);
    }
}
