package cern.c2mon.daq.opcua.connection;

import cern.c2mon.daq.opcua.EndpointListener;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.api.UaSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.CONNECTION_LOST;
import static cern.c2mon.daq.opcua.EndpointListener.EquipmentState.OK;

@Component("sessionActivityListener")
@Slf4j
@NoArgsConstructor
public class SessionActivityListenerImpl implements SessionActivityListener {

    @Getter
    private volatile boolean sessionActive = false;

    @Autowired
    private EndpointListener endpointListener;

    @Override
    public void onSessionActive(UaSession session) {
        this.sessionActive = true;
        endpointListener.onEquipmentStateUpdate(OK);
        log.info("Session with ID {} is now active.", session.getSessionId());
    }

    @Override
    public void onSessionInactive(UaSession session) {
        this.sessionActive = false;
        endpointListener.onEquipmentStateUpdate(CONNECTION_LOST);
        log.info("Session with ID {} is now inactive.", session.getSessionId());
    }
}
