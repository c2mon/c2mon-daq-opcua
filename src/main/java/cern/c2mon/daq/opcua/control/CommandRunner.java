package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCUAException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import com.google.common.base.Joiner;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * The CommandRunner identifies appropriate actions to take upon receiving an {@link ISourceCommandTag}s and executes
 * them on the {@link Endpoint}.
 */
@Component("commandRunner")
@Slf4j
@AllArgsConstructor
public class CommandRunner {

    @Setter
    private Endpoint endpoint;

    /**
     * Execute the action corresponding to a command tag, either by writing to a nodeId or by calling a method node on
     * the server. Retries e.g in case of connection issues are handled in {@link cern.c2mon.daq.common.messaging.impl.RequestController}.
     *
     * @param tag     The commandTag to execute
     * @param command the value of the command that shall be executed
     * @return A joined String of method results, if the command is of type METHOD and the server returns any output
     * arguments. Else an empty String.
     * @throws EqCommandTagException if the command cannot be executed or the server returns an invalid status code.
     */
    public String runCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws EqCommandTagException {
        Object arg = TypeConverter.cast(command.getValue(), command.getDataType());
        if (arg == null) {
            throw new EqCommandTagException(ExceptionContext.COMMAND_VALUE_ERROR.getMessage());
        }
        try {
            final var hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
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
        }
        return null;
    }

    /**
     * Executes the method associated with the tag. If the tag contains both a primary and a redundant item name, it is
     * assumed that the primary item name refers to the object node containing the methodId, and that the redundant item
     * name refers to the method node.
     *
     * @param tag the command tag associated with a method node.
     * @param arg the input arguments to pass to the method.
     * @return an array of the method's output arguments. If there are none, an empty array is returned.
     */
    private Object[] executeMethod(ISourceCommandTag tag, Object arg) throws OPCUAException, InterruptedException {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final Map.Entry<StatusCode, Object[]> result;
        if (def.getMethodNodeId() == null) {
            final NodeId parent = endpoint.getParentObjectNodeId(def.getNodeId());
            result = endpoint.callMethod(parent, def.getMethodNodeId(), arg);
        } else {
            result = endpoint.callMethod(def.getNodeId(), def.getMethodNodeId(), arg);
        }
        log.info("executeMethod returned {}.", result.getValue());
        handleCommandResponseStatusCode(result.getKey(), ExceptionContext.METHOD_CODE);
        return result.getValue();
    }

    private void runClassicCommand(ISourceCommandTag tag, Object arg, int pulse) throws OPCUAException, InterruptedException, EqCommandTagException {
        if (pulse == 0) {
            log.info("Setting Tag with ID {} to {}.", tag.getId(), arg);
            executeWriteCommand(tag, arg);
        } else {
            final DataValue value = endpoint.read(ItemDefinition.toNodeId(tag));
            handleCommandResponseStatusCode(value.getStatusCode(), ExceptionContext.READ);
            final Object original = MiloMapper.toObject(value.getValue());
            if (original != null && original.equals(arg)) {
                log.info("Tag with ID {} is already set to {}. Skipping command with pulse.", tag.getId(), arg);
            } else {
                final var f = CompletableFuture.runAsync(() -> {
                    log.info("Resetting Tag with ID {} to {}.", tag.getId(), original);
                    try {
                        executeWriteCommand(tag, original);
                    } catch (OPCUAException | InterruptedException e) {
                        throw new CompletionException(e);
                    }
                }, CompletableFuture.delayedExecutor(pulse, TimeUnit.SECONDS));
                log.info("Setting Tag with ID {} to {} for {} seconds.", tag.getId(), arg, pulse);
                executeWriteCommand(tag, arg);
                try {
                    f.join();
                } catch (CompletionException e) {
                    if (e.getCause() instanceof OPCUAException) {
                        throw (OPCUAException) e.getCause();
                    } else if (e.getCause() instanceof InterruptedException) {
                        throw (InterruptedException) e.getCause();
                    } else {
                        throw new EqCommandTagException("An unexpected error occurred when resetting the command tag.", e.getCause());
                    }
                }
            }
        }
    }

    private void executeWriteCommand(ISourceCommandTag tag, Object arg) throws OPCUAException, InterruptedException {
        final StatusCode statusCode = endpoint.write(ItemDefinition.toNodeId(tag), arg);
        handleCommandResponseStatusCode(statusCode, ExceptionContext.COMMAND_CLASSIC);
    }

    /**
     * {@link cern.c2mon.daq.common.messaging.impl.RequestController} assumes all command executions that do not throw
     * an error to be successful. To ensure a reliable result, repeat the execution by throwing an error upon status
     * codes that are not "good".
     *
     * @param statusCode the status code returned by upon the taken action
     * @param context    The context of the action taken which resulted in the action code
     * @throws CompletionException a wrapper for a CommunicationException, since this method is only called from within
     *                             CompletableFutures and will be handled on join.
     */
    private void handleCommandResponseStatusCode(@Nullable StatusCode statusCode, ExceptionContext context) throws CommunicationException {
        log.info("Action completed with Status Code: {}", statusCode);
        if (statusCode == null || !statusCode.isGood()) {
            throw new CommunicationException(context);
        }
    }
}