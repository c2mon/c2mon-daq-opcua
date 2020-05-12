package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.common.conf.equipment.IDataTagChanger;
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
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.FAIL;
import static cern.c2mon.shared.daq.config.ChangeReport.CHANGE_STATE.SUCCESS;

@Component("errorDelegate")
@RequiredArgsConstructor
@Slf4j
public class ControlDelegate implements IDataTagChanger {

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

    public synchronized void refreshDataTag(ISourceDataTag tag) {
        try {
            run(() -> controller.refreshDataTag(tag), false);
        } catch (ConfigurationException e) {
            log.error("An error occurred when attempting to refresh SourceDataTag with ID {}.", tag.getId(), e);
        }
    }

    @Override
    public void onAddDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        getAndReport(() -> {
            final var s = controller.subscribeTag(sourceDataTag);
            refreshDataTag(sourceDataTag);
            return s;
        }, changeReport);
    }

    @Override
    public void onRemoveDataTag(final ISourceDataTag sourceDataTag, final ChangeReport changeReport) {
        getAndReport(() -> controller.removeTag(sourceDataTag), changeReport);
    }

    @Override
    public void onUpdateDataTag(final ISourceDataTag sourceDataTag, final ISourceDataTag oldSourceDataTag, final ChangeReport changeReport) {
        if (sourceDataTag.getHardwareAddress().equals(oldSourceDataTag.getHardwareAddress())) {
            completeSuccessFullyWithMessage(changeReport, "The new and old sourceDataTags have the same hardware address, no update was required.");
        } else {
            getAndReport(() -> {
                final var s = controller.removeTag(oldSourceDataTag) + controller.subscribeTag(sourceDataTag);
                refreshDataTag(sourceDataTag);
                return s;
            }, changeReport);
        }
    }

    /**
     * Execute the action corresponding to a command tag, either by writing to a nodeId or by calling a method node on
     * the server.
     *
     * @param tag     The commandTag to execute
     * @param command the value of the command that shall be executed
     * @return A joined String of method results, if the command is of type METHOD and the server returns any output
     * arguments. Else an empty String.
     * @throws EqCommandTagException if the command cannot be executed
     */
    public String runCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws EqCommandTagException {
        Object arg = TypeConverter.cast(command.getValue(), command.getDataType());
        if (arg == null) {
            throw new EqCommandTagException(ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
        }
        try {
            return runCommand(tag, arg);
        } catch (Exception e) {
            final var msg = (e instanceof ConfigurationException) ?
                    "Please check whether the configuration is correct." :
                    "An unexpected error occurred.";
            throw new EqCommandTagException(msg, e);
        }
    }

    private String runCommand(ISourceCommandTag tag, Object arg) throws Exception {
        final var hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
        switch (hardwareAddress.getCommandType()) {
            case METHOD:
                final var s = Stream.of(getSupplierResponse(() -> commandRunner.executeMethod(tag, arg)))
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                log.info("Successfully executed method {} with arg {} and result {}", tag, arg, s);
                return s;
            case CLASSIC:
                runClassicCommand(tag, arg, hardwareAddress.getCommandPulseLength());
                break;
            default:
                throw new ConfigurationException(ConfigurationException.Cause.COMMAND_TYPE_UNKNOWN);
        }
        return null;
    }

    private void runClassicCommand(ISourceCommandTag tag, Object arg, int pulse) throws Exception {
        if (pulse == 0) {
            run(() -> commandRunner.executeCommand(tag, arg), config.isFailFast());
        } else {
            final var original = getSupplierResponse(() -> commandRunner.getOriginal(tag));
            if (original != null && original.equals(arg)) {
                log.info("Tag with ID {} is already set to {}. Skipping command with pulse.", tag.getId(), arg);
                return;
            }
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            final ScheduledFuture<?> f = scheduler.schedule(() -> {
                try {
                    run(() -> commandRunner.executeCommand(tag, original, true), config.isFailFast());
                } catch (ConfigurationException e) {
                    // the RuntimeException will be caught in runCommand(), therefore safe to use here.
                    throw new RuntimeException(e);
                }
            }, pulse, TimeUnit.SECONDS);
            run(() -> commandRunner.executeCommand(tag, arg), config.isFailFast());
            f.get();
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

    private synchronized void getAndReport(ThrowingSupplier<String> r, ChangeReport changeReport) {
        try {
            completeSuccessFullyWithMessage(changeReport, getSupplierResponse(r));
        } catch (Exception e) {
            log.error("An unexpected exception ocurred: ", e);
            changeReport.appendError(e.getMessage());
            changeReport.setState(FAIL);
        }
    }

    private synchronized <S> S getSupplierResponse(ThrowingSupplier<S> r) throws Exception {
        return getSupplierResponse(r, false);
    }

    private synchronized <S> S getSupplierResponse(ThrowingSupplier<S> r, boolean isRetry) throws Exception {
        try {
            return r.get();
        } catch (CommunicationException e) {
            handleCommunicationException(e, isRetry, false);
            return getSupplierResponse(r, true);
        }
    }

    private void completeSuccessFullyWithMessage(ChangeReport changeReport, String message) {
        changeReport.appendInfo(message);
        changeReport.setState(SUCCESS);
    }

    private void handleCommunicationException(CommunicationException e, boolean isRetry, boolean failFast) throws ConfigurationException {
        if (isRetry || failFast) {
            log.error("Executing not successful even after a second attempt. Aborting!");
            throw new OPCCriticalException(e);
        } else if (CommunicationException.reconnectRequired(e) || !controller.isConnected()) {
            try {
                log.info("Reconnect and repeat");
                controller.reconnect();
            } catch (CommunicationException ex) {
                log.error("Reconnection unsuccessful. Aborting!");
                throw new OPCCriticalException(e);
            }
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

