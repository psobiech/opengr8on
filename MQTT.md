# Configure VCLU

## Plain text

Configure MQTTUrl, eg. tcp://user:pass@localhost:1883

## TLS enabled

Copy certificates and CLU private key into CLU runtime directory

```bash
cp ./easy-rsa/easyrsa3/pki/ca.crt ./runtime/mqtt/ca.crt
cp ./easy-rsa/easyrsa3/pki/issued/clu0.crt ./runtime/mqtt/certificate.crt # required only if using client certificate authentication
cp ./easy-rsa/easyrsa3/pki/private/clu0.key ./runtime/mqtt/key.pem # required only if using client certificate authentication
```

Configure MQTTUrl, eg. ssl://user:pass@localhost:8883 (or ssl://localhost:8883 if using client certificate authentication)
Run VCLU and enable UseMQTT in OM.

## MqttTopic

Example publish:

```lua
CLU1703856280877->myTopic->Publish("topic", "message")
```

Example onInit script with auto subscription:

```lua
-- subscribe to the topic (supports MQTT topic patterns)
CLU1703856280877->myTopic->Subscribe("zigbee2mqtt/#")
```

Example onMessage script:

```lua
-- read current message message
CLU1703856280877->AddToLog(CLU1703856280877->myTopic->Topic .. ": " .. CLU1703856280877->myTopic->Message)

-- publish the same message to some other topic
CLU1703856280877->myTopic->Publish("innytopic", CLU1703856280877->myTopic->Message)

-- unblock next message in the queue
CLU1703856280877->myTopic->NextMessage()
```

# Configure MQTT broker

The broker should be compatible with Tasmota32 https://tasmota.github.io/docs/TLS/#tls-secured-mqtt and AWS IoT

## Generate Certificates

```bash
git clone git@github.com:OpenVPN/easy-rsa.git
```

## ./easy-rsa/easyrsa3/vars

```bash
set_var EASYRSA_REQ_COUNTRY   "US"
set_var EASYRSA_REQ_PROVINCE  "California"
set_var EASYRSA_REQ_CITY      "San Francisco"
set_var EASYRSA_REQ_ORG       "Copyleft Certificate Co"
set_var EASYRSA_REQ_EMAIL     "me@example.net"
set_var EASYRSA_REQ_OU        "My Organizational Unit"

set_var EASYRSA_NO_PASS	1

set_var EASYRSA_KEY_SIZE	2048
set_var EASYRSA_DIGEST		"sha256"

# for TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 / ECDHE-RSA-AES128-GCM-SHA256
set_var EASYRSA_ALGO		rsa

# for TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 / ECDHE-ECDSA-AES128-GCM-SHA256
#set_var EASYRSA_ALGO		ec
#set_var EASYRSA_CURVE		secp521r1

set_var EASYRSA_CA_EXPIRE	3650
set_var EASYRSA_CERT_EXPIRE	3650
```

```bash
cd easyrsa3

./easyrsa init-pki
./easyrsa build-ca

# change subject to your mqtt broker hostname
./easyrsa gen-req localhost
./easyrsa sign-req server localhost

# vclu certificate 
./easyrsa gen-req clu0
./easyrsa sign-req client clu0

# example user certificate
./easyrsa gen-req user0
./easyrsa sign-req client user0
```

# Configure mosquitto

```bash
mkdir -p ./mqtt/config/certs ./mqtt/data ./mqtt/log
cp ./easy-rsa/easyrsa3/pki/ca.crt ./mqtt/config/certs/
cp ./easy-rsa/easyrsa3/pki/issued/localhost.crt ./mqtt/config/certs/
cp ./easy-rsa/easyrsa3/pki/private/localhost.key ./mqtt/config/certs/
```

## ./mqtt/config/mosquitto.conf

```properties
persistence true
persistence_location /mosquitto/data/
log_dest stdout

per_listener_settings true

# Plain MQTT
#listener 1883
#allow_anonymous false
#password_file /mosquitto/config/passwd

# MQTT over TLS/SSL
listener 8883
cafile /mosquitto/config/certs/ca.crt
certfile /mosquitto/config/certs/localhost.crt
keyfile /mosquitto/config/certs/localhost.key
allow_anonymous false
require_certificate false
#password_file /mosquitto/config/passwd
tls_version tlsv1.2
ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256

# WebSockets over TLS/SSL
listener 9883
protocol websockets
cafile /mosquitto/config/certs/ca.crt
certfile /mosquitto/config/certs/localhost.crt
keyfile /mosquitto/config/certs/localhost.key
allow_anonymous false
require_certificate false
#password_file /mosquitto/config/passwd
tls_version tlsv1.2
ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256
```

## ./mqtt/docker-compose.yml

```yaml
version: '3.8'

services:
    mosquitto:
        image: eclipse-mosquitto:2
        ports:
            - 8883:8883
            - 9883:9883
        volumes:
            - ./config:/mosquitto/config
            - ./data:/mosquitto/data
            - ./log:/mosquitto/log
        networks:
            - mosquitto
networks:
    mosquitto:
        name: mosquitto
        driver: bridge
```

## Run MQTT broker

```bash
❯ docker-compose up
[+] Running 1/0
 ✔ Container mqtt-mosquitto-1  Created                                                              0.0s 
Attaching to mosquitto-1
mosquitto-1  | 1703839960: mosquitto version 2.0.18 starting
mosquitto-1  | 1703839960: Config loaded from /mosquitto/config/mosquitto.conf.
mosquitto-1  | 1703839960: Opening ipv4 listen socket on port 8883.
mosquitto-1  | 1703839960: Opening ipv6 listen socket on port 8883.
mosquitto-1  | 1703839960: Opening websockets listen socket on port 9883.
mosquitto-1  | 1703839960: mosquitto version 2.0.18 running
```

## Test MQTT

```bash
mosquitto_pub --cafile ./easy-rsa/easyrsa3/pki/ca.crt -h localhost -t "topic" -m "test_message" -p 8883 -d --cert ./easy-rsa/easyrsa3/pki/issued/user0.crt --key ./easy-rsa/easyrsa3/pki/private/user0.key
```
