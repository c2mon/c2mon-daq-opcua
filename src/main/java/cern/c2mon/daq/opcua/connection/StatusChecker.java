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

import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;

import java.util.TimerTask;

public abstract class StatusChecker extends TimerTask {
    
    private Endpoint endpoint;
    
    public StatusChecker(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void run() {
        try {
            endpoint.isConnected();
        } catch (OPCCommunicationException e) {
            onOPCCommunicationException(endpoint, e);
        } catch (OPCCriticalException e) {
            onOPCCriticalException(endpoint, e);
        } catch (Exception e) {
            onOPCUnknownException(endpoint, e);
        }

    }

    public abstract void onOPCUnknownException(
            final Endpoint endpoint, final Exception e);


    public abstract void onOPCCriticalException(
            final Endpoint endpoint, final OPCCriticalException e);


    public abstract void onOPCCommunicationException(
            final Endpoint endpoint, final OPCCommunicationException e);

}
