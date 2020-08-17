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
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
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
public class AliveWriter {

    private final Controller controllerProxy;
    private final MessageSender messageSender;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private final AtomicInteger writeCounter = new AtomicInteger(0);
    private ScheduledFuture<?> writeAliveTask;
    private long writeTime;
    private NodeId aliveTagAddress;

    /**
     * Initializes the AliveWriter considering the given equipment Configuration. If no the writer is not enabled or no
     * alive tag is specified, the aliveWriter is not started.
     * @param aliveTag  the DataTag containing the Node acting as AliveTag
     * @param aliveTagInterval the interval in which to write to the aliveTag
     */
    public void initialize(ISourceDataTag aliveTag, long aliveTagInterval) {
        writeTime = aliveTagInterval;
        try {
            final ItemDefinition def = ItemDefinition.of(aliveTag);
            aliveTagAddress = def.getNodeId();
            startWriter();
        } catch (ConfigurationException e) {
            log.error("The AliveTag has an incorrect hardware address and cannot be started", e);
        }
    }

    /**
     * Stops the aliveWriter execution.
     */
    public void stopWriter() {
        if (writeAliveTask != null && (!writeAliveTask.isCancelled() || !writeAliveTask.isDone())) {
            log.info("Stopping OPCAliveWriter...");
            writeAliveTask.cancel(false);
        }
    }

    private void startWriter() {
        stopWriter();
        log.info("Starting OPCAliveWriter...");
        writeAliveTask = executor.scheduleAtFixedRate(this::sendAlive, writeTime, writeTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Writes once to the server and increase the write value.
     */
    private void sendAlive() {
        try {
            if (controllerProxy.write(aliveTagAddress, writeCounter.intValue())) {
                log.info("Written successfully to alive, sending AliveTag update.");
                messageSender.onAlive();
            }
            writeCounter.incrementAndGet();
            writeCounter.compareAndSet(Byte.MAX_VALUE, 0);
        } catch (OPCUAException e) {
            log.error("Error while writing alive. Retrying...", e);
        }
    }
}