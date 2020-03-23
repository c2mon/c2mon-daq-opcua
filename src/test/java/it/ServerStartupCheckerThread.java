package it;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.downstream.EndpointImpl;
import cern.c2mon.daq.opcua.downstream.MiloClientWrapperImpl;
import cern.c2mon.daq.opcua.mapping.TagSubscriptionMapperImpl;
import cern.c2mon.daq.opcua.upstream.EventPublisher;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;

@AllArgsConstructor
public class ServerStartupCheckerThread implements Runnable {
    private String address;
    @Getter
    private Thread thread;

    public ServerStartupCheckerThread(String address) {
        this.address = address;
        thread = new Thread(this, address);
        thread.start();
    }

    @SneakyThrows
    @Override
    public void run() {
        Endpoint endpoint = new EndpointImpl(
                new MiloClientWrapperImpl(address, SecurityPolicy.None),
                new TagSubscriptionMapperImpl(),
                new EventPublisher());

        boolean serverRunning = false;
        synchronized (this) {
            while (!serverRunning) {
                try {
                    endpoint.initialize(false);
                    serverRunning = endpoint.isConnected();
                } catch (Exception e) {
                    Thread.sleep(100);
                    //Server not yet ready
                }
            }
            endpoint.reset();
            notify();
        }
    }
}
