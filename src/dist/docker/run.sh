#!/usr/bin/env bash
docker run --rm --name daq-it -ti --net=host -e "C2MON_PORT_61616_TCP=tcp://localhost:61616" \
  gitlab-registry.cern.ch/c2mon/c2mon-daq-it bin/C2MON-DAQ-STARTUP.jvm -f $@
