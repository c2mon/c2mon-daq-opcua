docker run --rm --name daq-opcua -ti --net=host -e "C2MON_PORT_61616_TCP=tcp://localhost:61616" docker.cern.ch/c2mon-project/daq-opcua bin/C2MON-DAQ-STARTUP.jvm -f $@
