package cern.c2mon.daq.opcua.connection;

import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Deadband {
    public static int minTimeDeadband = 500;

    int time;
    float value;
    short type;

    public static Deadband of(ISourceDataTag tag) {
        return new Deadband(
                tag.getTimeDeadband(),
                Math.min(minTimeDeadband, tag.getValueDeadband()),
                tag.getValueDeadbandType());
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