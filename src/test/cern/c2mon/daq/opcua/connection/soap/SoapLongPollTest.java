package cern.c2mon.daq.opcua.connection.soap;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.axiom.om.OMElement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opcfoundation.xmlda.ItemValue;
import org.opcfoundation.xmlda.SubscribePolledRefreshReplyItemList;

import cern.c2mon.daq.opcua.connection.common.impl.OPCUADefaultAddress;

public class SoapLongPollTest {
    

    private OPCUADefaultAddress address;

    private SoapLongPoll poll;
    
    private volatile static Throwable exception;
    
    @Before
    public void setUp() throws URISyntaxException {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                exception = e;
            }
        });
        address = new OPCUADefaultAddress.DefaultBuilder(
                "http://host/path", 100, 1000)
                .build();
        poll = new SoapLongPoll(
                address , "asd", 1000, 10000);
    }
    
    @After
    public void tearDown() throws Exception {
        if  (exception != null) {
            throw new Exception(exception);
        }
    }
    
    @Test
    public void testNotifyListeners() throws InterruptedException {
        ISoapLongPollListener listener = createMock(ISoapLongPollListener.class);
        poll.addListener(listener);
        
        String clientHandle = "asd";
        long timeStamp = 100;
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timeStamp);
        OMElement element = createMock(OMElement.class);
        
        ItemValue itemValue = new ItemValue();
        itemValue.setClientItemHandle(clientHandle);
        itemValue.setTimestamp(calendar);
        itemValue.setValue(element);
        SubscribePolledRefreshReplyItemList list =
            new SubscribePolledRefreshReplyItemList();
        list.addItems(itemValue);
        SubscribePolledRefreshReplyItemList[] lists = {list};
        
        String value = "asd";
        expect(element.getText()).andReturn(value);
        listener.valueChanged(clientHandle, timeStamp, value);
        
        replay(listener, element);
        poll.notifyListeners(lists);
        Thread.sleep(100);
        verify(listener, element);
    }

}