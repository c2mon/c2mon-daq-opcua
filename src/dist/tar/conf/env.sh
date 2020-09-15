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
VERSION=`cat ${DAQ_HOME}/version.txt`
JVM_OTHER_OPTS="-DJINTEGRA_LOG_LEVEL=1 -DJINTEGRA_LOG_ROLLOVER_LINECOUNT=100000 -DJINTEGRA_LOG_FILE=$DAQ_HOME/log/${PROCESS_NAME}.jintegra.log -Dapp.name=${PROCESS_NAME} -Dapp.version=${VERSION}"
