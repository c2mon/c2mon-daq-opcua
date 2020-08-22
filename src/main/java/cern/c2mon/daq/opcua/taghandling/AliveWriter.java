/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.opcua.MessageSender;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

import java.util.Map;
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

    private final Controller controllerProxy;
    private final MessageSender messageSender;
    private final AtomicInteger writeCounter = new AtomicInteger(0);
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> writeAliveTask;
    private NodeId aliveTagAddress;
    private Runnable writeAliveRunnable = null;

    /**
     * Start the AliveWriter on the given aliveTag. If no the writer is not enabled or no alive tag is specified, a
     * simple alive monitoring procedure is started instead which uses regular read operations to supervise the
     * connection to the server.
     * @param aliveTag         the DataTag containing the Node acting as AliveTag
     * @param aliveTagInterval the interval in which to write to the aliveTag
     */
    public void startAliveWriter(ISourceDataTag aliveTag, long aliveTagInterval) {
        try {
            final ItemDefinition def = ItemDefinition.of(aliveTag);
            aliveTagAddress = def.getNodeId();
            log.info("Starting Alive Writer...");
            writeAliveRunnable = this::aliveTagMonitoring;
        } catch (ConfigurationException e) {
            log.error("Error starting the AliveWriter. Please check the configuration.", e);
        }
        startAliveWriter(aliveTagInterval);
    }

    /**
     * Restart a aliveWriter. To be invoked as an actuator operation.
     * @param aliveTagInterval the interval during which to read from the aliveTag
     */
    @ManagedOperation
    public void startAliveWriter(long aliveTagInterval) {
        if (writeAliveRunnable == null) {
            log.info("Fall back to simple alive monitoring procedure...");
            writeAliveRunnable = this::simpleAliveMonitoring;
        }
        scheduleWriteTask(aliveTagInterval, writeAliveRunnable);
    }

    /**
     * Stops the aliveWriter execution.
     */
    @ManagedOperation
    public void stopWriter() {
        cancelTask();
        executor.shutdown();
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

    private void simpleAliveMonitoring() {
        try {
            final Map.Entry<ValueUpdate, SourceDataTagQuality> read = controllerProxy.read(Identifiers.Server_ServiceLevel);
            if (read.getValue().isValid()) {
                messageSender.onAlive();
            }
        } catch (OPCUAException e) {
            log.error("Exception during simple alive monitoring ", e);
        }
    }

    private void scheduleWriteTask(long writeTime, Runnable r) {
        cancelTask();
        // the AliveWriter may have been shutdown terminally. In this case the executor was stopped and must be recreated.
        if (executor.isShutdown()) {
            executor = Executors.newScheduledThreadPool(1);
        }
        if (writeTime > 0L) {
            this.writeAliveTask = executor.scheduleAtFixedRate(r, writeTime, writeTime, TimeUnit.MILLISECONDS);
        } else {
            log.error(ExceptionContext.BAD_ALIVE_TAG_INTERVAL.getMessage());
        }
    }

    private void cancelTask() {
        if (writeAliveTask != null && !writeAliveTask.isCancelled()) {
            log.info("Stopping Alive Writer...");
            writeAliveTask.cancel(false);
        }
    }
}