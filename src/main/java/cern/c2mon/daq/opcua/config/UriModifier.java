package cern.c2mon.daq.opcua.config;

import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static cern.c2mon.daq.opcua.config.AppConfigProperties.PortSubstitutionMode;
import static cern.c2mon.daq.opcua.config.AppConfigProperties.UriSubstitutionMode;

@Slf4j
@Component("uriModifier")
@RequiredArgsConstructor
public class UriModifier {
    private static final String HOST_REGEX = "^[a-z][a-z0-9+\\-.]*://([a-z0-9\\-._~%!$&'()*+,;=:]+@)?([a-z0-9\\-._~%]+|\\[[a-z0-9\\-._~%!$&'()*+,;=:]+])(:[0-9]+)?";
    private static final Pattern HOST_PATTERN = Pattern.compile(HOST_REGEX);

    private final AppConfigProperties config;

    /**
     * There is a common misconfiguration in OPC UA servers to return a local hostname in the endpointUrl that can not
     * be resolved by the client. Replace the hostname of each endpoint's URI by the one used to originally reach the
     * server to work around the issue.
     * @param localUri the URI with local hostnames to change as configured.
     * @return The URI updated according to the policies in config. If no modifications are configured or the URI could
     * not be modified, the localUri is returned without modification.
     */
    public String updateEndpointUrl(String discoveryUri, String localUri) {
        if (Stream.of(discoveryUri, localUri).anyMatch(StringUtil::isNullOrEmpty) ||
                (config.getPortSubstitutionMode().equals(PortSubstitutionMode.NONE) &&
                        config.getHostSubstitutionMode().equals(UriSubstitutionMode.NONE))) {
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
        final Matcher discoveryMatcher = HOST_PATTERN.matcher(discoveryUri);
        String globalizedUri = substituteHost(localUri, localMatcher, discoveryMatcher);
        localMatcher.reset();
        discoveryMatcher.reset();
        globalizedUri = substitutePort(globalizedUri, localMatcher, discoveryMatcher);
        log.info("Changed URI {} to {} as configured.", localUri, globalizedUri);
        return globalizedUri;
    }

    private String substituteHost(String uri, Matcher localMatcher, Matcher discoveryMatcher) {
        final UriSubstitutionMode hostMode = config.getHostSubstitutionMode();
        if (hostMode.equals(UriSubstitutionMode.NONE)) {
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
            if (!portMode.global && (!discoveryMatcher.find() || discoveryMatcher.group(3) == null)) {
                log.info("Cannot find a port to substitute in the discovery URI. Skipping modification.");
                return localUri;
            } else if (!portMode.global) {
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