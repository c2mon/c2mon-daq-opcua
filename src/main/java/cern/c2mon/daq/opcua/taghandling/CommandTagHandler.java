package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.common.conf.equipment.ICommandTagChanger;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.IControllerProxy;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The CommandRunner identifies appropriate actions to take upon receiving an {@link ISourceCommandTag}s and executes
 * them on the {@link Endpoint}.
 */
@Component("commandRunner")
@Slf4j
@RequiredArgsConstructor
public class CommandTagHandler implements ICommandTagChanger {

    private final IControllerProxy controllerProxy;

    @Value("#{@appConfigProperties.getTimeout()}")
    private int timeout;

    /**
     * Called by the Core on a configuration change. Since required information is always fetched from the up-to-date
     * equipment configuration in {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}, this method only needs to return
     * success.
     * @param sourceCommandTag the new {@link ISourceCommandTag} that was added to the equipment configuration
     * @param changeReport     the ChangeReport wherein to report success.
     */
    @Override
    public void onAddCommandTag(ISourceCommandTag sourceCommandTag, ChangeReport changeReport) {
        handleCommandTagChanges(changeReport);
    }

    /**
     * Called by the Core on a configuration change. Since required information is always fetched from the up-to-date
     * equipment configuration in {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}, this method only needs to return
     * success.
     * @param sourceCommandTag the @link ISourceCommandTag} that was removed from the equipment configuration
     * @param changeReport     the ChangeReport wherein to report success.
     */
    @Override
    public void onRemoveCommandTag(ISourceCommandTag sourceCommandTag, ChangeReport changeReport) {
        handleCommandTagChanges(changeReport);
    }

    /**
     * Called by the Core on a configuration change. Since required information is always fetched from the up-to-date
     * equipment configuration in {@link cern.c2mon.daq.opcua.OPCUAMessageHandler}, this method only needs to return
     * success.
     * @param sourceCommandTag    the new {@link ISourceCommandTag} that was added to the equipment configuration
     * @param oldSourceCommandTag the old @link ISourceCommandTag} that was removed from the equipment configuration
     * @param changeReport        the ChangeReport wherein to report success.
     */
    @Override
    public void onUpdateCommandTag(ISourceCommandTag sourceCommandTag, ISourceCommandTag oldSourceCommandTag, ChangeReport changeReport) {
        handleCommandTagChanges(changeReport);
    }

