package cern.c2mon.daq.opcua.connection.endpoint;

import cern.c2mon.daq.opcua.connection.Deadband;
import cern.c2mon.daq.opcua.connection.EndpointImpl;
import cern.c2mon.daq.opcua.mapping.GroupDefinitionPair;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import it.ServerTagProvider;
import org.easymock.EasyMock;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.serialization.EncodingLimits;
import org.eclipse.milo.opcua.stack.core.serialization.SerializationContext;
import org.eclipse.milo.opcua.stack.core.types.DataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;

public abstract class EndpointSubscriptionBase {
    protected UaSubscription subscription = createMock(UaSubscription.class);
    protected OpcUaClient client = createMock(OpcUaClient.class);
    protected OpcUaSubscriptionManager subscriptionManager = createMock(OpcUaSubscriptionManager.class);

    protected TagSubscriptionMapper tagSubscriptionMapper;
    protected EndpointImpl endpoint;

    @BeforeEach
    public void setUp() throws SecurityException {
        tagSubscriptionMapper = new TagSubscriptionMapperImpl();
        endpoint = new EndpointImpl(tagSubscriptionMapper, client);
    }

    @AfterEach
    public void tearDown() {
        tagSubscriptionMapper.clear();
    }


    protected List<ISourceDataTag> createAndMockTagsInKnownGroups(int nrOfGroups, ServerTagProvider... serverTags) {
        mockCreateSubscriptionRequest(nrOfGroups, serverTags.length);
        BiFunction<Integer, ServerTagProvider, ISourceDataTag> createTagMethod =
                (i, serverTag) -> createTagWithMockedSubscriptionGroup(serverTag, 0, (short) 0, i);
        return createTagsWithMethod(nrOfGroups, serverTags, createTagMethod)
                .collect(Collectors.toList());
    }

    protected List<ISourceDataTag> createAndMockTagsInNewGroups(int nrOfGroups, ServerTagProvider... serverTags) {
        mockCreateSubscriptionRequest(nrOfGroups, serverTags.length);
        BiFunction<Integer, ServerTagProvider, ISourceDataTag> createTagMethod =
                (i, serverTag) -> serverTag.createDataTag(0, (short) 0, i);

        return createTagsWithMethod(nrOfGroups, serverTags, createTagMethod)
                .collect(Collectors.groupingBy(ISourceDataTag::getTimeDeadband))
                .entrySet()
                .stream()
                .map(entry -> {
                    mockCreateSubscription(entry.getKey());
                    return entry.getValue();
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @NotNull
    private Stream<ISourceDataTag> createTagsWithMethod(int nrOfGroups, ServerTagProvider[] serverTags, BiFunction<Integer, ServerTagProvider, ISourceDataTag> fn) {
        AtomicInteger counter = new AtomicInteger();
        return Stream.of(serverTags)
                .map(serverTag -> {
                    if (counter.get() < nrOfGroups) counter.getAndIncrement();
                    return fn.apply(counter.get(), serverTag);
                });
    }

    private void mockCreateSubscription(int tagtimedb) {
        int timedb = Math.min(tagtimedb, Deadband.minTimeDeadband);
        EasyMock.expect(client.getSubscriptionManager())
                .andReturn(subscriptionManager).once();
        EasyMock.expect(subscriptionManager.createSubscription(timedb))
                .andReturn(CompletableFuture.completedFuture(subscription)).once();
    }

    private void mockCreateSubscriptionRequest(int nrOfGroups, int nrOfTags) {
        EasyMock.expect(subscription.createMonitoredItems(anyObject(TimestampsToReturn.class), anyObject(List.class), anyObject(BiConsumer.class)))
                .andReturn(CompletableFuture.completedFuture(nrOfTags))
                .times(nrOfGroups);
        EasyMock.expect(client.getSerializationContext())
                .andReturn(newDefaultSerializationContext())
                .times(nrOfTags);
    }

    private ISourceDataTag createTagWithMockedSubscriptionGroup(ServerTagProvider serverTag, float valueDeadband, short deadbandType, int timeDeadband) {
        ISourceDataTag tag = serverTag.createDataTag(valueDeadband, deadbandType, timeDeadband);
        GroupDefinitionPair pair = tagSubscriptionMapper.toGroup(tag);
        pair.getSubscriptionGroup().setSubscription(subscription);
        return tag;
    }

    private static SerializationContext newDefaultSerializationContext() {
        return new SerializationContext() {
            private final NamespaceTable namespaceTable = new NamespaceTable();
            public EncodingLimits getEncodingLimits() {
                return EncodingLimits.DEFAULT;
            }
            public NamespaceTable getNamespaceTable() {
                return this.namespaceTable;
            }
            public DataTypeManager getDataTypeManager() {
                return OpcUaDataTypeManager.getInstance();
            }
        };
    }
}
