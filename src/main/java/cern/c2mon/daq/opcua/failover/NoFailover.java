package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.RetryDelegate;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Lazy
@Component("noFailover")
@Slf4j
public class NoFailover extends ControllerBase implements UaSubscriptionManager.SubscriptionListener {

    private final Endpoint endpoint;

    public NoFailover(TagSubscriptionMapper mapper, MessageSender messageSender, RetryDelegate retryDelegate, ApplicationContext applicationContext, Endpoint endpoint) {
        super(mapper, messageSender, retryDelegate, applicationContext);
        this.endpoint = endpoint;
    }

    @Override
    public void initializeMonitoring(String uri, Endpoint endpoint, String[] redundantAddresses) throws OPCUAException {
        super.initializeMonitoring(uri, endpoint, redundantAddresses);
        this.endpoint.monitorEquipmentState(true, null);
    }

    @Override
    public Endpoint currentEndpoint() {
        return endpoint;
    }

    @Override
    public List<Endpoint> passiveEndpoints() {
        return Collections.emptyList();
    }

    @Override
    public void switchServers() throws OPCUAException {
        log.error("Switch servers called on non-redundant servers. Proceeding with current server...");
    }
}
