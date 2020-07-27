# Overview

The OPC-UA DAQ allows C2MON to collect data from OPC-UA servers:

- OPC-UA implementations based on Eclipse Milo (Eclipse Public License 1.0).


# Downloading latest stable distribution tarball

The c2mon-daq-it tarball release can be downloaded from [CERN Nexus Repository](https://nexus.web.cern.ch/nexus/#nexus-search;gav~cern.c2mon.daq~c2mon-daq-opcua~~tar.gz~)

Please check [here](https://gitlab.cern.ch/c2mon/c2mon-daq-opcua/tags) for the latest stable releaes version.

## Installing

- Download the latest stable distribution tarball
- Note, that the tarball does not include a root folder, so you have to create this yourself before extracting it:
  
  ```bash
  mkdir c2mon-daq-it; tar -xzf c2mon-daq-it-1.0.x-dist.tar.gz -C c2mon-daq-it
  
  ```




# General configuration tips
In order to configure RESTful datatags you have first to declare a REST DAQ [Process](http://c2mon.web.cern.ch/c2mon/docs/latest/user-guide/client-api/configuration/#configuring-processes) and [Equipment](http://c2mon.web.cern.ch/c2mon/docs/latest/user-guide/client-api/configuration/#configuring-equipment) to which you want then to attach the tags. 

Please read therefore also the documentation about the [C2MON configuration API](http://c2mon.web.cern.ch/c2mon/docs/latest/user-guide/client-api/configuration/#configuration-api). 

The `EquipmentMessageHandler` class to be specified during the Equipment creation is: `cern.c2mon.daq.opcua.OPCUAMessageHandler`

# Commands

The OPC-UA DAQ supports commands.

# Useful Links

- https://github.com/digitalpetri/ua-client-sdk
- [C2MON configuration API] (https://c2mon.web.cern.ch/c2mon/docs/latest/user-guide/client-api/configuration/#configuration-api)
