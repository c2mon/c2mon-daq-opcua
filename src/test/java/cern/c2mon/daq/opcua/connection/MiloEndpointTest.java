package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.config.TimeRecordMode;
import cern.c2mon.daq.opcua.exceptions.*;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.SubscriptionGroup;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapper;
import cern.c2mon.daq.opcua.testutils.EdgeTagFactory;
import cern.c2mon.daq.opcua.testutils.TestListeners;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.serialization.EncodingLimits;
import org.eclipse.milo.opcua.stack.core.serialization.SerializationContext;
import org.eclipse.milo.opcua.stack.core.types.DataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDataTypeManager;
import org.eclipse.milo.opcua.stack.core.types.builtin.*;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static cern.c2mon.daq.opcua.MessageSender.EquipmentState.OK;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

public class MiloEndpointTest {
    OpcUaClient mockClient = createNiceMock(OpcUaClient.class);
    UaSubscription mockSubscription = createNiceMock(UaSubscription.class);
    RetryDelegate mockRetryDelegate = createNiceMock(RetryDelegate.class);
    SecurityModule mockSecurityModule = createNiceMock(SecurityModule.class);
    OpcUaSubscriptionManager mockSubscriptionManager = createNiceMock(OpcUaSubscriptionManager.class);
//    ExpandedNodeId mockExpandedNodeID = createNiceMock(ExpandedNodeId.class);

    Endpoint endpoint;
    TagSubscriptionMapper mapper = new TagSubscriptionMapper();
    TestListeners.TestListener msgSender = new TestListeners.TestListener();

    SubscriptionGroup altBoolSbsGrp;
    SubscriptionGroup dipSbsGrp;
    ItemDefinition altBool;
    ItemDefinition dip;

    public static long altBoolID = 1L;
    public static long dipID = 2L;

    @BeforeEach
    public void setUp() throws ConfigurationException {
        altBool = mapper.getOrCreateDefinition(EdgeTagFactory.AlternatingBoolean.createDataTagWithID(altBoolID));
        dip = mapper.getOrCreateDefinition(EdgeTagFactory.DipData.createDataTagWithID(dipID, 0, (short) 0, 2));
        altBoolSbsGrp = mapper.getGroup(altBool.getTimeDeadband());
        dipSbsGrp = mapper.getGroup(dip.getTimeDeadband());

        AppConfigProperties properties = AppConfigProperties.builder().maxRetryAttempts(3).requestTimeout(300).timeRecordMode(TimeRecordMode.CLOSEST).retryDelay(1000).build();
        endpoint = new MiloEndpoint(mockSecurityModule, mockRetryDelegate, mapper, msgSender, properties, new AppConfig(properties).exceptionClassifierTemplate());
        expect(mockClient.getSubscriptionManager()).andReturn(mockSubscriptionManager).anyTimes();
        expect(mockClient.getSerializationContext()).andReturn(serializationContext).anyTimes();
        expect(mockClient.getNamespaceTable()).andReturn(serializationContext.getNamespaceTable()).anyTimes();
        replay(mockClient);
    }

    @Test
    public void exceptionInDiscoveryShouldBeEscalated() throws OPCUAException {
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andThrow(new CommunicationException(ExceptionContext.CONNECT))
                .anyTimes();
        replay(mockRetryDelegate);
        assertThrows(OPCUAException.class, () -> endpoint.initialize("test"));
    }


