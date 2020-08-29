package cern.c2mon.daq.opcua.testutils;

import cern.c2mon.daq.opcua.config.AppConfig;
import cern.c2mon.daq.opcua.config.AppConfigProperties;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.FailoverBase;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

@Getter
@Setter
@Slf4j
public class TestController extends FailoverBase {
    CountDownLatch serverSwitchLatch = new CountDownLatch(2);
    CompletableFuture<Void> switchDone = new CompletableFuture<>();

    @Autowired Endpoint endpoint;

    @Setter
    OPCUAException toThrow;

    public TestController(AppConfigProperties properties) {
        super(properties, new AppConfig(properties).alwaysRetryTemplate());
        listening.set(true);
        stopped.set(false);
        toThrow = null;
    }

    public void setStopped(boolean val) {
        stopped.set(val);
    }
    public void setListening(boolean val) {
        listening.set(val);
    }

    @Override
    protected Endpoint currentEndpoint() {
        return endpoint;
    }

    @Override
    protected List<Endpoint> passiveEndpoints() {
        return null;
    }

    @Override
    public void switchServers() throws OPCUAException {
        serverSwitchLatch.countDown();
        if (serverSwitchLatch.getCount() <= 0) {
            log.info("Count hit 0, ceasing retries.");
        } else if (toThrow == null) {
            log.info("Completing switchServer successfully.");
        } else {
            log.info("Throwing {}.", toThrow.getClass().getName());
            throw toThrow;
        }
    }

    public void triggerRetryFailover() {
        triggerServerSwitch();
        switchDone.complete(null);
    }

    public void setTo(int latchCount, OPCUAException toThrow) {
        serverSwitchLatch = new CountDownLatch(latchCount);
        switchDone = new CompletableFuture<>();
        this.toThrow = toThrow;
    }
}