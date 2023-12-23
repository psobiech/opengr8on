[![Maven](https://github.com/psobiech/opengr8on/actions/workflows/maven.yml/badge.svg)](https://github.com/psobiech/opengr8on/actions/workflows/maven.yml)

# Disclaimer

This project is not endorsed by, directly affiliated with, maintained, authorized, or sponsored by Grenton Sp. z o.o.

The use of any trade name or trademark is for identification and reference purposes only and does not imply any association with the trademark holder of their product brand.

Any product names, logos, brands, and other trademarks or images featured or referred to within this page are the property of their respective trademark holders.

Unless expressly stated otherwise, the person who associated a work with this deed makes no warranties about the work, and disclaims liability for all uses of the work, to the fullest extent permitted by applicable law.

# Requirements
Java 21

# TFTP
It seems that the CLU FTP server is not RFC compliant, this is why we forked the commons-net TFTP library to revert the fix from commons-net (https://issues.apache.org/jira/browse/NET-414), that is breaking compatibility with CLUs.

# Virtual CLU

## Local
> mvn package
> 
> java -jar vclu/target/vclu.jar eth0

## Docker

> docker build . --target app-runtime -t vclu:latest
>
> docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime vclu:latest eth0

## Quickstart

* Assuming OM is extracted in $OM_DIR (https://grentonsmarthome.github.io/release-en/om/).

1. Copy [clu_VIRTUAL_ft00000001_fv001_htaa55aa55_hv00000001.xml](runtime%2Fdevice-interfaces%2Fclu_VIRTUAL_ft00000001_fv001_htaa55aa55_hv00000001.xml) to `$OM_DIR/configuration/com.grenton.om/device-interfaces/clu_VIRTUAL_ft00000001_fv001_htaa55aa55_hv00000001.xml`
1. Restart/Launch OM
1. Run Virtual CLU (eg. `docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest eth0` - assuming eth0 is your network interface name)
1. Start OM Discovery
1. When prompted for KEY type: `00000000`
![vclu_sn.png](docs%2Fimg%2Fvclu_sn.png)
1. Virtual CLU should be available like normal CLU: 
![vclu_discover.png](docs%2Fimg%2Fvclu_discover.png)
![vclu_features.png](docs%2Fimg%2Fvclu_features.png)


What works:
- Most of OM integration and LUA scripting (Control, Events, Embedded features, User features, LUA Scripting)
- Communication between CLU and CLU (accessing variables from other CLUs)

Does not work:
- No virtual objects are implemented yet
- VCLU does not preserve keys on restarts (requires discovery every time, unless you use the hardcoded defaults)
- If discovery is interrupted, VCLU requires restart (some key management issue?)
- Only tested under Linux
- No error handling - LUA errors sometimes are silently dropped

TODOs:
- most of the code requires refactoring
- support binding via IP instead of network interface name

# Licenses
TFTP (tfp/ directory) is licensed under Apache 2.0 (as it is a copy of commons-net implementation)

Documentation (docs/ directory) is under CC BY-SA 4.0 license.

Datasheets are owned by their respective owners.

All other code is licensed under GPLv3.
