package cern.c2mon.daq.opcua;

import cern.c2mon.daq.common.IEquipmentMessageSender;
import cern.c2mon.shared.common.datatag.SourceDataTagQuality;
import cern.c2mon.shared.common.datatag.SourceDataTagQualityCode;
import cern.c2mon.shared.common.datatag.ValueUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.*;

public class OPCUAMessageSenderTest {

    IEquipmentMessageSender eqSenderMock = niceMock(IEquipmentMessageSender.class);
    MessageSender sender = new OPCUAMessageSender();

    @BeforeEach
    public void setUp() {
        sender.initialize(eqSenderMock);
    }

    @Test
    public void onValueUpdateShouldUpdateWithValueIfQualityIsValid() {
        final SourceDataTagQuality valid = new SourceDataTagQuality(SourceDataTagQualityCode.OK);
        ValueUpdate valueUpdate = new ValueUpdate(null);
        eqSenderMock.update(0L, valueUpdate, valid);
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onValueUpdate(0L, valid, valueUpdate);
        verify(eqSenderMock);
    }


    @Test
    public void onValueUpdateShouldUpdateWithoutValueIfQualityIsValid() {
        final SourceDataTagQuality invalid = new SourceDataTagQuality(SourceDataTagQualityCode.CONVERSION_ERROR);
        eqSenderMock.update(0L, invalid);
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onValueUpdate(0L, invalid, null);
        verify(eqSenderMock);
    }

    @Test
    public void onEquipmentStateUpdateShouldSendOKIFOK() {
        final MessageSender.EquipmentState state = MessageSender.EquipmentState.OK;
        eqSenderMock.confirmEquipmentStateOK(anyString());
        expectLastCall().once();
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate(state);
        verify(eqSenderMock);
    }


    @Test
    public void onEquipmentStateUpdateShouldSendIncorrectOtherwise() {
        eqSenderMock.confirmEquipmentStateIncorrect(anyString());
        expectLastCall().times(2);
        replay(eqSenderMock);

        sender.onEquipmentStateUpdate(MessageSender.EquipmentState.CONNECTION_FAILED);
        sender.onEquipmentStateUpdate(MessageSender.EquipmentState.CONNECTION_LOST);
        verify(eqSenderMock);
    }

}
