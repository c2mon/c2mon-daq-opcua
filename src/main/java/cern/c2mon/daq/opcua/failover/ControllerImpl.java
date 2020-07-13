package cern.c2mon.daq.opcua.failover;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.connection.MessageSender;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@AllArgsConstructor
@Component("singleServerController")
public class ControllerImpl implements Controller {


    /** A flag indicating whether the controller was stopped. */
    protected final AtomicBoolean stopped = new AtomicBoolean(true);

    protected final MessageSender messageSender;
    protected Endpoint activeEndpoint;

    public void connect(String uri) throws OPCUAException {
        stopped.set(false);
        currentEndpoint().initialize(uri);
    }

    public void initializeMonitoring(String[] redundantAddresses) {
        currentEndpoint().manageSessionActivityListener(true, null);
    }

    @Override
    public Endpoint currentEndpoint() {
        return activeEndpoint;
    }


    @Override
    public void stop() {
        log.info("Disconnecting... ");
        stopped.set(true);
        try {
            currentEndpoint().disconnect();
        } catch (OPCUAException ex) {
            log.error("Error disconnecting from endpoint with uri {}: ", currentEndpoint().getUri(), ex);
        }
    }

    @Override
    public Map<UInteger, SourceDataTagQuality> subscribe(Map<SubscriptionGroup, List<ItemDefinition>> groupsWithDefinitions) {
        return groupsWithDefinitions.entrySet().stream()
                .flatMap(e -> subscribeOnEndpoint(currentEndpoint(), e))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    @Override
    public boolean unsubscribe(ItemDefinition definition) {
        return currentEndpoint().deleteItemFromSubscription(definition.getClientHandle(), definition.getTimeDeadband());
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public static Stream<Entry<UInteger, SourceDataTagQuality>> subscribeOnEndpoint(Endpoint e, Map.Entry<SubscriptionGroup, List<ItemDefinition>> groupWithDefinitions) {
        try {
            return e.subscribe(groupWithDefinitions.getKey(), groupWithDefinitions.getValue()).entrySet().stream();
        } catch (ConfigurationException configurationException) {
            return groupWithDefinitions.getValue()
                    .stream()
                    .map(d -> Map.entry(d.getClientHandle(), new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)));
        }
    }
}
