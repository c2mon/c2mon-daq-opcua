package cern.c2mon.daq.opcua.connection;

import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import com.google.common.collect.ImmutableSet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.serialization.UaEnumeration;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.eclipse.milo.opcua.stack.core.StatusCodes.*;

/**
 * This utility class provides a collection of functions mapping Milo- or OPCUA specific Java classes into a format that
 * is interpretable for C2MON.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MiloMapper {

    /** Status Codes indicating a node ID supplied by an incorrect hardware address */
    private static final Collection<Long> INCORRECT_NATIVE_ADDRESS = ImmutableSet.<Long>builder().add(
            Bad_NodeIdInvalid,
            Bad_NodeIdUnknown,
            Bad_ParentNodeIdInvalid,
            Bad_SourceNodeIdInvalid,
            Bad_TargetNodeIdInvalid,
            Bad_BoundNotSupported,
            Bad_BoundNotFound,
            Bad_ServiceUnsupported,
            Bad_NotSupported,
            Bad_ViewIdUnknown,
            Bad_NodeClassInvalid,
            Bad_MethodInvalid,
            Bad_ArgumentsMissing,
            Bad_DeadbandFilterInvalid).build();

    private static final Collection<Long> DATA_UNAVAILABLE = ImmutableSet.<Long>builder().add(
            Bad_DataLost,
            Bad_DataUnavailable,
            Bad_NoDataAvailable,
            Bad_NoData,
            Bad_NotReadable,
            Bad_NotWritable,
            Bad_NotFound,
            Bad_IndexRangeNoData,
            Uncertain_InitialValue,
            Uncertain_NoCommunicationLastUsableValue,
            Uncertain_NotAllNodesAvailable,
            Uncertain_SubstituteValue).build();

    private static final Collection<Long> OUT_OF_BOUNDS = ImmutableSet.<Long>builder().add(
            Bad_OutOfRange).build();

    private static final Collection<Long> UNSUPPORTED_TYPE = ImmutableSet.<Long>builder().add(
            Bad_TypeMismatch,
            Bad_DataEncodingInvalid).build();

    private static final Collection<Long> VALUE_CORRUPTED = ImmutableSet.<Long>builder().add(
            Uncertain_SensorNotAccurate,
            Uncertain_EngineeringUnitsExceeded).build();


    /**
     * Represents a {@link StatusCode} as a {@link SourceDataTagQualityCode}
     * @param statusCode the status code returned by the Eclipse Milo client
     * @return a quality code that can be associated with a C2MON {@link cern.c2mon.shared.common.datatag.ISourceDataTag}.
     */
    public static SourceDataTagQuality getDataTagQuality(StatusCode statusCode) {
        SourceDataTagQualityCode tagCode = SourceDataTagQualityCode.UNKNOWN;
        if (statusCode == null) {
            return new SourceDataTagQuality(tagCode, "No status code was passed with the value update");
        } else if (statusCode.isGood()) {
            tagCode = SourceDataTagQualityCode.OK;
        } else if (OUT_OF_BOUNDS.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.OUT_OF_BOUNDS;
        } else if (DATA_UNAVAILABLE.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.DATA_UNAVAILABLE;
        } else if (INCORRECT_NATIVE_ADDRESS.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS;
        } else if (VALUE_CORRUPTED.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.VALUE_CORRUPTED;
        } else if (UNSUPPORTED_TYPE.contains(statusCode.getValue())) {
            tagCode = SourceDataTagQualityCode.UNSUPPORTED_TYPE;
        }
        return new SourceDataTagQuality(tagCode, statusCode.toString());
    }

    /**
     * Extracts the actual object from the list of {@link Variant}s that are returned by the Milo client.
     * @param variants the variants wrap objects returned by the Milo client. They can be output arguments returned by a
     *                 call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                 update of a subscription, or other
     * @return the POJOs extracted from the Variants
     */
    public static Object[] toObject(Variant... variants) {
        return Stream.of(variants)
                .map(MiloMapper::toObject)
                .filter(Objects::nonNull)
                .toArray();
    }

    /**
     * Extracts the actual object from a {@link Variant} returned by the Milo client.
     * @param variant the variant wraps an object returned by the Milo client. It can an the output argument returned by
     *                a call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                update of a subscription, or other
     * @return the POJO extracted from the variant
     */
    public static Object toObject(Variant variant) {
        final Optional<ExpandedNodeId> dataType = variant.getDataType();
        if (!dataType.isPresent()) {
            log.info("The variant {} did not contain a data type and cannot be processed.", variant);
            return null;
        }
        final Optional<NodeId> nodeId = dataType.get().local(null);
        if (!nodeId.isPresent()) {
            log.error("The NodeID {} resides in another server. A Namespace Table must be provided to extract it.", dataType.get().toString());
            return null;
        }
        final Class<?> objectClass = TypeUtil.getBackingClass(nodeId.get());
        if (objectClass == null) {
            log.error("The backing object class was not recognized by the Milo OPC UA stack and cannot be processed.");
            return null;
        }
        return variant.getValue() instanceof UaEnumeration ? ((UaEnumeration) variant.getValue()).getValue() : variant.getValue();
    }
}