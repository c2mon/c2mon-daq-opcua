package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

public interface DataQualityMapper {

    static SourceDataTagQualityCode getDataTagQualityCode(StatusCode miloQualityCode) {
        if (StatusCode.GOOD.equals(miloQualityCode)) {
            return SourceDataTagQualityCode.OK;
        }
        else if(StatusCode.BAD.equals(miloQualityCode)) {
            return SourceDataTagQualityCode.VALUE_CORRUPTED;
        }
        return SourceDataTagQualityCode.UNKNOWN;
    }

    static SourceDataTagQualityCode getBadNodeIdCode() {
        return SourceDataTagQualityCode.INCORRECT_NATIVE_ADDRESS;
    }
}
