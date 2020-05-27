package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.AppConfig;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.ISourceDataTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("errorDelegate")
@RequiredArgsConstructor
@Slf4j
public class ControlDelegate {

    private final AppConfig config;

    private final Controller controller;

    private final CommandRunner commandRunner;

    /**
     * Subscribe to an OPC UA server of the given URI and subscribe to the dataTags. If the connection is unsuccessful,
     *
     * @param uri      the URI of the OPC UA server to connect to
     * @param dataTags the tags representing OPC UA node IDs on the server to subscribe to
     * @throws ConfigurationException if it is not possible to connect to any of the the OPC UA server's endpoints with
     *                                the given authentication configuration settings, or if there are no data tags to
     *                                subscribe to.
     */
    public synchronized void initialize(String uri, Collection<ISourceDataTag> dataTags) throws ConfigurationException {
        run(() -> controller.connect(uri), config.isFailFast());
        try {
            run(() -> controller.subscribeTags(dataTags), config.isFailFast());
        } catch (ConfigurationException e) {
            if (config.isFailFast()) {
                throw e;
            } else {
                log.error("Proceeding without subscribing any data tags.", e);
            }
        }
    }

    public synchronized void stop() {
        try {
            run(controller::stop, false);
        } catch (ConfigurationException e) {
            log.error("An error occurred when attempting to disconnect from the OPC data source", e);
        }
    }

    public synchronized void refreshAllDataTags() {
        try {
            run(controller::refreshAllDataTags, false);
        } catch (ConfigurationException e) {
            log.error("An error occurred when attempting to refresh all SourceDataTags", e);
        }
    }


    private synchronized void run(ThrowingRunnable r, boolean failFast) throws ConfigurationException {
        run(r, false, failFast);
    }

    private synchronized void run(ThrowingRunnable r, boolean isRetry, boolean failFast) throws ConfigurationException {
        try {
            r.run();
        } catch (CommunicationException e) {
            handleCommunicationException(e, isRetry, failFast);
            run(r, true, failFast);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            log.error("An unexpected exception ocurred.", e);
        }
    }

    private synchronized <S> S getSupplierResponse(ThrowingSupplier<S> r, boolean isRetry) throws Exception {
        try {
            return r.get();
        } catch (CommunicationException e) {
            handleCommunicationException(e, isRetry, false);
            return getSupplierResponse(r, true);
        }
    }

    private void handleCommunicationException(CommunicationException e, boolean isRetry, boolean failFast) throws ConfigurationException {
        if (isRetry || failFast) {
            log.error("Executing not successful even after a second attempt. Aborting!");
            throw new OPCCriticalException(e);
        } else if (CommunicationException.reconnectRequired(e) || !controller.isConnected()) {
            log.info("Reconnect and repeat");
            controller.reconnect();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws OPCUAException;
    }
}

