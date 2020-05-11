package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.exceptions.OPCCriticalException;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;

/**
 * The CommandRunner identifies appropriate actions to take upon receiving an {@link ISourceCommandTag}s and executes them on the {@link Endpoint}.
 */
@Component("commandRunner")
@Slf4j
@AllArgsConstructor
public class CommandRunner {

    @Setter
    @Autowired
    @Qualifier("miloEndpoint")
    Endpoint endpoint;

    /**
     * Executes the method associated with the tag. If the tag contains both a primary and a reduntand item name,
     * it is assumed that the primary item name refers to the object node containing the methodId, and that the
     * redundant item name refers to the method node.
     * @param tag the command tag associated with a method node.
     * @param arg the input arguments to pass to the method.
     * @return an array of the method's output arguments. If there are none, an empty array is returned.
     * @throws ConfigurationException if the server reports a status code pointing towards a misconfiguration
     * @throws CommunicationException if an error occurred when browsing the server address space or calling the method node
     */
    public Object[] executeMethod(ISourceCommandTag tag, Object arg) throws ConfigurationException, CommunicationException {
        log.info("executeMethod of tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        final ItemDefinition def = ItemDefinition.of(tag);
        final Map.Entry<StatusCode, Object[]> response;
        response = def.getMethodNodeId() == null ?
                endpoint.callMethod(endpoint.getParentObjectNodeId(def.getNodeId()), def.getNodeId(), arg) :
                endpoint.callMethod(def.getNodeId(), def.getMethodNodeId(), arg);
        log.info("executeMethod returned status code {} and output {} .", response.getKey(), response.getValue());
        handleCommandResponseStatusCode(response.getKey(), METHOD);
        return response.getValue();
    }

    public void executeCommand(ISourceCommandTag tag, Object arg) throws CommunicationException {
        log.info("executeCommand on tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        StatusCode write = endpoint.write(ItemDefinition.toNodeId(tag), arg);
        handleCommandResponseStatusCode(write, COMMAND_WRITE);
    }

    public void executePulseCommand(ISourceCommandTag tag, Object arg, int pulseLength) throws CommunicationException {
        log.info("executePulseCommand on tag with ID {} and name {} with argument {} and pulse length {}.", tag.getId(), tag.getName(), arg, pulseLength);
        final NodeId nodeId = ItemDefinition.toNodeId(tag);
        final Object original = MiloMapper.toObject(endpoint.read(nodeId).getValue());
        if (original != null && original.equals(arg)) {
            log.info("{} is already set to {}. Skipping command with pulse.", nodeId, arg);
        } else {
            log.info("Setting {} to {} for {} seconds.", nodeId, arg, pulseLength);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> rewrite(tag, nodeId, original, pulseLength, false), pulseLength, TimeUnit.SECONDS);
            scheduler.shutdown();
            final StatusCode statusCode = endpoint.write(nodeId, arg);
            handleCommandResponseStatusCode(statusCode, COMMAND_WRITE);
        }
    }

    private void rewrite(ISourceCommandTag tag, NodeId nodeId, Object original, int pulseLength, boolean retry) {
        try {
            final StatusCode statusCode = endpoint.write(nodeId, original);
            log.info("Resetting tag with ID {} and name {} to {} returned statusCode {}. ", tag.getId(), tag.getName(), original, statusCode);
            handleCommandResponseStatusCode(statusCode, COMMAND_REWRITE);
        } catch (CommunicationException e) {
            if (retry) {
                throw new OPCCriticalException(e);
            }
            log.info("Could not rewrite the initial value! Try again in a while. ");
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> rewrite(tag, nodeId, original, pulseLength, true), pulseLength, TimeUnit.SECONDS);
        }
    }

    private void handleCommandResponseStatusCode(StatusCode statusCode, ExceptionContext context) throws CommunicationException {
        if (!statusCode.isGood()) {
            throw new CommunicationException(context);
        }
    }

}
