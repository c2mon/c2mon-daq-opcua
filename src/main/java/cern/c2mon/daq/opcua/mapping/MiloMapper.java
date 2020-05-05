package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.type.TypeConverter;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.util.TypeUtil;

import java.util.Objects;
import java.util.stream.Stream;

public class MiloMapper {

    public static Object[] toObject(Variant[] variants) {
        return Stream.of(variants)
                .map(MiloMapper::toObject)
                .filter(Objects::nonNull)
                .toArray();
    }

    public static Object toObject(Variant variant) {
        final var dataType = variant.getDataType();
        if (dataType.isPresent()) {
            final var expandedNodeId = dataType.get();
            if (expandedNodeId.isLocal()) {
                final var localId = expandedNodeId.local();
                if (localId.isPresent()) {
                    final Class<?> objectClass = TypeUtil.getBackingClass(localId.get());
                    if (objectClass != null) {
                        final String className = objectClass.getName();
                        if (TypeConverter.isConvertible(variant.getValue(), className)) {
                            return TypeConverter.cast(variant.getValue(), className);
                        }
                    }
                }
            }
        }
        return null;
    }
}