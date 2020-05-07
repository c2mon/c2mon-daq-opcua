package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.type.TypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * This utility class provides a collection of functions mapping Milo- or OPCUA specific Java classes into a format that
 * is interpretable for C2MON.
 */
@Slf4j
public abstract class MiloMapper {

    /**
     * Represents a {@link StatusCode} as a {@link SourceDataTagQualityCode}
     *
     * @param miloStatusCode the status code returned by the Eclipse Milo client
     * @return a quality code that can be assiciated with a C2MON {@link cern.c2mon.shared.common.datatag.ISourceDataTag}.
     */
    public static SourceDataTagQualityCode getDataTagQualityCode(StatusCode miloStatusCode) {
        if (StatusCode.GOOD.equals(miloStatusCode)) {
            return SourceDataTagQualityCode.OK;
        } else if (StatusCode.BAD.equals(miloStatusCode)) {
            return SourceDataTagQualityCode.VALUE_CORRUPTED;
        }
        return SourceDataTagQualityCode.UNKNOWN;
    }

    /**
     * Extracts the actual object from the list of {@link Variant}s that are returned by the Milo client.
     *
     * @param variants the variants wrap objects returned by the Milo client. They can be output arguments returned by a
     *                 call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                 update of a subscription, or other
     * @return the POJOs extracted from the Variants
     */
    public static Object[] toObject(Variant[] variants) {
        return Stream.of(variants)
                .map(MiloMapper::toObject)
                .filter(Objects::nonNull)
                .toArray();
    }

    /**
     * Extracts the actual object from a {@link Variant} returned by the Milo client.
     *
     * @param variant the variant wraps an object returned by the Milo client. It can an the output argument returned by
     *                a call to an OPC UA method node, represent a {@link org.eclipse.milo.opcua.stack.core.types.builtin.DataValue}
     *                update of a subscription, or other
     * @return the POJO extracted from the variant
     */
    public static Object toObject(Variant variant) {
        final var dataType = variant.getDataType();
        if (dataType.isEmpty()) {
            log.info("The variant {} did not contain a data type and cannot be processed.", variant);
            return null;
        }
        final var expandedNodeId = dataType.get();
        if (!expandedNodeId.isLocal() || expandedNodeId.local().isEmpty()) {
            log.error("The NodeID {} resides in another server. A Namespace Table must be provided to extract it.", expandedNodeId);
            return null;
        }
        final Class<?> objectClass = TypeUtil.getBackingClass(expandedNodeId.local().get());
        if (objectClass == null) {
            log.error("The backing object class was not recognized by the Milo OPC UA stack and cannot be processed.");
            return null;
        }
        final String className = objectClass.getName();
        if (TypeConverter.isConvertible(variant.getValue(), className)) {
            log.error("The {} cannot convert the value object {} into class {}.", TypeConverter.class.getName(), variant.getValue(), className);
            return null;
        }
        return TypeConverter.cast(variant.getValue(), className);
    }
}