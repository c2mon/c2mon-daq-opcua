package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.model.types.objects.NonTransparentRedundancyType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@RequiredArgsConstructor
@Component("failoverProxy")
public class FailoverProxyImpl implements FailoverProxy {

    private final FailoverMode noFailover;
    private final FailoverMode coldFailover;
    private final Endpoint endpoint;

    private FailoverMode currentFailover;

    public void initialize(String uri) throws OPCUAException, InterruptedException {
        currentFailover = noFailover;
        endpoint.initialize(uri);
        final Map.Entry<RedundancySupport, String[]> redundancyInfo = redundancySupport();
        switch (redundancyInfo.getKey()) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                currentFailover = coldFailover;
                break;
        }
        currentFailover.initialize(endpoint, redundancyInfo.getValue());
    }

    public Endpoint getEndpoint() {
        return currentFailover.currentEndpoint();
    }

    private Map.Entry<RedundancySupport, String[]> redundancySupport() throws OPCUAException {
        RedundancySupport mode = RedundancySupport.None;
        String[] redundantUriArray = new String[0];
        try {
            final var redundancyNode = endpoint.getServerRedundancyNode();
            mode = redundancyNode.getRedundancySupport()
                    .thenApply(m -> (m == null) ? RedundancySupport.None : m)
                    .join();
            if (mode != RedundancySupport.None
                    && mode != RedundancySupport.Transparent
                    && redundancyNode instanceof NonTransparentRedundancyType) {
                redundantUriArray = ((NonTransparentRedundancyType) redundancyNode).getServerUriArray().join();
            }
        } catch (CompletionException e) {
            log.error("An exception occurred when reading the server's redundancy information. Proceeding without setting up redundancy.", e);
        }
        return Map.entry(mode, redundantUriArray);
    }
}