    @Test
    public void exceptionInSecurityModuleShouldBeEscalated() throws OPCUAException {
        final EndpointDescription description = EndpointDescription.builder().endpointUrl("opc.tcp://local:200").build();
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(description))
                .once();
        expect(mockSecurityModule.createClient(anyString(), anyObject()))
                .andThrow(new ConfigurationException(ExceptionContext.AUTH_ERROR))
                .anyTimes();
        replay(mockRetryDelegate, mockSecurityModule);
        assertThrows(OPCUAException.class, () -> endpoint.initialize("test"));
    }

    @Test
    public void successfulSecurityModuleShouldSetURI() throws OPCUAException {
        initializeEndpoint("test");
        assertEquals("test", endpoint.getUri());
    }

    @Test
    public void addSessionActivityListenerShouldSendActiveUnlessDisconnected() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(0, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void addSessionActivityListenerShouldNotSendActiveIfDisconnected() throws OPCUAException {
        initializeEndpoint("test");
        ((SessionActivityListener)endpoint).onSessionInactive(null);
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void twoAddSessionActivityListenerShouldOnlySendOneActive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(2, 1);
        endpoint.manageSessionActivityListener(true, l);
        endpoint.manageSessionActivityListener(true, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
    }

    @Test
    public void removeSessionActivityListenerShouldNotSendActiveOrInactive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(false, l);
        assertEquals(1, l.getSessionActiveLatch().getCount());
        assertEquals(1, l.getSessionInactiveLatch().getCount());
    }

    @Test
    public void addAndRemoveSessionActivityListenerShouldOnlySendFirstActive() throws OPCUAException {
        initializeEndpoint("test");
        final TestSessionActivityListener l = new TestSessionActivityListener(1, 1);
        endpoint.manageSessionActivityListener(true, l);
        endpoint.manageSessionActivityListener(false, l);
        assertEquals(0, l.getSessionActiveLatch().getCount());
        assertEquals(1, l.getSessionInactiveLatch().getCount());
    }

    @Test
    public void updateEquipmentStateShouldNotifyMessageSenderIfActive() throws InterruptedException, ExecutionException, TimeoutException {
        final CompletableFuture<MessageSender.EquipmentState> s = msgSender.getStateUpdate().get(0);
        endpoint.setUpdateEquipmentStateOnSessionChanges(true);
        assertTrue(s.isDone());
        assertEquals(OK, s.get(20L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void updateEquipmentStateShouldNotNotifyMessageSenderIfDisconnected() {
        ((SessionActivityListener)endpoint).onSessionInactive(null);
        final CompletableFuture<MessageSender.EquipmentState> s = msgSender.getStateUpdate().get(0);
        endpoint.setUpdateEquipmentStateOnSessionChanges(true);
        assertFalse(s.isDone());
    }

    @Test
    public void setUpdateEquipmentStateToFalseShouldNotNotifyMessageSenderIf() {
        final CompletableFuture<MessageSender.EquipmentState> s = msgSender.getStateUpdate().get(0);
        endpoint.setUpdateEquipmentStateOnSessionChanges(false);
        assertFalse(s.isDone());
    }

    @Test
    public void disconnectShouldDoNothingIfClientIsNotSet() {
        resetToStrict(mockRetryDelegate);
        replay(mockRetryDelegate);
        endpoint.disconnect();
        verify(mockRetryDelegate);
    }

    @Test
    public void disconnectShouldCallDisconnectIfClientIsSet() throws OPCUAException {
        initializeEndpoint("test");
        reset(mockRetryDelegate);
        Capture<ExceptionContext> capture = newCapture(CaptureType.ALL);
        expect(mockRetryDelegate.completeOrRetry(capture(capture), anyObject(), anyObject()))
                .andReturn(null)
                .once();
        replay(mockRetryDelegate);
        endpoint.disconnect();
        assertEquals(ExceptionContext.DISCONNECT, capture.getValue());
    }

    @Test
    public void exceptionDuringDisconnectShouldNotBeEscalated() throws  OPCUAException {
        initializeEndpoint("test");
        reset(mockRetryDelegate);
        Capture<ExceptionContext> capture = newCapture(CaptureType.ALL);
        expect(mockRetryDelegate.completeOrRetry(capture(capture), anyObject(), anyObject()))
                .andThrow(new CommunicationException(ExceptionContext.DISCONNECT))
                .anyTimes();
        replay(mockRetryDelegate);
        assertDoesNotThrow(() -> endpoint.disconnect());
    }

    @Test
    public void configurationExceptionDuringSubscribeShouldEscalate() throws OPCUAException {
        initializeEndpoint("test");
        OPCUAException e = new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION);
        throwExceptionFromRetryDelegate(e);
        assertThrows(e.getClass(), () -> endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool)));
    }

    @Test
    public void endpointDisconnectedExceptionDuringSubscribeShouldEscalate() throws OPCUAException {
        initializeEndpoint("test");
        OPCUAException e = new EndpointDisconnectedException(ExceptionContext.CREATE_SUBSCRIPTION);
        throwExceptionFromRetryDelegate(e);
        assertThrows(e.getClass(), () -> endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool)));
    }

    @Test
    public void otherExceptionDuringSubscribeShouldNotEscalate() throws OPCUAException {
        initializeEndpoint("test");
        final OPCUAException e = new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION);
        throwExceptionFromRetryDelegate(e);
        assertDoesNotThrow(() -> endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool)));
    }

    @Test
    public void failedSubscribeShouldReturnDataUnavailable() throws OPCUAException {
        initializeEndpoint("test");
        throwExceptionFromRetryDelegate(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
        final Map<Integer, SourceDataTagQuality> subscribe = endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool));
        assertTrue(subscribe.values().stream()
                .allMatch(q -> q.equals(new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE))));
    }

    @Test
    public void subscribeShouldReturnClientHandleQualityMap() throws OPCUAException {
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        final Map<Integer, SourceDataTagQuality> subscribe = endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool));
        assertTrue(subscribe.containsKey(altBool.getClientHandle()));
        assertTrue(subscribe.containsValue(MiloMapper.getDataTagQuality(StatusCode.GOOD)));
    }

    @Test
    public void failedRequestsShouldBeReturnedAsDataUnavailable() throws OPCUAException {
        initializeEndpoint("test");
        throwExceptionFromRetryDelegate(new CommunicationException(ExceptionContext.CREATE_SUBSCRIPTION));
        final Map<Integer, SourceDataTagQuality> subscribe = endpoint.subscribe(dipSbsGrp, Collections.singletonList(altBool));
        assertTrue(subscribe.containsValue(new SourceDataTagQuality(SourceDataTagQualityCode.DATA_UNAVAILABLE)));
    }

    @Test
    public void recreateAllSubscriptionsShouldSucceedIfThereAreNoTagsInSubscriptions() {
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldSucceedIfThereAreGroupsInMapper() {
        mapper.clear();
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldSucceedIfAllSubscriptionsCouldBeRecreated() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldSucceedForMulitpleSubscriptionGroups() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        dipSbsGrp.add(dipID, dip);
        setUpMonitoredItems(Arrays.asList(altBool, dip), StatusCode.GOOD);
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldFailWithCommunicationExceptionIfNoSubscriptionIsCreated() throws OPCUAException {
        altBoolSbsGrp.add(mapper.getTagId(altBool.getClientHandle()), altBool);
        dipSbsGrp.add(mapper.getTagId(dip.getClientHandle()), dip);
        initializeEndpoint("test");
        throwExceptionFromRetryDelegate(new ConfigurationException(ExceptionContext.CREATE_SUBSCRIPTION));
        assertThrows(CommunicationException.class, () -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldSucceedIfSingleRecreationSucceeded() throws OPCUAException {
        altBoolSbsGrp.add(mapper.getTagId(altBool.getClientHandle()), altBool);
        dipSbsGrp.add(mapper.getTagId(dip.getClientHandle()), dip);
        setUpMonitoredItems(Arrays.asList(altBool), StatusCode.GOOD);
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void recreateAllSubscriptionsShouldNotEscalateEndpointDisconnectedException() throws OPCUAException {
        altBoolSbsGrp.add(mapper.getTagId(altBool.getClientHandle()), altBool);
        dipSbsGrp.add(mapper.getTagId(dip.getClientHandle()), dip);
        initializeEndpoint("test");
        throwExceptionFromRetryDelegate(new EndpointDisconnectedException(ExceptionContext.CREATE_SUBSCRIPTION));
        assertDoesNotThrow(() -> endpoint.recreateAllSubscriptions());
    }

    @Test
    public void deleteUnsubscribedItemFromSubscriptionShouldReturnFalse() {
        assertFalse(endpoint.deleteItemFromSubscription(-1, altBool.getTimeDeadband()));
    }

    @Test
    public void deleteItemFromSubscriptionOfSizeOneShouldDeleteSubscription() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.GOOD, altBool);
        Capture<ExceptionContext> capture = newCapture(CaptureType.ALL);

        resetToStrict(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(capture(capture), anyObject(), anyObject()))
                .andReturn(null)
                .once();
        replay(mockRetryDelegate);

        endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband());
        assertEquals(ExceptionContext.DELETE_SUBSCRIPTION, capture.getValue());
    }
    @Test
    public void deleteItemFromSubscriptionOfSizeOneShouldReturnTrue() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.GOOD, altBool);
        returnFromRetryDelegate(Collections.singletonList(StatusCode.GOOD));

        assertTrue(endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband()));

    }

    @Test
    public void deleteItemFromSubscriptionContainingMultipleSubscriptionsShouldOnlyDeleteItem() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.GOOD, altBool, dip);
        Capture<ExceptionContext> capture = newCapture(CaptureType.ALL);

        resetToStrict(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(capture(capture), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(StatusCode.GOOD))
                .once();
        replay(mockRetryDelegate);

        endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband());
        assertEquals(ExceptionContext.DELETE_MONITORED_ITEM, capture.getValue());
    }

    @Test
    public void deleteSingleMonitoredItemWithGoodStatusCodeShouldReturnTrue() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.GOOD, altBool, dip);
        returnFromRetryDelegate(Collections.singletonList(StatusCode.GOOD));

        assertTrue(endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband()));
    }

    @Test
    public void deleteSingleMonitoredItemWithBadStatusCodeShouldReturnFalse() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.BAD, altBool, dip);
        returnFromRetryDelegate(Collections.singletonList(StatusCode.BAD));

        assertFalse(endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband()));
    }


    @Test
    public void exceptionDuringDeleteSingleMonitoredItemShouldReturnFalse() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.BAD, altBool, dip);
        throwExceptionFromRetryDelegate(new CommunicationException(ExceptionContext.DELETE_MONITORED_ITEM));

        assertFalse(endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband()));
    }

    @Test
    public void exceptionDuringDeleteSubscriptionShouldReturnFalse() throws OPCUAException {
        altBoolSbsGrp.add(altBoolID, altBool);
        setUpMonitoredItems(Collections.singletonList(altBool), StatusCode.GOOD);
        endpoint.subscribe(altBoolSbsGrp, Collections.singletonList(altBool));
        setUpMonitoredItemsInSubscription(StatusCode.BAD, altBool);
        throwExceptionFromRetryDelegate(new CommunicationException(ExceptionContext.DELETE_MONITORED_ITEM));

        assertFalse(endpoint.deleteItemFromSubscription(altBool.getClientHandle(), altBool.getTimeDeadband()));
    }

    @Test
    public void exceptionDuringReadShouldBeEscalated() throws OPCUAException {
        final CommunicationException e = new CommunicationException(ExceptionContext.DELETE_MONITORED_ITEM);
        throwExceptionFromRetryDelegate(e);
        assertThrows(e.getClass(), () -> endpoint.read(altBool.getNodeId()));
    }

    @Test
    public void readNullValueShouldThrowConfigurationException() throws OPCUAException {
        returnFromRetryDelegate(null);
        assertThrows(ConfigurationException.class, () -> endpoint.read(altBool.getNodeId()));
    }

    @Test
    public void readValueShouldReturnValueUpdateWithMiloMappedQuality() throws OPCUAException {
        returnFromRetryDelegate(new DataValue(StatusCode.GOOD));
        final Map.Entry<ValueUpdate, SourceDataTagQuality> read = endpoint.read(altBool.getNodeId());
        assertEquals(MiloMapper.getDataTagQuality(StatusCode.GOOD), read.getValue());
    }

    @Test
    public void readValueShouldReturnPureValueUpdate() throws OPCUAException {
        returnFromRetryDelegate(new DataValue(new Variant(2), StatusCode.GOOD));
        final Map.Entry<ValueUpdate, SourceDataTagQuality> read = endpoint.read(altBool.getNodeId());
        assertEquals(2, read.getKey().getValue());
    }

    @Test
    public void readValueShouldContainTimestamp() throws OPCUAException {
        returnFromRetryDelegate(new DataValue(StatusCode.GOOD));
        final Map.Entry<ValueUpdate, SourceDataTagQuality> read = endpoint.read(altBool.getNodeId());
        assertNotNull(read.getKey().getSourceTimestamp());
    }

    @Test
    public void exceptionDuringWriteShouldBeEscalated() throws OPCUAException {
        final CommunicationException e = new CommunicationException(ExceptionContext.DELETE_MONITORED_ITEM);
        throwExceptionFromRetryDelegate(e);
        assertThrows(e.getClass(), () -> endpoint.write(altBool.getNodeId(), 2));
    }

    @Test
    public void writeShouldReturnTrueIfStatusCodeIsGood() throws OPCUAException {
        returnFromRetryDelegate(StatusCode.GOOD);
        assertTrue(endpoint.write(altBool.getNodeId(), 2));
    }

    @Test
    public void writeShouldReturnFalseIfStatusCodeIsNotGood() throws OPCUAException {
        returnFromRetryDelegate(StatusCode.UNCERTAIN);
        assertFalse(endpoint.write(altBool.getNodeId(), 2));
    }

    @Test
    public void callMethodShouldShouldBrowse() throws OPCUAException {
        Capture<ExceptionContext> capture = newCapture(CaptureType.ALL);

        resetToStrict(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(capture(capture), anyObject(), anyObject()))
                .andThrow(new CommunicationException(ExceptionContext.DELETE_MONITORED_ITEM))
                .once();
        replay(mockRetryDelegate);

        try {
            endpoint.callMethod(altBool, null);
        } catch (OPCUAException ignored) {};
        assertEquals(ExceptionContext.BROWSE, capture.getValue());
    }

    @Test
    public void callMethodShouldShouldThrowErrorIfBrowseReturnsNullReferences() throws OPCUAException {
        final BrowseResult browseResult = new BrowseResult(StatusCode.GOOD, new ByteString(new byte[]{}), null);
        returnFromRetryDelegate(browseResult);
        assertThrows(ConfigurationException.class, () -> endpoint.callMethod(altBool, null));
    }


    @Test
    public void callMethodShouldShouldThrowErrorIfBrowseReturnsEmpty() throws OPCUAException {
        final BrowseResult browseResult = new BrowseResult(
                StatusCode.GOOD,
                new ByteString(new byte[]{}),
                new ReferenceDescription[]{});
        returnFromRetryDelegate(browseResult);
        assertThrows(ConfigurationException.class, () -> endpoint.callMethod(altBool, null));
    }


    @Test
    public void callMethodShouldShouldThrowErrorIfBrowseResultIsNotLocal() throws OPCUAException {
        initializeEndpoint("test");
        final ExpandedNodeId expNodeId = new ExpandedNodeId(
                altBool.getNodeId().getNamespaceIndex(),
                "http://opcfoundation.org/UA/",
                altBool.getNodeId().getIdentifier().toString(),
                UInteger.valueOf(2));
        final ReferenceDescription rd = new ReferenceDescription(altBool.getNodeId(), true, expNodeId, null, null, null, null);
        final BrowseResult browseResult = new BrowseResult(
                StatusCode.GOOD,
                new ByteString(new byte[]{1}),
                new ReferenceDescription[]{rd});
        returnFromRetryDelegate(browseResult);
        assertThrows(ConfigurationException.class, () -> endpoint.callMethod(altBool, null));
    }



    @Test
    public void callMethodShouldShouldThrowErrorIfBrowseStatusCodeIsBad() throws OPCUAException {
        initializeEndpoint("test");
        final ExpandedNodeId expNodeId = new ExpandedNodeId(
                altBool.getNodeId().getNamespaceIndex(),
                "http://opcfoundation.org/UA/",
                altBool.getNodeId().getIdentifier().toString());
        final ReferenceDescription rd = new ReferenceDescription(altBool.getNodeId(), true, expNodeId, null, null, null, null);
        final BrowseResult browseResult = new BrowseResult(
                StatusCode.BAD,
                new ByteString(new byte[]{1}),
                new ReferenceDescription[]{rd});
        returnFromRetryDelegate(browseResult);
        assertThrows(ConfigurationException.class, () -> endpoint.callMethod(altBool, null));
    }

    @Test
    public void callMethodShouldUseBrowsedMethodAsMethodID() throws OPCUAException {
        /*

        initializeEndpoint("test");
        final ExpandedNodeId expNodeId = new ExpandedNodeId(
                altBool.getNodeId().getNamespaceIndex(),
                "http://opcfoundation.org/UA/",
                altBool.getNodeId().getIdentifier().toString());
        final ReferenceDescription rd = new ReferenceDescription(altBool.getNodeId(), true, expNodeId, null, null, null, null);
        final BrowseResult browseResult = new BrowseResult(StatusCode.GOOD,
                new ByteString(new byte[]{1}),
                new ReferenceDescription[]{rd});
        final Capture<Supplier<CompletableFuture<CallMethodResult>>> methodRequestCapture = newCapture(CaptureType.ALL);
        final CallMethodResult methodResult = new CallMethodResult(StatusCode.GOOD, new StatusCode[]{}, new DiagnosticInfo[]{}, new Variant[]{new Variant(StatusCode.BAD)});
        resetToStrict(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(browseResult)
                .once();
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), capture(methodRequestCapture)))
                .andReturn(methodCallResult)
                .once();
        replay(mockRetryDelegate);
        endpoint.callMethod(altBool, null);

        final Capture<NodeId> methodCallCapture = newCapture(CaptureType.ALL);
        final Capture<NodeId> objectCapture = newCapture(CaptureType.ALL);
        final Capture<NodeId> methodCapture = newCapture(CaptureType.ALL);
        resetToNice(mockClient);

        expect(mockClient.call(capture(methodCallCapture))).andReturn()
        replay(mockClient);
        methodRequestCapture.getValue().get();
*/

    }

    /*
    final BrowseResult result = retryDelegate.completeOrRetry(BROWSE, this::getDisconnectPeriod, () -> client.browse(bd));
        if (result.getReferences() != null && result.getReferences().length > 0) {
        final Optional<NodeId> objectNode = result.getReferences()[0].getNodeId().local(client.getNamespaceTable());
        if (result.getStatusCode().isGood() && objectNode.isPresent()) {
            return objectNode.get();
        }
    }

    public Map.Entry<Boolean, Object[]> callMethod(ItemDefinition definition, Object arg) throws OPCUAException {
        return (definition.getMethodNodeId() == null)
                ? callMethod(getParentObjectNodeId(definition.getNodeId()), definition.getNodeId(), arg)
                : callMethod(definition.getNodeId(), definition.getMethodNodeId(), arg);
    }
*/

    private void returnFromRetryDelegate(Object toReturn) throws OPCUAException {
        resetToStrict(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(toReturn)
                .once();
        replay(mockRetryDelegate);
    }

    private void setUpMonitoredItemsInSubscription(StatusCode code, ItemDefinition... definitions) {
        final ImmutableList<UaMonitoredItem> items = ImmutableList.<UaMonitoredItem>builder()
                .addAll(definitionsToMonitoredItems(Arrays.asList(definitions), code))
                .build();
        expect(mockSubscription.getMonitoredItems())
                .andReturn(items)
                .anyTimes();
        replay(mockSubscription);
    }

    private void throwExceptionFromRetryDelegate(OPCUAException e) throws OPCUAException {
        resetToNice(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andThrow(e)
                .anyTimes();
        replay(mockRetryDelegate);
    }

    private List<OpcUaMonitoredItem> definitionsToMonitoredItems(Collection<ItemDefinition> definitions, StatusCode code) {
        return definitions.stream()
                .map(d -> new OpcUaMonitoredItem(mockClient,
                        UInteger.valueOf(d.getClientHandle()),
                        null, null, code, 0, null, null, MonitoringMode.Disabled, null))
                .collect(Collectors.toList());
    }

    private void setUpMonitoredItems(Collection<ItemDefinition> definitions, StatusCode code) throws OPCUAException {
        initializeEndpoint("test");
        resetToNice(mockRetryDelegate);
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(mockSubscription)
                .once();
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(definitionsToMonitoredItems(definitions, code))
                .once();
        replay(mockRetryDelegate);
    }

    private void initializeEndpoint(String uri) throws OPCUAException {
        final EndpointDescription description = EndpointDescription.builder().endpointUrl("opc.tcp://local:200").build();
        expect(mockRetryDelegate.completeOrRetry(anyObject(), anyObject(), anyObject()))
                .andReturn(Collections.singletonList(description))
                .once();
        expect(mockSecurityModule.createClient(anyString(), anyObject()))
                .andReturn(mockClient)
                .anyTimes();
        replay(mockRetryDelegate, mockSecurityModule);
        endpoint.initialize(uri);
    }

    SerializationContext serializationContext = new SerializationContext() {
        @Getter
        final
        NamespaceTable namespaceTable = new NamespaceTable();

        @Override
        public EncodingLimits getEncodingLimits() {
            return new EncodingLimits(1, 2, 3);
        }

        @Override
        public DataTypeManager getDataTypeManager() {
            return OpcUaDataTypeManager.createAndInitialize(namespaceTable);
        }
    };

    @Getter
    private static class TestSessionActivityListener implements SessionActivityListener {
        CountDownLatch sessionActiveLatch;
        CountDownLatch sessionInactiveLatch;

        public TestSessionActivityListener(int activeLatchCount, int inactiveLatchCount) {
            sessionActiveLatch = new CountDownLatch(activeLatchCount);
            sessionInactiveLatch = new CountDownLatch(inactiveLatchCount);
        }

        @Override
        public void onSessionActive(UaSession session) {
            sessionActiveLatch.countDown();
        }

        @Override
        public void onSessionInactive(UaSession session) {
            sessionActiveLatch.countDown();
        }

    }

}
