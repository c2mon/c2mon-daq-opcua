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

package cern.c2mon.daq.opcua.address;

import cern.c2mon.daq.opcua.connection.AliveWriter;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@Getter
@AllArgsConstructor
public class EquipmentAddress {

    private URI uri;
    private String user;
    private String domain;
    private String password;
    private int serverTimeout;
    private int serverRetryTimeout;

    /**
     * If, set to false, then the Alive WriterTask is not started.
     * The OPC has then to update itself the equipment alive tag,
     * otherwise the C2MON server will invalidate all tags from this
     * process because of an alive timer expiration.<p>
     * The default value is <code>true</code>.
     * @see AliveWriter
     */
    private boolean aliveWriterEnabled;

    public EquipmentAddress(String uri, int serverTimeout, int serverRetryTimeout) throws URISyntaxException {
        this.uri = new URI(uri);
        this.serverTimeout = serverTimeout;
        this.serverRetryTimeout = serverRetryTimeout;
    }

    public EquipmentAddress(String uri,
                            String user,
                            String domain,
                            String password,
                            int serverTimeout,
                            int serverRetryTimeout,
                            boolean aliveWriterEnabled) throws URISyntaxException {
        this.uri = new URI(uri);
        this.user = user;
        this.domain = domain;
        this.password = password;
        this.serverTimeout = serverTimeout;
        this.serverRetryTimeout = serverRetryTimeout;
        this.aliveWriterEnabled = aliveWriterEnabled;
    }

    /**
     * @return the Uri as a String
     */
    public String getUriString() {
        return uri.toString();
    }

    /**
     * @return the protocol
     */
    public String getProtocol() {
        return uri.getScheme();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EquipmentAddress that = (EquipmentAddress) o;
        return Objects.equals(getUri(), that.getUri()) &&
                getServerTimeout() == that.getServerTimeout() &&
                getServerRetryTimeout() == that.getServerRetryTimeout() &&
                isAliveWriterEnabled() == that.isAliveWriterEnabled() &&
                Objects.equals(getUser(), that.getUser()) &&
                Objects.equals(getPassword(), that.getPassword()) &&
                Objects.equals(getDomain(), that.getDomain());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri(), getServerTimeout(), getServerRetryTimeout(), getUser(), getPassword());
    }
}