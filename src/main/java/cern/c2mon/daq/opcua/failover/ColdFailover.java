package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component("coldFailover")
@NoArgsConstructor
public class ColdFailover implements FailoverMode {

    private Endpoint endpoint;

    @Override
    public void initialize(Endpoint endpoint, String[] redundantAddresses) {
        this.endpoint = endpoint;

    }

    @Override
    public Endpoint currentEndpoint() {
        return null;
    }
}
