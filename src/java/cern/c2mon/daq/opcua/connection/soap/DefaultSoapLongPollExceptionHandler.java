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
package cern.c2mon.daq.opcua.connection.soap;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.c2mon.daq.opcua.connection.common.impl.OPCCommunicationException;
import cern.c2mon.daq.opcua.connection.common.impl.SubscriptionGroup;
/**
 * Default way to handle excpetions in the long poll mechanism. It just restarts
 * it after a certain time.
 * 
 * @author Andreas Lang
 *
 */
public class DefaultSoapLongPollExceptionHandler 
        implements ISoapLongPollExceptionHandler {
    
    /**
     * Logger of this class.
     */
    private static final Logger logger =
        LoggerFactory.getLogger(DefaultSoapLongPollExceptionHandler.class);

    /**
     * The default delay before the restart is done in ms.
     */
    public static final long DEFAULT_RESTART_DELAY = 2000L;
    
    /**
     * The maximum errors in ERROR_ALIVE_FACTOR * restart delay
     */
    private static final int MAX_ERRORS = 5;

    /**
     * The timer used to schedule the restart.
     */
    private final Timer timer = new Timer(true);
    
    /**
     * The endpoint this handler belongs to.
     */
    private final DASoapEndpoint endpoint;

    /**
     * The delay for the restart.
     */
    private long restartDelay;
    
    /**
     * Counter for errors.
     * 
     * Errors stay in the counter for ERROR_ALIVE_FACTOR * restartDelay.
     */
    private AtomicInteger errorCounter = new AtomicInteger(0);

    /**
     * Subscription group for this poll.
     */
    private SubscriptionGroup<DASoapItemDefintion> group;

    /**
     * Creates a new DefaultLongPollExceptionHandler
     * with the provided restart delay.
     * 
     * @param endpoint The endpoint of this handler.
     * @param group The group for this poll.
     * @param restartDelay The restart delay.
     */
    public DefaultSoapLongPollExceptionHandler(
            final DASoapEndpoint endpoint,
            final SubscriptionGroup<DASoapItemDefintion> group,
            final long restartDelay) {
        this.group = group;
        this.restartDelay = restartDelay;
        this.endpoint = endpoint;
    }

    /**
     * Creates a new DefaultLongPollExceptionHandler
     * with the default restart delay. See DEFAULT_RESTART_DELAY.
     * 
     * @param endpoint The endpoint of this handler.
     */
    public DefaultSoapLongPollExceptionHandler(
            final DASoapEndpoint endpoint,
            final SubscriptionGroup<DASoapItemDefintion> group) {
        this(endpoint, group, DEFAULT_RESTART_DELAY);
    }

    /**
     * Handles the connection exception by just restarting the poll.
     * 
     * @param t The occurred exception.
     * @param poll The poll in which the exception happened.
     */
    @Override
    public synchronized void onConnectionException(
            final Throwable t, final SoapLongPoll poll) {
        logger.error("Polling exception: ", t);
        errorCounter.incrementAndGet();
        if (!toMuchErrors()) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    endpoint.onSubscribe(group);
                }
            }, restartDelay);
        }
        else {
            timer.cancel();
            logger.error("Too many exceptions in subscription.");
            endpoint.notifyEndpointListenersSubscriptionFailed(
                    new OPCCommunicationException("Subscription failed restarting."));
        }
    }

    /**
     * Checks if there are too much errors.
     * 
     * @return True if the amount of errors exceeds the normal rate else false.
     */
    private boolean toMuchErrors() {
        return (errorCounter.get() > MAX_ERRORS);
    }
    
}
