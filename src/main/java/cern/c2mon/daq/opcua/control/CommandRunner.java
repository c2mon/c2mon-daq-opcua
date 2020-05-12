package cern.c2mon.daq.opcua.control;

import cern.c2mon.daq.opcua.connection.Endpoint;
import cern.c2mon.daq.opcua.exceptions.CommunicationException;
import cern.c2mon.daq.opcua.exceptions.ConfigurationException;
import cern.c2mon.daq.opcua.exceptions.ExceptionContext;
import cern.c2mon.daq.opcua.mapping.ItemDefinition;
import cern.c2mon.daq.opcua.mapping.MiloMapper;
import cern.c2mon.shared.common.command.ISourceCommandTag;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static cern.c2mon.daq.opcua.exceptions.ExceptionContext.*;

/**
 * The CommandRunner identifies appropriate actions to take upon receiving an {@link ISourceCommandTag}s and executes them on the {@link Endpoint}.
 */
@Component("commandRunner")
@Slf4j
@AllArgsConstructor
public class CommandRunner {

    @Setter
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

    public Object getOriginal(ISourceCommandTag tag) throws CommunicationException {
        final NodeId nodeId = ItemDefinition.toNodeId(tag);
        return MiloMapper.toObject(endpoint.read(nodeId).getValue());
    }

    public void executeCommand(ISourceCommandTag tag, Object arg) throws CommunicationException {
        executeCommand(tag, arg, false);
    }

    public void executeCommand(ISourceCommandTag tag, Object arg, boolean isRewrite) throws CommunicationException {
        log.info("executeCommand on tag with ID {} and name {} with argument {}.", tag.getId(), tag.getName(), arg);
        StatusCode write = endpoint.write(ItemDefinition.toNodeId(tag), arg);
        handleCommandResponseStatusCode(write, isRewrite ? COMMAND_REWRITE : COMMAND_WRITE);
    }

    private void handleCommandResponseStatusCode(StatusCode statusCode, ExceptionContext context) throws CommunicationException {
        if (!statusCode.isGood()) {
            throw new CommunicationException(context);
        }
    }

}
