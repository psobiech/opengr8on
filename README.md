[![Publish](https://github.com/psobiech/opengr8on/actions/workflows/publish.yml/badge.svg)](https://github.com/psobiech/opengr8on/actions/workflows/publish.yml) ![jacoco.svg](badges%2Fjacoco.svg)

# Virtual CLU

As of now, the VCLU is complete enough to be able to check out how the Grenton system / Object Manager is programmed, before buying the device itself.

Just run VCLU docker image (or multiple images on different devices, just adjust the serial numbers) in the same network as computer running Grenton Object
Manager (https://grentonsmarthome.github.io/release-en/om/).

What works:

- Most of OM integration and LUA scripting (Control, Events, Embedded features, User features, LUA Scripting)
- Communication between CLU and CLU (accessing variables from other CLUs) - even physical ones
- Tested under linux/amd64 and linux/arm64 (on Raspberry PI4)

Does not work:

- No known issues

TODOs:

- implement missing objects
- measurements
- persistent storage
- create fortified mode (when CLU does not accept new keys or commands using default keys)

## Virtual Objects

### MqttTopic (and mosquitto configuration)

[MQTT.md](MQTT.md)

### Timer

### HttpRequest

## Example Client
[client/README.md](client%2FREADME.md)

## Quickstart

* Assuming OM is extracted in $OM_HOME (https://grentonsmarthome.github.io/release-en/om/).

1. Copy all VCLU [device-interfaces](runtime%2Fdevice-interfaces) to `$OM_HOME/configuration/com.grenton.om/device-interfaces/`
1. Restart/Launch OM or Reload Device Interfaces
1. Clone ./runtime directory from this repository
1. Run Virtual CLU (eg. `docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest eth0` - assuming
   eth0 is your network interface name - you can specify also local IP address)
1. Start OM Discovery
1. When prompted for KEY type: `00000000`
   ![vclu_sn.png](docs%2Fimg%2Fvclu_sn.png)
1. Virtual CLU should be available like normal CLU (you might see errors regarding IP address, but they can be ignored for now):
   ![vclu_discover.png](docs%2Fimg%2Fvclu_discover.png)
   ![vclu_features.png](docs%2Fimg%2Fvclu_features.png)

# Build

## Docker

> docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest eth0

or

> docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest 192.168.31.44

## Local

> mvn package

> java -jar vclu/target/vclu.jar eth0

or

> java -jar vclu/target/vclu.jar 192.168.31.44

# Licenses

Documentation (docs/ directory) is under CC BY-SA 4.0 license.

Datasheets are owned by their respective owners.

All other code is licensed under AGPLv3.

# Disclaimer

This project is not endorsed by, directly affiliated with, maintained, authorized, or sponsored by Grenton Sp. z o.o.

The use of any trade name or trademark is for identification and reference purposes only and does not imply any association with the trademark holder of their
product brand.

Any product names, logos, brands, and other trademarks or images featured or referred to within this page are the property of their respective trademark
holders.

Unless expressly stated otherwise, the person who associated a work with this deed makes no warranties about the work, and disclaims liability for all uses of
the work, to the fullest extent permitted by applicable law.
