package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * A {@link Controller} maintaining connection to a non-redundant server, or servers in transparent redundancy.
 */
@Slf4j
@NoArgsConstructor
@Component("singleServerController")
public class NoFailover extends ControllerBase {

    /**
     * The {@link Endpoint} connected to the server
     */
    private Endpoint activeEndpoint;

    /**
     * Initialize supervision and connection monitoring to the active server.
     * @param endpoint           the already connected {@link Endpoint}
     * @param redundantAddresses usually empty
     */
    @Override
    public void initialize(Endpoint endpoint, String... redundantAddresses) {
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
