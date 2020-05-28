package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.daq.tools.equipmentexceptions.EqCommandTagException;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            throw new EqCommandTagException(ConfigurationException.Cause.COMMAND_VALUE_ERROR.message);
        }
        try {
            final var hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
            switch (hardwareAddress.getCommandType()) {
                case METHOD:
                    return Stream.of(executeMethod(tag, arg).join())
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));
                case CLASSIC:
                    runClassicCommand(tag, arg, hardwareAddress.getCommandPulseLength()).join();
                    return null;
                default:
                    // impossible
                    throw new EqCommandTagException(ConfigurationException.Cause.COMMAND_TYPE_UNKNOWN.message);
            }
        } catch (Exception e) {
            final Throwable cause = e.getCause();
            if (cause instanceof EqCommandTagException) {
                throw (EqCommandTagException) cause;
            } else if (cause instanceof ConfigurationException) {
                throw new EqCommandTagException("Please check whether the configuration is correct.", cause);
            }
            throw new EqCommandTagException("An unexpected error occurred.", (cause == null) ? e : cause);
        }
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
    private CompletableFuture<Object[]> executeMethod(ISourceCommandTag tag, Object arg) {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final var response = (def.getMethodNodeId() == null) ?
                endpoint.getParentObjectNodeId(def.getNodeId())
                        .thenCompose(parentNodeId -> endpoint.callMethod(parentNodeId, def.getNodeId(), arg)) :
                endpoint.callMethod(def.getNodeId(), def.getMethodNodeId(), arg);
        return response
                .thenApply(codeResultEntry -> {
                    log.info("executeMethod returned {}.", codeResultEntry.getValue());
                    handleCommandResponseStatusCode(codeResultEntry.getKey(), ExceptionContext.METHOD_CODE);
                    return codeResultEntry.getValue();
                });
    }

    private CompletableFuture<Void> runClassicCommand(ISourceCommandTag tag, Object arg, int pulse) {
        if (pulse == 0) {
            log.info("Setting Tag with ID {} to {}.", tag.getId(), arg);
            return executeWriteCommand(tag, arg);
        } else {
            return endpoint.read(ItemDefinition.toNodeId(tag))
                    .thenApply(value -> {
                        handleCommandResponseStatusCode(value.getStatusCode(), ExceptionContext.READ);
                        return MiloMapper.toObject(value.getValue());
                    })
                    .thenCompose(original -> {
                        if (original.equals(arg)) {
                            log.info("Tag with ID {} is already set to {}. Skipping command with pulse.", tag.getId(), arg);
                            return CompletableFuture.completedFuture(null);
                        } else {
                            log.info("Setting Tag with ID {} to {} for {} seconds.", tag.getId(), arg, pulse);
                        return executeWriteCommand(tag, arg)
                                .thenComposeAsync(v -> {
                                    log.info("Setting Tag with ID {} back to {}.", tag.getId(), original);
                                    return executeWriteCommand(tag, original);
                                }, CompletableFuture.delayedExecutor(pulse, TimeUnit.SECONDS));
                        }
                    });
        }
    }

    private CompletableFuture<Void> executeWriteCommand(ISourceCommandTag tag, Object arg) {
        return endpoint.write(ItemDefinition.toNodeId(tag), arg)
                .thenAccept(s -> handleCommandResponseStatusCode(s, ExceptionContext.COMMAND_CLASSIC));
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
    private void handleCommandResponseStatusCode(@Nullable StatusCode statusCode, ExceptionContext context) {
        log.info("Action completed with Status Code: {}", statusCode);
        if (statusCode == null || !statusCode.isGood()) {
            throw new CompletionException(new CommunicationException(context));
        }
    }
}
