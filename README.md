[![GitHub Release](https://img.shields.io/github/v/release/psobiech/opengr8on?label=release)](https://github.com/psobiech/opengr8on/releases)
[![Docker Image Version (latest semver)](https://img.shields.io/docker/v/psobiech/opengr8on?sort=semver&label=docker%20version)](https://github.com/psobiech/opengr8on/pkgs/container/opengr8on)
[![Docker Image Size with architecture (latest by date/latest semver)](https://img.shields.io/docker/image-size/psobiech/opengr8on?label=docker%20size)](https://github.com/psobiech/opengr8on/pkgs/container/opengr8on)
![jacoco.svg](badges%2Fjacoco.svg)
[![AGPLv3 License](https://img.shields.io/badge/license-AGPL-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)

# Virtual CLU

As of now, the VCLU is complete enough to be able to check out how the Grenton system / Object Manager is programmed, before buying the device itself.

Just run VCLU docker image (or multiple images on different devices, just adjust the serial numbers) in the same network as computer running Grenton Object
Manager (https://grentonsmarthome.github.io/release-en/om/).

What works:

- Most of OM integration and LUA scripting (Control, Events, Embedded features, User features, LUA Scripting)
- Communication between CLU and CLU (accessing variables from other CLUs) - even physical ones
- Tested under linux/amd64 and linux/arm64 (on Raspberry PI4), unit tests now work also on Windows

Does not work:

- No known issues

TODOs:

- implement missing objects
- measurements
- create fortified mode (when CLU does not accept new keys or commands using default keys)

## Virtual Objects

Object behaviour may differ from physical CLU ones, in some cases its intentional. 

### MqttTopic

Implemented: Yes

* Automatically converts JSON from/to Lua Tables

[MQTT.md](modules/MQTT.md) (+ mosquitto broker configuration)

### Timer

Implemented: Yes

### HttpRequest

Implemented: Yes

* Automatically converts JSON/XML/FROM_DATA from/to Lua Tables, depending on Content-Type headers

### HttpListener

Implemented: Yes

* Automatically converts JSON/XML/FROM_DATA from/to Lua Tables, depending on Content-Type headers
* Might add TLS support, currently server binds on port 80

### Calendar

Implemented: No

### Scheduler

Implemented: No

### SunriseSunsetCalendar

Implemented: No

### PresenceSensor

Implemented: No

### EventScheduler

Implemented: No

## TFTP

[TFTP.md](modules%2FTFTP.md)

## Example Client

[CLIENT.md](modules%2FCLIENT.md)

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
1. Virtual CLU should be available like normal CLU (you might see errors regarding IP address assignment, they can be ignored - since we cannot change IP address from an application):
   ![vclu_discover.png](docs%2Fimg%2Fvclu_discover.png)
   ![vclu_features.png](docs%2Fimg%2Fvclu_features.png)

# Build

## Docker

Host networking is required, since Grenton protocol requires broadcast packets. 

> docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest eth0

or

> docker run --net host --mount type=bind,source=./runtime,target=/opt/docker/runtime ghcr.io/psobiech/opengr8on:latest 192.168.31.44

## Local

> mvn package -Dmaven.test.skip=true

> java -jar vclu/target/vclu.jar eth0

or

> java -jar vclu/target/vclu.jar 192.168.31.44

## Port binding permission issues (Linux, it's not required for containers)

The application requires to bind to port TFTP 69, which on Linux is a protected port (<1024).
To bypass this limitation and not need to run the application as root, you can:

### Change protected port range (recommended)

```bash
# Enables binding to any port by any application - does not persist after reboot
echo 0 | sudo tee /proc/sys/net/ipv4/ip_unprivileged_port_start
```

### Allow all java applications to bind on protected ports

```bash
# Enables binding for all java applications to any port - java updates will clear the flag
sudo setcap 'cap_net_bind_service=+ep' "$JAVA_HOME/bin/java"
```

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
