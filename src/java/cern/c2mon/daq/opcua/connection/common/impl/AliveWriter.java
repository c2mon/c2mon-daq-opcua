package cern.c2mon.daq.opcua.connection.common.impl;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import cern.c2mon.daq.opcua.connection.common.IOPCEndpoint;
import cern.c2mon.daq.common.logger.EquipmentLogger;
import cern.c2mon.daq.common.logger.EquipmentLoggerFactory;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;

/**
 * Regularly writes to a value in the OPC server to simulate an alive.
 * 
 * @author Andreas Lang
 * 
 */
public class AliveWriter extends TimerTask {
    /**
     * The timer used to schedule the writing.
     */
    private Timer timer;
    /**
     * The endpoint to write to.
     */
    private final IOPCEndpoint endpoint;
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
     * The equipmentLogger for this alive writer.
     */
    private EquipmentLogger equipmentLogger;

    /**
     * Creates a new alive writer.
     * 
     * @param endpoint
     *            The endpoint to write to.
     * @param writeTime
     *            The time between two write operations.
     * @param targetTag
     *            The tag which represents the value to write to.
     * @param equipmentLoggerFactory
     *            for creating the proper equipment loggers EquipmentLoggerFactory 
     */
    public AliveWriter(final IOPCEndpoint endpoint, final long writeTime, 
            final ISourceDataTag targetTag, final EquipmentLoggerFactory equipmentLoggerFactory) {
        this.equipmentLogger = equipmentLoggerFactory.getEquipmentLogger(getClass());
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
        OPCHardwareAddress hardwareAddress =
            (OPCHardwareAddress) targetTag.getHardwareAddress();
        // We send an Integer since Long could cause problems to the OPC
        Object castedValue = Integer.valueOf(writeCounter.intValue());
        equipmentLogger.debug("Writing value: " + castedValue 
                + " type: " + castedValue.getClass().getName());
        try {
            endpoint.write(hardwareAddress, castedValue);
            writeCounter.incrementAndGet();
            writeCounter.compareAndSet(Byte.MAX_VALUE, 0);
        } catch (OPCCommunicationException exception) {
            equipmentLogger.error("Error while writing alive. Going to retry...", exception);
        } catch (OPCCriticalException exception) {
            equipmentLogger.error("Critical error while writing alive. Stopping alive...", exception);
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
        equipmentLogger.info("Starting OPCAliveWriter...");
        timer.schedule(this, writeTime, writeTime);
    }

    /**
     * Stops this writer.
     */
    public synchronized void stopWriter() {
        if (timer != null) {
            equipmentLogger.info("Stopping OPCAliveWriter...");
            timer.cancel();
            timer = null;
        }
    }

}
