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
package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.downstream.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import lombok.extern.slf4j.Slf4j;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static cern.c2mon.daq.opcua.exceptions.ConfigurationException.Cause.MISSING_TARGET_TAG;

/**
 * Regularly writes to a value in the OPC server to simulate an alive.
 *
 * @author Andreas Lang
 */

/**
 * OPC-UA has a built-in keep-alive functionality, sending keep-alive messages to the client's subscription
 * listener @see{@link cern.c2mon.daq.opcua.downstream.EndpointSubscriptionListener}  when there are no monitored items to send to the client to indicate that the server is active.
 * The only way that an active and initialized client does not receive this keep-alive is if it doesn't have any active
 * subscriptions and monitored items.
 *
 * TODO: Should we keep the AliveWriter for these cases, i.e. to stimulate an alive of the client to the server for
 * clients that don't have any active subscriptions?
 */

@Slf4j
public class AliveWriter extends TimerTask {
  /**
   * The timer used to schedule the writing.
   */
  private Timer timer;
  /**
   * The endpoint to write to.
   */
  private final Endpoint endpoint;
  /**
   * The time between two write operations.
   */
  private final long writeTime;
  /**
   * The tag which represents the target to write to.
   */
  private final ISourceDataTag targetTag;
  /**
   * The value to write (used like a counter).
   */
  private AtomicInteger writeCounter = new AtomicInteger(0);

  /**
   * Creates a new alive writer.
   *
   * @param endpoint   The endpoint to write to.
   * @param writeTime  The time between two write operations.
   * @param targetTag  The tag which represents the value to write to.
   */
  public AliveWriter(final Endpoint endpoint, final long writeTime, final ISourceDataTag targetTag) throws ConfigurationException {
    if (targetTag == null) {
      throw new ConfigurationException(MISSING_TARGET_TAG);
    }

    this.endpoint = endpoint;
    this.writeTime = writeTime;
    this.targetTag = targetTag;
  }

  /**
   * Implementation of the run method. Writes once to the server and increases
   * the write value.
   */
  @Override
  public void run() {
    OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) targetTag.getHardwareAddress();

    // We send an Integer since Long could cause problems to the OPC
    Object castedValue = writeCounter.intValue();
    if (log.isDebugEnabled()) { log.debug("Writing value: " + castedValue + " type: " + castedValue.getClass().getName()); }
    try {
      endpoint.write(hardwareAddress, castedValue);
      writeCounter.incrementAndGet();
      writeCounter.compareAndSet(Byte.MAX_VALUE, 0);
    }
    catch (OPCCommunicationException exception) {
      log.error("Error while writing alive. Going to retry...", exception);
    }
    catch (OPCCriticalException exception) {
      log.error("Critical error while writing alive. Stopping alive...", exception);
      synchronized (this) {
        cancel();
      }
    }
  }

  /**
   * Starts this writer.
   */
  public synchronized void startWriter() {
    if (timer != null) {
      timer.cancel();
    }
    timer = new Timer("OPCAliveWriter");
    log.info("Starting OPCAliveWriter...");
    timer.schedule(this, writeTime, writeTime);
  }

  /**
   * Stops this writer.
   */
  public synchronized void stopWriter() {
    if (timer != null) {
      log.info("Stopping OPCAliveWriter...");
      timer.cancel();
      timer = null;
    }
  }

}
