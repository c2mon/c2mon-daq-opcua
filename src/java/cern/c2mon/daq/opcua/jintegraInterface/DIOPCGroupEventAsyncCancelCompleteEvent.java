package cern.c2mon.daq.opcua.jintegraInterface;

import com.linar.jintegra.*;

/**
 * Event Class 'DIOPCGroupEventAsyncCancelCompleteEvent'. Generated 10/5/2006 11:24:10 AM
 * from 'G:\Users\t\timoper\Public\opcdaauto.dll<P>'
 * Generated using com2java Version 2.6 Copyright (c) Intrinsyc Software International, Inc.
 * See  <A HREF="http://j-integra.intrinsyc.com/">http://j-integra.intrinsyc.com/</A><P>
 *
 * Generator Options:
 *   AwtForOcxs = False
 *   PromptForTypeLibraries = False
 *   RetryOnReject = False
 *   IDispatchOnly = False
 *   GenBeanInfo = False
 *   LowerCaseMemberNames = True
 *   TreatInStarAsIn = False
 *   ArraysAsObjects = False
 *   OmitRestrictedMethods = False
 *   ClashPrefix = zz_
 *   ImplementConflictingInterfaces = False
 *   DontRenameSameMethods = False
 *   RenameConflictingInterfaceMethods = False
 *   ReuseMethods = False
 *
 * Command Line Only Options:
 *   MakeClsidsPublic = False
 *   DontOverwrite = False
 */
public class DIOPCGroupEventAsyncCancelCompleteEvent extends java.util.EventObject {
  public DIOPCGroupEventAsyncCancelCompleteEvent(Object source) { super(source); }
  public void init(int cancelID) {
    this.cancelID = cancelID;
  }

  int cancelID;
  public final int getCancelID() { return cancelID; }
}