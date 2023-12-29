# Generate Certificates

```
mkdir -p mqtt/config mqtt/data mqtt/log
cd mqtt
git clone https://github.com/fcgdam/easy-ca.git
cd easy-ca
./create-root-ca -d ../config/certs
cd ../config/certs
./bin/create-server -s localhost #or specify your MQTT hostname
./bin/create-client -c clu0 -n clu0
./bin/create-client -c user1 -n user1
```

# Configure mosquitto

## ./mqtt/config/mosquitto.conf
```
persistence true
persistence_location /mosquitto/data/
log_dest stdout

per_listener_settings true

# MQTT over TLS/SSL
listener 8883
cafile /mosquitto/config/certs/ca/ca.crt
certfile /mosquitto/config/certs/certs/localhost.server.crt
keyfile /mosquitto/config/certs/private/localhost.server.key
require_certificate true
allow_anonymous false
tls_version tlsv1.2 

# WebSockets over TLS/SSL
listener 9883
protocol websockets
cafile /mosquitto/config/certs/ca/ca.crt
certfile /mosquitto/config/certs/certs/localhost.server.crt
keyfile /mosquitto/config/certs/private/localhost.server.key
require_certificate true
allow_anonymous false
tls_version tlsv1.2 
```

## ./mqtt/docker-compose.yml
```
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
```
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

```
mosquitto_pub --cafile ./mqtt/config/certs/ca/ca.crt -h localhost -t "topic" -m "message" -p 8883 -d --cert ./mqtt/config/certs/certs/user1.client.crt --key ./mqtt/config/certs/private/user1.client.key
```

# Configure VCLU
Copy certificates and CLU private key into CLU runtime directory
```
cp ./mqtt/config/certs/ca/ca.crt ./runtime/root/a/MQTT-ROOT.CRT``
cp ./mqtt/config/certs/certs/clu0.client.crt ./runtime/root/a/MQTT-PUBLIC.CRT
cp ./mqtt/config/certs/private/clu0.client.key ./runtime/root/a/MQTT-PRIVATE.PEM
```

Run VCLU and enable UseMQTT in OM.