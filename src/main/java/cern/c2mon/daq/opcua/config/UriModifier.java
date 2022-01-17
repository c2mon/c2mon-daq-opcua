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
package cern.c2mon.daq.opcua.config;

import cern.c2mon.daq.opcua.scope.EquipmentScoped;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static cern.c2mon.daq.opcua.config.AppConfigProperties.HostSubstitutionMode;
import static cern.c2mon.daq.opcua.config.AppConfigProperties.PortSubstitutionMode;

/**
 * A utility class for modifying hostname and/or port of URIs either to values set in the {@link AppConfigProperties},
 * or to reflect another URI.
 */
@Slf4j
@RequiredArgsConstructor
@EquipmentScoped
public class UriModifier {
    private static final String HOST_REGEX = "^[a-z][a-z0-9+\\-.]*://([a-z0-9\\-._~%!$&'()*+,;=:]+@)?([a-z0-9\\-._~%]+|\\[[a-z0-9\\-._~%!$&'()*+,;=:]+])(:[0-9]+)?";
    private static final Pattern HOST_PATTERN = Pattern.compile(HOST_REGEX);

    private final AppConfigProperties config;

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URI by the one used to originally reach the
     * server to work around the issue.
     * @param reference the URI used as a reference for changing the localUri.
     * @param localUri  the URI with local hostnames to change as configured.
     * @return The URI updated according to the policies in config. If no modifications are configured or the URI could
     * not be modified, the localUri is returned without modification.
     */
    public String updateEndpointUrl(String reference, String localUri) {
        if (Stream.of(reference, localUri).anyMatch(StringUtil::isNullOrEmpty) ||
                (config.getPortSubstitutionMode().equals(PortSubstitutionMode.NONE) &&
                        config.getHostSubstitutionMode().equals(HostSubstitutionMode.NONE))) {
            return localUri;
        }

        // Some hostnames contain characters not allowed in a URI, such as underscores in Windows machine hostnames.
        // Therefore, parsing is done using a regular expression rather than relying on java URI class methods.
        // https://stackoverflow.com/questions/40554198/java-opc-ua-client-eclipse-milo-endpoint-url-changes-to-localhost
        final Matcher localMatcher = HOST_PATTERN.matcher(localUri);
        if (!localMatcher.find()) {
            log.info("URI {} could not be processed. Does it follow the propper syntax?", localUri);
            return localUri;
        }
        final Matcher discoveryMatcher = HOST_PATTERN.matcher(reference);
        String globalizedUri = substituteHost(localUri, localMatcher, discoveryMatcher);
        localMatcher.reset();
        discoveryMatcher.reset();
        globalizedUri = substitutePort(globalizedUri, localMatcher, discoveryMatcher);
        log.info("Changed URI {} to {} as configured.", localUri, globalizedUri);
        return globalizedUri;
    }

    private String substituteHost(String uri, Matcher localMatcher, Matcher discoveryMatcher) {
        final HostSubstitutionMode hostMode = config.getHostSubstitutionMode();
        if (hostMode.equals(HostSubstitutionMode.NONE)) {
            return uri;
        }
        if (hostMode.global && StringUtil.isNullOrEmpty(config.getGlobalHostName())) {
            log.info("The global hostName is not set. Skipping modification.");
            return uri;
        } else if (!hostMode.global && !discoveryMatcher.find()) {
            log.info("Cannot find a host to substitute in the discovery URI. Skipping modification.");
            return uri;
        }
        String newHost = hostMode.global ? config.getGlobalHostName() : discoveryMatcher.group(2);
        if (hostMode.substitute) {
            return uri.replace(localMatcher.group(2), newHost);
        } else {
            final int idx = localMatcher.end(2);
            return uri.substring(0, idx) + "." + newHost + uri.substring(idx);
        }
    }

    private String substitutePort(String localUri, Matcher localMatcher, Matcher discoveryMatcher) {
        final PortSubstitutionMode portMode = config.getPortSubstitutionMode();
        if (!portMode.equals(PortSubstitutionMode.NONE)) {
            String newPort = ":" + config.getGlobalPort();
            if (portMode.equals(PortSubstitutionMode.LOCAL) && (!discoveryMatcher.find() || discoveryMatcher.group(3) == null)) {
                log.info("Cannot find a port to substitute in the discovery URI. Skipping modification.");
                return localUri;
            } else if (portMode.equals(PortSubstitutionMode.LOCAL)) {
                newPort = discoveryMatcher.group(3);
            }
            if (localMatcher.find() && localMatcher.group(3) != null) {
                return localUri.replace(localMatcher.group(3), newPort);
            } else {
                final int idx = localMatcher.end(2);
                return localUri.substring(0, idx) + newPort + localUri.substring(idx);
            }
        }
        return localUri;
    }
}
