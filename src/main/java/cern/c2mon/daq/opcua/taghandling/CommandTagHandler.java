package cern.c2mon.daq.opcua.taghandling;

import cern.c2mon.daq.common.conf.equipment.ICommandTagChanger;
import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.control.Controller;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import cern.c2mon.shared.common.datatag.address.OPCCommandHardwareAddress;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import cern.c2mon.shared.daq.config.ChangeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The CommandRunner identifies appropriate actions to take upon receiving an {@link ISourceCommandTag}s and executes
 * them on the {@link Endpoint}.
 */
@Component("commandTagHandler")
@Slf4j
@RequiredArgsConstructor
public class CommandTagHandler implements ICommandTagChanger {

    private final Controller controller;

    @Value("#{@appConfigProperties.getRequestTimeout()}")
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
        if (!(tag.getHardwareAddress() instanceof OPCHardwareAddress)) {
            throw new EqCommandTagException(ExceptionContext.HARDWARE_ADDRESS_TYPE.getMessage());
        }
        Object arg = command.getValue();
        if (arg != null) {
            arg = TypeConverter.cast(command.getValue(), command.getDataType());
            if (arg == null) {
                throw new EqCommandTagException(ExceptionContext.COMMAND_VALUE_ERROR.getMessage());
            }
        }
        final OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
        try {
            if (hardwareAddress.getCommandType().equals(OPCCommandHardwareAddress.COMMAND_TYPE.METHOD)) {
                return Arrays.stream(executeMethod(tag, arg))
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
            } else {
                runClassicCommand(tag, arg, hardwareAddress.getCommandPulseLength());
            }
        } catch (Exception e) {
            throw new EqCommandTagException("An exception occurred while running the command.", e);
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
        final Map.Entry<Boolean, Object[]> result = controller.callMethod(def, arg);
        log.info("executeMethod returned {}.", result.getValue());
        if (!result.getKey()) {
            throw new CommunicationException(ExceptionContext.METHOD_CODE);
        }
        return result.getValue();
    }


    private void runClassicCommand(ISourceCommandTag tag, Object arg, int pulse) throws OPCUAException {
        final NodeId nodeId = ItemDefinition.getNodeIdForTag(tag);
        if (pulse == 0) {
            log.info("Setting Tag with ID {} to {}.", tag.getId(), arg);
            executeWriteCommand(nodeId, arg);
        } else {
            final Object original = readOriginal(nodeId);
            if (original.equals(arg)) {
                log.info("Tag with ID {} is already set to {}. Skipping command with pulse.", tag.getId(), arg);
            } else {
                writeRewrite(nodeId, tag.getId(), arg, original, pulse);
            }
        }
    }

    private void writeRewrite(NodeId nodeId, long tagId, Object arg, Object original, int pulse) throws OPCUAException {
        log.info("Setting Tag with ID {} to {} for {} seconds.", tagId, arg, pulse);
        executeWriteCommand(nodeId, arg);
        try {
            TimeUnit.SECONDS.sleep(pulse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Failed with exception ", e);
            log.info("Interrupted pulse, continue resetting the tag.");
        }
        log.info("Resetting Tag with ID {} to {}.", tagId, original);
        executeWriteCommand(nodeId, original);
    }

    private void executeWriteCommand(NodeId nodeId, Object arg) throws OPCUAException {
        if (!controller.write(nodeId, arg)) {
            throw new CommunicationException(ExceptionContext.COMMAND_CLASSIC);
        }
    }

    /**
     * Read the current value of the tag
     * @param nodeId The nodeId referencing the {@link ISourceCommandTag} whose current value shall be read
     * @return the current value of the {@link org.eclipse.milo.opcua.stack.core.types.builtin.NodeId} associated with
     * the tag
     * @throws OPCUAException if an exception occurred during the read operation, or if a bad quality reading was
     *                        obtained. {@link cern.c2mon.daq.common.messaging.impl.RequestController} assumes all
     *                        command executions that do not throw  an error to be successful. To ensure a reliable
     *                        result, repeat the execution by throwing an error upon status  codes that are invalid.
     */
    private Object readOriginal(NodeId nodeId) throws OPCUAException {
        final Map.Entry<ValueUpdate, SourceDataTagQuality> read = controller.read(nodeId);
        log.info("Action completed with Status Code: {}", read.getValue());
        if (read.getValue() == null || !read.getValue().isValid()) {
            throw new CommunicationException(ExceptionContext.READ);
        }
        return read.getKey().getValue();
    }

    private void handleCommandTagChanges(ChangeReport changeReport) {
        changeReport.appendInfo("No action required.");
        changeReport.setState(ChangeReport.CHANGE_STATE.SUCCESS);
    }
}
