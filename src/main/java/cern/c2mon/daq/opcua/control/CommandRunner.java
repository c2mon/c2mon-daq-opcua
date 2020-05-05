package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.OPCCommunicationException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import cern.c2mon.shared.common.datatag.address.OPCHardwareAddress;
import cern.c2mon.shared.common.type.TypeConverter;
import cern.c2mon.shared.daq.command.SourceCommandTagValue;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cern.c2mon.daq.opcua.exceptions.OPCCommunicationException.Context.*;

@Component("commandRunner")
@Slf4j
@AllArgsConstructor
public class CommandRunner {

    @Setter
    @Autowired
    Endpoint endpoint;

    public String runCommand(ISourceCommandTag tag, SourceCommandTagValue command) throws ConfigurationException {
        String result = "";
        Object arg = TypeConverter.cast(command.getValue(), command.getDataType());
        if (arg == null) {
            throw new ConfigurationException(ConfigurationException.Cause.COMMAND_VALUE_ERROR);
        }
        OPCHardwareAddress hardwareAddress = (OPCHardwareAddress) tag.getHardwareAddress();
        switch (hardwareAddress.getCommandType()) {
            case METHOD:
                final Object[] response = executeMethod(tag, arg);
                log.info("Successfully executed method {} with arg {}", tag, arg);
                result = Stream.of(response).map(Object::toString).collect(Collectors.joining(", "));
                break;
            case CLASSIC:
                final int pulse = hardwareAddress.getCommandPulseLength();
                if (pulse > 0)  {
                    executePulseCommand(tag, arg, pulse);
                } else {
                    executeCommand(tag, arg);
                }
                break;
            default:
                throw new ConfigurationException(ConfigurationException.Cause.COMMAND_TYPE_UNKNOWN);
        }
        return result;
    }

    /**
     * Executes the method associated with the tag. If the tag contains both a primary and a reduntand item name,
     * it is assumed that the primary item name refers to the object node containing the methodId, and that the
     * redundant item name refers to the method node.
     * @param tag the command tag associated with a method node.
     * @param arg the input arguments to pass to the method.
     * @return an array of the method's output arguments. If there are none, an empty array is returned.
     */
    public Object[] executeMethod(ISourceCommandTag tag, Object arg) {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final Map.Entry<StatusCode, Object[]> response = def.getMethodNodeId() == null ?
                endpoint.callMethod(endpoint.getParentObjectNodeId(def.getNodeId()), def.getNodeId(), arg):
                endpoint.callMethod(def.getNodeId(), def.getMethodNodeId(), arg);
        log.info("executeMethod returned status code {} and output {} .", response.getKey(), response.getValue());
        handleCommandResponseStatusCode(response.getKey(), METHOD);
        return response.getValue();
    }

    public void executeCommand(ISourceCommandTag tag, Object arg) {
        log.info("executeCommand on tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        StatusCode write = endpoint.write(ItemDefinition.toNodeId(tag), arg);
        handleCommandResponseStatusCode(write, COMMAND_WRITE);
    }

    public void executePulseCommand(ISourceCommandTag tag, Object arg, int pulseLength) {
        log.info("executePulseCommand on tag with ID {} and name {} with argument {} and pulse length {}.", tag.getId(), tag.getName(), arg, pulseLength);
        final NodeId nodeId = ItemDefinition.toNodeId(tag);
        final Object original = MiloMapper.toObject(endpoint.read(nodeId).getValue());
        if (original != null && original.equals(arg)) {
            log.info("{} is already set to {}. Skipping command with pulse.", nodeId, arg);
        } else {
            log.info("Setting {} to {} for {} seconds.", nodeId, arg, pulseLength);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                final StatusCode statusCode = endpoint.write(nodeId, original);
                log.info("Resetting tag with ID {} and name {} to {} returned statusCode {}. ", tag.getId(), tag.getName(), original, statusCode);
                handleCommandResponseStatusCode(statusCode, COMMAND_REWRITE);
            }, pulseLength, TimeUnit.SECONDS);
            scheduler.shutdown();
            final StatusCode statusCode = endpoint.write(nodeId, arg);
            handleCommandResponseStatusCode(statusCode, COMMAND_WRITE);
        }
    }

    private void handleCommandResponseStatusCode(StatusCode statusCode, OPCCommunicationException.Context context) {
        if (!statusCode.isGood()) {
            throw new OPCCommunicationException(context);
        }
    }

}
