package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Lazy
@Component("noFailover")
@NoArgsConstructor
@Slf4j
public class NoFailover implements FailoverMode {

    private Map.Entry<String, Endpoint> server;

    @Override
    public void initialize(Map.Entry<String, Endpoint> server, String[] redundantAddresses, Collection<SessionActivityListener> listeners) {
        this.server = server;
    }

    @Override
    public void disconnect() throws OPCUAException {
        if (currentEndpoint() != null) {
            currentEndpoint().disconnect();
        }
    }

    @Override
    public Endpoint currentEndpoint() {
        return (server == null) ? null : server.getValue();
    }

    @Override
    public void triggerServerSwitch() {
        log.info("No server to fall back to.");
    }
}
