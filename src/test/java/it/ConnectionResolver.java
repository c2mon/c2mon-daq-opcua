package it;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class ConnectionResolver {

    private GenericContainer image;

    public ConnectionResolver(GenericContainer image) {
        this.image = image;
    }

    public void initialize() {
        log.info("Servers starting... ");
        image.start();
        log.info("Servers ready");
    }

    public String getURI(int port) {
        String hostName = image.getContainerIpAddress();
        return "opc.tcp://" + hostName + ":" + port;
    }

    public void close() {
        image.stop();
        image.close();
    }
}
