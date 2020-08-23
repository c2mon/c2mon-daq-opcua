package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The AliveWriter ensures that the SubEquipments connected to the OPC UA server are still running by writing to them.
 * Not to be confused with OPC UA's built-in functionality. The built-in "keepalive" only asserts that the server is
 * running. The SubEquipment behind the Server may be down. The AliveWriter checks the state of the SubEquipment by
 * writing a counter to it and validating that the counter values are as expected.
 */
@Component("aliveWriter")
@Slf4j
@RequiredArgsConstructor
@ManagedResource
public class AliveWriter {

    private final AtomicInteger writeCounter = new AtomicInteger(0);
    private final Controller controllerProxy;
    private final MessageSender messageSender;
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> writeAliveTask;
    private NodeId aliveTagAddress;

    /**
     * Start the AliveWriter on the given aliveTag.
     * @param aliveTag         the DataTag containing the Node acting as AliveTag
     * @param aliveTagInterval the interval in which to write to the aliveTag
     */
    public void startAliveWriter(ISourceDataTag aliveTag, long aliveTagInterval) {
        try {
            final ItemDefinition def = ItemDefinition.of(aliveTag);
            aliveTagAddress = def.getNodeId();
            log.info("Starting Alive Writer...");
            startAliveWriter(aliveTagInterval);
        } catch (ConfigurationException e) {
            log.error("Error starting the AliveWriter. Please check the configuration.", e);
        }
    }

    /**
     * Restart a previously configured aliveWriter. To be invoked as an actuator operation.
     * @param aliveTagInterval the interval during which to read from the aliveTag
     */
    @ManagedOperation
    public void startAliveWriter(long aliveTagInterval) {
        if (aliveTagAddress != null) {
            log.info("Starting AliveWriter...");
            cancelTask();
            // the AliveWriter may have been shutdown terminally. In this case the executor was stopped and must be recreated.
            if (executor.isShutdown()) {
                executor = Executors.newScheduledThreadPool(1);
            }
            if (aliveTagInterval > 0L) {
                this.writeAliveTask = executor.scheduleAtFixedRate(this::aliveTagMonitoring, aliveTagInterval, aliveTagInterval, TimeUnit.MILLISECONDS);
            } else {
                log.error(ExceptionContext.BAD_ALIVE_TAG_INTERVAL.getMessage());
            }
        } else {
            log.error("The AliveWriter has not been configured appropriately and cannot be invoked.");
        }
    }

    /**
     * Stops the aliveWriter execution.
     */
    @ManagedOperation
    public void stopAliveWriter() {
        log.info("Stopping AliveWriter...");
        cancelTask();
        executor.shutdownNow();
    }

    private void aliveTagMonitoring() {
        try {
            if (controllerProxy.write(aliveTagAddress, writeCounter.intValue())) {
                messageSender.onAlive();
            }
            writeCounter.incrementAndGet();
            writeCounter.compareAndSet(Byte.MAX_VALUE, 0);
        } catch (OPCUAException e) {
            log.error("Error while writing alive. Retrying...", e);
        }
    }

    private void cancelTask() {
        if (writeAliveTask != null && !writeAliveTask.isCancelled()) {
            log.info("Stopping Alive Writer...");
            writeAliveTask.cancel(true);
        }
    }
}