package cern.c2mon.daq.opcua;

@FunctionalInterface
public interface TriConsumer<A, B, C> {
    void apply(A a, B b, C c);
}