    /**
     * Execute the action corresponding to a command tag, either by writing to a nodeId or by calling a method node on
     * the server. Retries e.g in case of connection issues are handled in {@link cern.c2mon.daq.common.messaging.impl.RequestController}.
     * @param tag     The commandTag to execute
     * @param command the value of the command that shall be executed
     * @return A joined String of method results, if the command is of type METHOD and the server returns any output
     * arguments. Else an empty String.
     * @throws EqCommandTagException if the command cannot be executed or the server returns an invalid status code.
     */
    public String runCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws EqCommandTagException {
        Object arg = command.getValue();
        if (arg != null) {
            arg = TypeConverter.cast(command.getValue(), command.getDataType());
            if (arg == null) {
                throw new EqCommandTagException(ExceptionContext.COMMAND_VALUE_ERROR.getMessage());
            }
        }
        try {
            final OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
            switch (hardwareAddress.getCommandType()) {
                case METHOD:
                    return Joiner.on(", ").join(executeMethod(tag, arg));
                case CLASSIC:
                    runClassicCommand(tag, arg, hardwareAddress.getCommandPulseLength());
                    break;
                default:
                    // impossible
                    throw new EqCommandTagException(ExceptionContext.COMMAND_TYPE_UNKNOWN.getMessage());
            }
        } catch (ConfigurationException e) {
            throw new EqCommandTagException("Please check whether the configuration is correct.", e);
        } catch (OPCUAException e) {
            throw new EqCommandTagException("A communication error occurred while running the command. ", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during command execution.");
        } catch (Exception e) {
            log.error("Exception", e);
        }
        return null;
    }

    /**
     * Executes the method associated with the tag. If the tag contains both a primary and a redundant item name, it is
     * assumed that the primary item name refers to the object node containing the methodId, and that the redundant item
     * name refers to the method node.
     * @param tag the command tag associated with a method node.
     * @param arg the input arguments to pass to the method.
     * @return an array of the method's output arguments. If there are none, an empty array is returned.
     */

    private Object[] executeMethod(ISourceCommandTag tag, Object arg) throws OPCUAException {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final Map.Entry<Boolean, Object[]> result = controllerProxy.callMethod(def, arg);
        log.info("executeMethod returned {}.", result.getValue());
        if (!result.getKey()) {
            throw new CommunicationException(ExceptionContext.METHOD_CODE);
        }
        return result.getValue();
    }


    private void runClassicCommand(ISourceCommandTag tag, Object arg, int pulse) throws OPCUAException, InterruptedException {
        if (pulse == 0) {
            log.info("Setting Tag with ID {} to {}.", tag.getId(), arg);
            executeWriteCommand(tag, arg);
        } else {
            final Object original = readOriginal(tag);
            if (original != null && original.equals(arg)) {
                log.info("Tag with ID {} is already set to {}. Skipping command with pulse.", tag.getId(), arg);
            } else {
                writeRewrite(tag, arg, original, pulse);
            }
        }
    }

    private void writeRewrite(ISourceCommandTag tag, Object arg, Object original, int pulse) throws OPCUAException {
        Runnable rewrite = () -> {
            log.info("Resetting Tag with ID {} to {}.", tag.getId(), original);
            try {
                executeWriteCommand(tag, original);
            } catch (OPCUAException e) {
                throw new CompletionException(e);
            }
        };
        log.info("Setting Tag with ID {} to {} for {} seconds.", tag.getId(), arg, pulse);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(rewrite, pulse, TimeUnit.SECONDS);
        executeWriteCommand(tag, arg);
        awaitAndShutdown(executor, pulse);

    }

    private void executeWriteCommand(ISourceCommandTag tag, Object arg) throws OPCUAException {
        if (!controllerProxy.write(ItemDefinition.toNodeId(tag), arg)) {
            throw new CommunicationException(ExceptionContext.COMMAND_CLASSIC);
        }
    }

    /**
     * Read the current value of the tag
     * @param tag The {@link ISourceCommandTag} whose current value shall be read
     * @return the current value of the {@link org.eclipse.milo.opcua.stack.core.types.builtin.NodeId} associated with
     * the tag
     * @throws OPCUAException if an exception occurred during the read operation, or if a bad quality reading was
     *                        obtained. {@link cern.c2mon.daq.common.messaging.impl.RequestController} assumes all
     *                        command executions that do not throw  an error to be successful. To ensure a reliable
     *                        result, repeat the execution by throwing an error upon status  codes that are invalid.
     */
    private Object readOriginal(ISourceCommandTag tag) throws OPCUAException {
        final Map.Entry<ValueUpdate, SourceDataTagQuality> read = controllerProxy.read(ItemDefinition.toNodeId(tag));
        log.info("Action completed with Status Code: {}", read.getValue());
        if (read.getValue() == null || !read.getValue().isValid()) {
            throw new CommunicationException(ExceptionContext.READ);
        }
        return read.getKey().getValue();
    }

    private void awaitAndShutdown(ScheduledExecutorService executor, int pulse) {
        executor.shutdown();
        try {
            long await = ((long) pulse) + timeout;
            if (!executor.awaitTermination(await, TimeUnit.SECONDS)) {
                log.error("Shutting down now");
                executor.shutdownNow();
                if (!executor.awaitTermination(await, TimeUnit.SECONDS)) {
                    log.error("Server switch still running");
                }
            }
        } catch (InterruptedException ie) {
            log.error("Interrupted... ", ie);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Executor shut down");
    }

    private void handleCommandTagChanges(ChangeReport changeReport) {
        changeReport.appendInfo("No action required.");
        changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
    }
}
