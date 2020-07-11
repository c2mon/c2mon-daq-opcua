package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Lazy
@Component("noFailover")
@RequiredArgsConstructor
@Slf4j
public class NoFailover implements FailoverMode, UaSubscriptionManager.SubscriptionListener {

    private final Endpoint endpoint;

    @Override
    public void initializeMonitoring(String uri, Endpoint endpoint, String[] redundantAddresses) {
        this.endpoint.monitorEquipmentState(true, null);
    }

    @Override
    public void disconnect() throws OPCUAException {
        endpoint.disconnect();
    }

    @Override
    public Endpoint currentEndpoint() {
        return endpoint;
    }

    @Override
    public List<Endpoint> passiveEndpoints() {
        return Collections.emptyList();
    }
}
