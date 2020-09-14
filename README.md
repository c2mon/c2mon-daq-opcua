**This module is still in the testing phase, use with caution!**

# Overview

The OPC-UA DAQ allows C2MON to collect data from OPC-UA servers. It relies on the [Eclipse Milo](https://github.com/eclipse/milo) library (Eclipse Public License 1.0). 

# Downloading latest stable distribution tarball

The c2mon-daq-it tarball release can be downloaded from [CERN Nexus Repository](https://nexus.web.cern.ch/nexus/#nexus-search;gav~cern.c2mon.daq~c2mon-daq-opcua~~tar.gz~).

Please check [here](https://gitlab.cern.ch/c2mon/c2mon-daq-opcua/tags) for the latest stable releaes version.


## Installing

- Download the latest stable distribution tarball
- Note, that the tarball does not include a root folder, so you have to create this yourself before extracting it:
  
  ```bash
  mkdir c2mon-daq-it; tar -xzf c2mon-daq-it-1.0.x-dist.tar.gz -C c2mon-daq-it
  
  ```

# Commands

The OPC-UA DAQ supports commands.

# Configuration
Follow the C2MON [Client API](https://c2mon.web.cern.ch/c2mon/docs/user-guide/client-api/index.html) for information on how to configure the Processes, Equipment and DataTags.
The `EquipmentMessageHandler` class to be specified during the Equipment creation is: `cern.c2mon.daq.opcua.OPCUAMessageHandler`. 

The DAQ can be configured externally through Spring Boot as described [here](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config). 
These options are valid for all Equipments of a DAQ Process. They can be overwritten for a particular Equipment by appending the respective option to the EquipmentAddress. 
For example, "URI=opc.tcp://hostname:2020;trustAllServers=true" will skip validation of the incoming server certificate against the stored certificate authority **only** for the equipment with that address.

| Category          | Property                  | Description                                                                                                                                                                                                                                                                                                                                                                                           |
|-------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **General**       | restartDelay              | The delay in milliseconds before restarting the DAQ after an Equipment change if the change affected the EquipmentAddress.                                                                                                                                                                                                                                                                            |
|                   | requestTimeout            | The timeout in milliseconds indicating for how long the client is willing to wait for a server response on a single transaction in milliseconds. The maximum value is 5000 due to a static timeout in the EclipseMilo package.                                                                                                                                                                        |
|                   | queueSize                 | The maximum number of values which can be queued in between publish intervals of the subscriptions. If more updates occur during the time frame of the DataTags’ time deadband, these values are added to the queue. The fastest possible sampling rate for the server is used for each MonitoredItem.                                                                                                |
|                   | aliveWriterEnabled        | The AliveWriter ensures that the SubEquipments connected to the OPC UA server are still running, and sends regular AliveTags to the C2MON Core.                                                                                                                                                                                                                                                       |
| **Redundany**     | redundancyMode            | The redundancy handler mode to use (Part of the FailoverMode enum). A ConcreteController will be resolved (within ControllerFactory) according to this value, instead of querying the the server’s AddressSpace for the appropriate information. Can be for speedup to avoid querying the server for its redundancy mode upon each new connection, and to support vendor-specific redundancy modes.   |
|                   | redundantServerUris       | URIs of redundant servers to use instead of the reading the URIs from the server’s address space                                                                                                                                                                                                                                                                                                      |
|                   | failoverDelay             | The delay before triggering a failover after a Session deactivates. Set to -1 to not use the Session status as a trigger for a failover.                                                                                                                                                                                                                                                              |
|                   | connectionMonitoringRate  | The publishing rate for the subscriptions to Nodes monitoring the connection in redundant server sets in seconds.                                                                                                                                                                                                                                                                                     |
| **Security**      | trustAllServers           | The client will make no attempt to validate server certificates, but trust servers. If disabled, incoming server certificates are verified against the certificates listed in pkiBaseDir.                                                                                                                                                                                                             |
|                   | pkiBaseDir                | Specifies the path to the PKI directory of the client. If the“trusted” subdirectory in pkiBaseDir contains either a copy of either the incoming certificate or a certificate higher up the Certificate Chain, then the certificate is deemed trustworthy.                                                                                                                                             |
|                   | certifierPriority         | [NO_SECURITY, GENERATE, LOAD] <br> Connection with a Certifier associated with the element will be attempted in decreasing order of the associated value until successful. If the value is not given then that Certifier will not be used.                                                                                                                                                            |
| **Certification** | applicationName                                                                           | The name of the application to specify in the connection request and a generated certificate                                                                                                                                                                                                                                          |
|                   | applicationUri                                                                            | Must match the applicationUri of a loaded certificate exactly, if applicable.                                                                                                                                                                                                                                                         |
|                   | organization  <br> organizationalUnit <br> localityName <br> stateName <br>  countryCode  | Properties to use in the generation of a self-signed certificate.                                                                                                                                                                                                                                                                     |
|                   | keystore.type <br> keystore.path <br> keystore.password <br> keystore.alias               | Properties required to load a certificate and private key from a keystore file.                                                                                                                                                                                                                                                       |
|                   | pki.privateKeyPath <br> pki.certificatePath                                               | Paths to the PEM-encoded certificate and private key files respecively.                                                                                                                                                                                                                                                               |
| **Modifying server information**  | applicationName                                                                         | The name of the application to specify in the connection request and a generated certificate                                                                                                                                                                                                                            |
|                                   | applicationUri                                                                          | Must match the applicationUri of a loaded certificate exactly, if applicable.                                                                                                                                                                                                                                           |
|                                   | organization <br> organizationalUnit <br> localityName <br> stateName <br> countryCode  | Properties to use in the generation of a self-signed certificate.                                                                                                                                                                                                                                                       |
|                                   | keystore.type <br> keystore.path <br> keystore.password <br> keystore.alias             | Properties required to load a certificate and private key from a keystore file.                                                                                                                                                                                                                                         |
|                                   | pki.privateKeyPath <br> pki.certificatePath                                             | Paths to the PEM-encoded certificate and private key files respecively.                                                                                                                                                                                                                                                 |
| **Retry**         | retryDelay                | The initial delay before retrying a failed service call. The time in between retries is multiplied by retryMultiplier on every new failure, until reaching the maximum time of "maxRetryDelay".                                                                                                                                                                                                       |
|                   | retryMultiplier           | On each new failed attempt, the delay time before another call is multiplied by "retryMulitplier" starting with "retryDelay" and up to a maximum of "maxRetryDelay".                                                                                                                                                                                                                                  |
|                   | maxRetryDelay             | The maximum delay when retrying failed service calls.                                                                                                                                                                                                                                                                                                                                                 |
|                   | maxRetryAttempts          | The maximum amount of attempts to retry failed service calls in milliseconds. This does NOT include recreating subscriptions, and the failover process in redundant server setups.                                                                                                                                                                                                                    |

# Redundancy

OPC UA redundancy is supported in Cold Failover mode, which can be used as a fallback for higher redundancy modes. 
The DAQ can be extended to support other redundancy modes as follows:
## Custom Redundancy modes

*   Add a custom value to the _cern.c2mon.daq.opcua.config.AppConfigProperties.FailoverMode_ enumeration and configure your Process to use this FailoverMode.
*   Create a new _cern.c2mon.daq.opcua.control.ConcreteController_. You may find it it useful to extend ControllerBase, in which case you should only need to deal with Initial choice of server, monitoring the connection for your failover triggers, and the failover process itself
*   Modify the _getObject(AppConfigProperties.FailoverMode mode)_ method within _cern.c2mon.daq.opcua.control.ControllerFactory_ to return an instance of your ConcreteController upon your custom FailoverMode

## OPC UA Redundancy modes

*   Add a new ConcreteController extending FailoverBase which overwrite the initalize(), switchServers(), currentEndpoint() and passiveEndpoints() methods respectively with the appropriate actions:
    *   **initialize()**  
        *   is passed an already connected Endpoint by the ControllerProxy.
        *   Find the _healthiest_ Server (with the highest ServiceLevel): this should be the active server
        *   For **HotAndMirrored:**
            *   Only connect to the healthiest server.
        *   For all other modes:
            *   Connect to the server(s) at the `redundantUris`:
        *   Set the appropriate **MonitoringMode** on the Endpoints  
            *   In _org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode,_ "Reporting" is  sampling and  publishing in Table 111 (UA Part 6) below
            *   **Warm**: Set active Endpoint to `Reporting`, and all others to `Disabled`
            *   **Hot(a)**: active to `Reporting`, all others to `Sampling`
            *   **Hot(b)**: all to `Reporting`
            *   **HotAndMirrored:** active to `Reporting`, this should be the only one that's connected.
        *   call _cern.c2mon.daq.opcua.control.FailoverBase.connectionMonitoring()_ when done.
    *   **currentEndpoint()** should return the active Endpoint: This will be the only Endpoint called for read or write operations.
    *   **passiveEndpoints()** should return all the Endpoints on which subscriptions should be crated and modified (an empty list for **Warm** and **HotAndMirrored**, all Endpoints connected to the backup servers for **Hot**).
    *   **switchServer()** according to the redundancy mode:  
        *   switchServer() is called until successful by FailoverBase when the ServerState or ServiceLevel nodes change to unhealthy, or the session deactivates (if configured) → throw an OPCUAException if no connection is possible!
        *   find the new healthiest server → this will the new active server
        *   modify `cern.c2mon.daq.opcua.control.FailoverBase.MiloEndpoint` so that switching the MonitoringMode changes the modes of the active subscriptions
        *   modify the `org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode` as the former and now-active Endpoint accordingly
*   for **Warm** failover, call `cern.c2mon.daq.opcua.control.FailoverBase.triggerServerSwitch()` when receiving the StatusCode `Bad_NoCommunication` → this indicates that we're connected to the inactive Server
*   Modify the `getObject(AppConfigProperties.FailoverMode mode)` method within `cern.c2mon.daq.opcua.control.ControllerFactory` to return an instance of your ConcreteController upon the appropriate FailoverMode  
    [Redundancy failover actions](https://reference.opcfoundation.org/v104/Core/docs/Part4/6.6.2/#Table111)

# Spring Actuators

The OPC UA DAQ exposes health, dumps, and info through Spring actuator endpoints, accessible through JMX and HTTP via Jolokia. The following operations are exposed to such access:

*   reading out values from single DataTags
    
*   starting and stopping the AliveWriter
    
*   examining the type of redundancy Controller currently in use
    
*   triggering a failover process in the case of a redundant server setup

## JMX

By default, JMX is exposed through port 8913. To change this port, modify the included Dockerfile and build a tarball. 
When running with Docker, this port must be exposed.
Set the following application property:

  ```bash
  spring.jmx.enabled=true
  ```

The process can now be accessed through JMX under **service:jmx:rmi:///jndi/rmi://[YOUR HOST]:8913/jmxrmi**

## HTTP

To expose the application through HTTP, set the following application properties and expose [HTTP PORT].

  ```bash
  management.endpoints.web.exposure.include=[TO EXPOSE],jolokia
  management.server.port=[HTTP PORT] 
  ```
The process can now be accessed through HTTP under **http://[YOUR HOST]:[HTTP PORT]/actuator/jolokia**
