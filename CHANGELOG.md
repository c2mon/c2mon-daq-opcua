# Changelog
All notable changes to this project will be documented in this file.
Project: c2mon-daq-opcua

## [1.10.3] - 2022-01-17 
### Changed
 - LSR-2356 when the configuration succeeds, but the subscription to the device server fails, the configuration call now returns 		REBOOT and no longer FAILURE

## [1.10.2] - 2021-12-13 
### Changed
 - LSR-2299 invert algorithm to find namespace, i.e. test all namespace and take the id of the one fitting best
 			instead of searching based on the ':' separator.


***end of document***  
