/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 * 
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 * 
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.daq.opcua.connection.soap;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


import org.apache.axis2.client.Stub;
import org.apache.axis2.transport.http.HTTPConstants;
import org.junit.Test;
import org.opcfoundation.xmlda.ItemValue;
import org.opcfoundation.xmlda.OPCXML_DataAccessStub;
import org.opcfoundation.xmlda.ReadRequestItem;
import org.opcfoundation.xmlda.Subscribe;
import org.opcfoundation.xmlda.SubscribeRequestItem;
import org.opcfoundation.xmlda.SubscriptionPolledRefresh;
import org.opcfoundation.xmlda.Write;

import cern.c2mon.daq.opcua.connection.soap.SoapObjectFactory;

public class SoapObjectFactoryTest {

    @Test
    public void testCreateSubscribe() {
        String requestHandle = "asd";
        int subscripionPingRate = 1000;
        List<SubscribeRequestItem> subscribeRequestItems =
            new ArrayList<SubscribeRequestItem>();
        subscribeRequestItems.add(new SubscribeRequestItem());
        subscribeRequestItems.add(new SubscribeRequestItem());
        subscribeRequestItems.add(new SubscribeRequestItem());
        Subscribe subscribe = SoapObjectFactory.createSubscribe(
                requestHandle, subscribeRequestItems, subscripionPingRate);
        assertEquals(
                requestHandle, subscribe.getOptions().getClientRequestHandle());
        assertEquals(subscripionPingRate, subscribe.getSubscriptionPingRate());
        assertEquals(3, subscribe.getItemList().getItems().length);
    }
    
    @Test
    public void testCreateSubscribeRequestItem() {
        String clientItemHandle = "asd";
        String address = "asd2";
        float valueDeadband = 32984.0f;
        int timeDeadband = 100;
        boolean bufferEnabled = false;
        SubscribeRequestItem item = 
            SoapObjectFactory.createSubscribeRequestItem(
                    clientItemHandle, address, valueDeadband, timeDeadband,
                    bufferEnabled);
        assertEquals(clientItemHandle, item.getClientItemHandle());
        assertEquals(address, item.getItemName());
        assertEquals(
                Double.valueOf(valueDeadband), 
                Double.valueOf(item.getDeadband()));
        assertEquals(timeDeadband, item.getRequestedSamplingRate());
        assertEquals(bufferEnabled, item.getEnableBuffering());
    }
    
    @Test
    public void testCreateSubscriptionPolledRefresh() {
        String serverSubscriptionHandle = "asd";
        int waitTime = 234;
        SubscriptionPolledRefresh refresh = 
            SoapObjectFactory.createSubscriptionPolledRefresh(
                    serverSubscriptionHandle, waitTime);
        assertEquals(serverSubscriptionHandle, refresh.getServerSubHandles()[0]);
        assertEquals(waitTime, refresh.getWaitTime());
        assertNotNull(refresh.getHoldTime());
    }
    
    @Test
    public void testCreateOPCDataAccessSoapWithDomain() 
            throws MalformedURLException {
//        URL serverURL = new URL("http://somehost/somepath");
//        String domain = "asd";
//        String user = "user";
//        String password = "password";
//        OPCXML_DataAccessStub access = 
//            SoapObjectFactory.createOPCDataAccessSoapInterface(
//                serverURL, domain, user, password);
//        assertEquals(
//                domain + "\\" + user,
//                access._getProperty(Stub.USERNAME_PROPERTY));
//        assertEquals(
//                password,
//                access._getProperty(HTTPConstants.PASSWORD_PROPERTY));
//        assertEquals(
//                serverURL.toString(),
//                access._getProperty(Stub.ENDPOINT_ADDRESS_PROPERTY));
    }
    
    @Test
    public void testCreateOPCDataAccessSoapWithoutDomain() 
            throws MalformedURLException {
//        URL serverURL = new URL("http://somehost/somepath");
//        String domain = null;
//        String user = "user";
//        String password = "password";
//        OPCXMLDataAccessSoap access = 
//            SoapObjectFactory.createOPCDataAccessSoapInterface(
//                serverURL, domain, user, password);
//        assertEquals(
//                user,
//                ((Stub) access)._getProperty(Stub.USERNAME_PROPERTY));
//        assertEquals(
//                password,
//                ((Stub) access)._getProperty(Stub.PASSWORD_PROPERTY));
//        assertEquals(
//                serverURL.toString(),
//                ((Stub) access)._getProperty(Stub.ENDPOINT_ADDRESS_PROPERTY));
    }
    
    @Test
    public void testCreateOPCDataAccessSoapWithoutCredentials() 
            throws MalformedURLException {
//        URL serverURL = new URL("http://somehost/somepath");
//        String domain = null;
//        String user = null;
//        String password = null;
//        OPCXMLDataAccessSoap access = 
//            SoapObjectFactory.createOPCDataAccessSoapInterface(
//                serverURL, domain, user, password);
//        assertNull(((Stub) access)._getProperty(Stub.USERNAME_PROPERTY));
//        assertNull(((Stub) access)._getProperty(Stub.PASSWORD_PROPERTY));
//        assertEquals(
//                serverURL.toString(),
//                ((Stub) access)._getProperty(Stub.ENDPOINT_ADDRESS_PROPERTY));
    }
    
    @Test
    public void testCreateRequestItem() {
        String clientItemHandle = "asd";
        String address = "address";
        ReadRequestItem item = 
            SoapObjectFactory.createReadRequestItem(clientItemHandle, address);
        assertEquals(clientItemHandle, item.getClientItemHandle());
        assertEquals(address, item.getItemName());
    }
    
    @Test
    public void testCreateWrite() {
        String clientItemHandle = "asd";
        ItemValue value = new ItemValue();
        ItemValue[] values = { value, value};
        Write write = 
            SoapObjectFactory.createWrite(clientItemHandle, values);
        assertEquals(
                clientItemHandle, write.getOptions().getClientRequestHandle());
        assertEquals(2, write.getItemList().getItems().length);
        assertEquals(value, write.getItemList().getItems()[0]);
        assertEquals(value, write.getItemList().getItems()[1]);
    }
    
    @Test
    public void testCreateItemValue() {
        String clientItemHandle = "asd";
        String itemName = "asdasdasd";
        Object value = 3424L;
        ItemValue itemValue =
            SoapObjectFactory.createItemValue(clientItemHandle, itemName, value);
        assertEquals(clientItemHandle, itemValue.getClientItemHandle());
        assertEquals(itemName, itemValue.getItemName());
        assertEquals(value.toString(), itemValue.getValue().getText());
    }
}
