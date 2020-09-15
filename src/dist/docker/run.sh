#!/usr/bin/env bash

###
# #%L
# This file is part of the CERN Control and Monitoring Platform 'C2MON'.
# %%
# Copyright (C) 2010 - 2020 CERN
# %%
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Lesser Public License for more details.
# 
# You should have received a copy of the GNU General Lesser Public
# License along with this program.  If not, see
# <http://www.gnu.org/licenses/lgpl-3.0.html>.
# #L%
###
docker run --rm --name daq-it gitlab-registry.cern.ch/c2mon/c2mon-daq-it bin/C2MON-DAQ-STARTUP.jvm -f $@
