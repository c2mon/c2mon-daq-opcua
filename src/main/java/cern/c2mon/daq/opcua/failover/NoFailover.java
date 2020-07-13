package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@NoArgsConstructor
@Component("singleServerController")
public class NoFailover extends ControllerBase {

    protected Endpoint activeEndpoint;

    @Override
    public void initialize(Endpoint endpoint, String[] redundantAddresses) {
        activeEndpoint = endpoint;
        activeEndpoint.manageSessionActivityListener(true, null);
    }

    @Override
    protected Endpoint currentEndpoint() {
        return activeEndpoint;
    }

    @Override
    protected List<Endpoint> passiveEndpoints() {
        return Collections.emptyList();
    }
}
