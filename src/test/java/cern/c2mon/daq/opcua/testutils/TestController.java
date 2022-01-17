/*-
 * #%L
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * %%
 * Copyright (C) 2010 - 2022 CERN
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
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
        super(properties, new AppConfig().alwaysRetryTemplate(properties));
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
