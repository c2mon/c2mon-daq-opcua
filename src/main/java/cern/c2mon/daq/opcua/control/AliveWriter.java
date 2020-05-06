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
package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.process.IEquipmentConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  /**
   * The endpoint to write to.
   */
  @Autowired
  private final Controller controller;

  /**
   * The service to schedule periodically to write to the alive tag hardware address
   */
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

  /**
   * The task of writing the alive value to the equipment.
   */
  private ScheduledFuture<?> writeAliveTask;

  /**
   * The time between two write operations.
   */
  private long writeTime;

  /**
   * The address to write to, represents the AliveTag.
   */
  private OPCHardwareAddress address;

  /**
   * Flag indicating whether the aliveWriter is enabled.
   */
  private boolean enabled = false;

  /**
   * The value to write (used like a counter).
   */
  private final AtomicInteger writeCounter = new AtomicInteger(0);

  /**
   * Initializes the AliveWriter considering the given equipment Configuration. If no the writer is not enabled or no
   * alive tag is specified, the aliveWriter is not started.
   *
   * @param config  the equipment configuration
   * @param enabled a flag indicating whether the AliveWriter should be started.
   */
  public void initialize(IEquipmentConfiguration config, boolean enabled) {
    if (enabled) {
      final ISourceDataTag aliveTag = config.getSourceDataTag(config.getAliveTagId());
      if (aliveTag != null) {
        this.enabled = true;
        this.writeTime = config.getAliveTagInterval() / 2;
        this.address = (OPCHardwareAddress) aliveTag.getHardwareAddress();
        startWriter();
      } else {
        log.error("The target tag is not defined, cannot start the Alive Writer.");
      }
    }
  }

  /**
   * Restarts the AliveWriter, if it was enabled on initialization.
   * @return a report describing the triggered restart, or the disabled state of the aliveWriter.
   */
  public String updateAndReport() {
    if (enabled) {
      startWriter();
      return "Alive Writer updated";
    } else {
      return "Alive Writer is not active and therefore was not updated.";
    }
  }

  public synchronized void startWriter() {
    stopWriter();
    log.info("Starting OPCAliveWriter...");
    writeAliveTask = executor.scheduleAtFixedRate(this::sendAlive, writeTime, writeTime, TimeUnit.MILLISECONDS);
  }

  public synchronized void stopWriter() {
    if (writeAliveTask != null) {
      log.info("Stopping OPCAliveWriter...");
      writeAliveTask.cancel(false);
      writeAliveTask = null;
    }
  }

  /**
   * Writes once to the server and increase the write value.
   */
  private void sendAlive() {
    // We send an Integer since Long could cause problems to the OPC
    Object castedValue = writeCounter.intValue();
    if (log.isDebugEnabled()) {
      log.debug("Writing value: " + castedValue + " type: " + castedValue.getClass().getName());
    }
    try {
      controller.writeAlive(address, castedValue);
      writeCounter.incrementAndGet();
      writeCounter.compareAndSet(Byte.MAX_VALUE, 0);
    } catch (OPCCommunicationException exception) {
      log.error("Error while writing alive. Going to retry...", exception);
    } catch (OPCCriticalException exception) {
      log.error("Critical error while writing alive. Stopping alive...", exception);
      stopWriter();
    }
  }
}