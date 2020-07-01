#!/usr/bin/env bash
docker run --rm --name daq-it gitlab-registry.cern.ch/c2mon/c2mon-daq-it bin/C2MON-DAQ-STARTUP.jvm -f $@
