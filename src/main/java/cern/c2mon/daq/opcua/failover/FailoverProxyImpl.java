package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.NonTransparentRedundancyTypeNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@RequiredArgsConstructor
@Component(value = "failoverProxy")
public class FailoverProxyImpl implements FailoverProxy {

    private final NoFailover noFailover;
    @Lazy private final FailoverMode coldFailover;

    FailoverMode currentFailover;

    @Override
    public void initialize(String uri) throws OPCUAException {
        noFailover.initializeEndpoint(uri);
        log.info("Initialized endpoint");
        final Map.Entry<RedundancySupport, String[]> redundancyInfo = redundancySupport(noFailover.currentEndpoint());
        switch (redundancyInfo.getKey()) {
            case HotAndMirrored:
            case Hot:
            case Warm:
            case Cold:
                currentFailover = coldFailover;
                break;
            default:
                currentFailover = noFailover;
        }
        log.info("Redundancy support {}, redundancyInfo {}, current failover {}", redundancyInfo.getKey().name(), redundancyInfo.getValue(), currentFailover.getClass().getName());
        currentFailover.initialize(uri, noFailover.currentEndpoint(), redundancyInfo.getValue());
    }

    @Override
    public Endpoint getActiveEndpoint() {
        return currentFailover.currentEndpoint();
    }

    @Override
    public Collection<Endpoint> getPassiveEndpoints() {
        return currentFailover.passiveEndpoints();
    }

    private Map.Entry<RedundancySupport, String[]> redundancySupport(Endpoint endpoint) throws OPCUAException {
        RedundancySupport mode = RedundancySupport.None;
        String[] redundantUriArray = new String[0];
        try {
            final var redundancyNode = endpoint.getServerRedundancyNode();
            mode = redundancyNode.getRedundancySupport()
                    .thenApply(m -> (m == null) ? RedundancySupport.None : m)
                    .join();
            if (mode != RedundancySupport.None
                    && mode != RedundancySupport.Transparent
                    && redundancyNode instanceof NonTransparentRedundancyTypeNode) {
                redundantUriArray = ((NonTransparentRedundancyTypeNode) redundancyNode).getServerUriArray().join();
            }
        } catch (CompletionException e) {
            log.error("An exception occurred when reading the server's redundancy information. Proceeding without setting up redundancy.", e);
        }
        return Map.entry(mode, redundantUriArray);
    }
}
