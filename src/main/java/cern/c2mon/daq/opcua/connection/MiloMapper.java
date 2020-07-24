package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.type.TypeConverter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static cern.c2mon.shared.common.datatag.SourceDataTagQualityCode.*;

/**
 * This utility class provides a collection of functions mapping Milo- or OPCUA specific Java classes into a format that
 * is interpretable for C2MON.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class MiloMapper {

    /**
     * Represents a {@link StatusCode} as a {@link SourceDataTagQualityCode}
     * @param statusCode the status code returned by the Eclipse Milo client
     * @return a quality code that can be associated with a C2MON {@link cern.c2mon.shared.common.datatag.ISourceDataTag}.
     */
    public static SourceDataTagQuality getDataTagQuality(StatusCode statusCode) {
        SourceDataTagQualityCode tagCode = UNKNOWN;
        if (statusCode.isGood()) {
            tagCode = OK;
        } else if (OPCUAException.isNodeIdConfigIssue(statusCode)) {
            tagCode = INCORRECT_NATIVE_ADDRESS;
        } else if (OPCUAException.isDataUnavailable(statusCode)) {
            tagCode = INCORRECT_NATIVE_ADDRESS;
        } else if (statusCode.isBad()) {
            tagCode = VALUE_CORRUPTED;
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
        final String className = objectClass.getName();
        if (!TypeConverter.isConvertible(variant.getValue(), className)) {
            log.error("The {} cannot convert the value object {} into class {}.", TypeConverter.class.getName(), variant.getValue(), className);
            return null;
        }
        return TypeConverter.cast(variant.getValue(), className);
    }
}