package cern.c2mon.daq.opcua.mapping;

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Deadband {
    public static final int minTimeDeadband = 500;

    int time;
    float value;
    short type;

    public static Deadband of(ISourceDataTag tag) {
        return of(tag.getTimeDeadband(), tag.getValueDeadbandType(), (short) tag.getValueDeadband());
    }

    public static Deadband of(int time, float value, short type) {
        return new Deadband(Math.max(minTimeDeadband, time), value, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deadband deadband = (Deadband) o;
        return getTime() == deadband.getTime() &&
                Float.compare(deadband.getValue(), getValue()) == 0 &&
                Objects.equals(getType(), deadband.getType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTime(), getValue(), getType());
    }
}