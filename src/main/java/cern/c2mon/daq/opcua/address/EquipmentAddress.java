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

import cern.c2mon.daq.opcua.control.AliveWriter;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import lombok.Getter;
import lombok.NonNull;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 *  POJO holding convenient access to relevant information of the equipment address.
 */
@Getter
public class EquipmentAddress {

    private final List<ServerAddress> addresses;
    private final int serverTimeout;
    private final int serverRetryTimeout;
    /**
     * If, set to false, then the Alive WriterTask is not started. The OPC has then to update itself the equipment alive
     * tag, otherwise the C2MON server will invalidate all tags from this process because of an alive timer
     * expiration.<p> The default value is <code>true</code>.
     * @see AliveWriter
     */
    private final boolean aliveWriterEnabled;

    public EquipmentAddress(List<ServerAddress> addresses, int serverTimeout, int serverRetryTimeout, boolean aliveWriterEnabled) throws ConfigurationException {
        if (addresses.isEmpty())
            throw new ConfigurationException(ExceptionContext.MISSING_URI);
        this.addresses = addresses;
        this.serverTimeout = serverTimeout;
        this.serverRetryTimeout = serverRetryTimeout;
        this.aliveWriterEnabled = aliveWriterEnabled;
    }

    public ServerAddress getServerAddressOfType(String protocol) throws ConfigurationException {
        for (ServerAddress address : addresses) {
            if (address.getProtocol().equals(protocol)) {
                return address;
            }
        }
        throw new ConfigurationException(ExceptionContext.ENDPOINT_TYPES_UNKNOWN);
    }

    public boolean supportsProtocol(String uri) {
        for (ServerAddress address : addresses) {
            if (address.getProtocol().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAddresses(), getServerTimeout(), getServerRetryTimeout(), isAliveWriterEnabled());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentAddress)) return false;
        EquipmentAddress that = (EquipmentAddress) o;
        return getServerTimeout() == that.getServerTimeout() &&
                getServerRetryTimeout() == that.getServerRetryTimeout() &&
                isAliveWriterEnabled() == that.isAliveWriterEnabled() &&
                getAddresses().equals(that.getAddresses());
    }

    @Getter
    public static class ServerAddress {
        private final URI uri;
        private final String user;
        private final String domain;
        private final String password;

        ServerAddress(@NonNull URI uri) {
            this.uri = uri;
            this.user = null;
            this.domain = null;
            this.password = null;
        }

        ServerAddress(@NonNull URI uri, String user, String domain, String password) {
            this.uri = uri;
            this.user = cleanArg(user);
            this.domain = cleanArg(domain);
            this.password = cleanArg(password);
        }

        private String cleanArg(String arg) {
            return arg == null || arg.trim().isEmpty() ? null : arg;
        }

        public String getProtocol() {
            return uri.getScheme();
        }

        @Override
        public int hashCode() {
            return Objects.hash(getUriString(), user, domain, password);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServerAddress that = (ServerAddress) o;
            return Objects.equals(getUriString(), that.getUriString()) &&
                    Objects.equals(user, that.user) &&
                    Objects.equals(domain, that.domain) &&
                    Objects.equals(password, that.password);
        }

        public String getUriString() {
            return uri.toString();
        }
    }
}