# Gate Hardware

I currently only own MODBUS and HTTP gates, but I assume all should be true for the ALARM gate as well.

The module is divided into two PCB's, the bottom ones are marked: `CLU_Gate_Bottom_v15` and contains connectors, voltage regulator and protection circuits.

The top PCB is marked as `CLU_GATE_Main_v14` and it contains an ESP32-WROVER-B module, modbus transceiver, ethernet transceiver, ethernet connector and a green
LED.  
In both cases MODBUS and HTTP gates are completely identical hardware wise.
To the point that even the HTTP gate also has ST3485EB RS-485 MODBUS transceiver. So technically each of them could work as any gate type (even at the same
time) with only software change (maybe even at the same time!).

Even software updates are identical for each of the gates. Capabilities are limited somehow using eFuses or using memory that is not updated during normal
firmware update (it might be write protected).
I tried using HTTP features on the MODBUS gate, but it was throwing LUA errors, so there needs to be some kind of software lock on the features.

SHA256 sums of firmware updates for the modules in the same version:

```
834eb9e88ad4e2ffb5bbd93fe4fa66e5858d082b35eaa9c83bbe8dd2b2c4b751  CLU_GATE_ALARM-18-2-2-1.1.10-2140.fw
834eb9e88ad4e2ffb5bbd93fe4fa66e5858d082b35eaa9c83bbe8dd2b2c4b751  CLU_GATE_HTTP-18-2-3-1.1.10-2140.fw
834eb9e88ad4e2ffb5bbd93fe4fa66e5858d082b35eaa9c83bbe8dd2b2c4b751  CLU_GATE_MODBUS-18-2-1-1.1.10-2140.fw
```

# Debug Port

The gate has exposed pins for what seems like a 2x5 pin debug port.

# Datasheets

[8720a.pdf](datasheets%2F8720a.pdf)
[esp32-wrover-b_datasheet_en.pdf](datasheets%2Fesp32-wrover-b_datasheet_en.pdf)
[st3485eb.pdf](datasheets%2Fst3485eb.pdf)

# Interfaces

[clu_GATE_HTTP_ft00000003_fv00000456_ht00000012_hv00000002.xml](http%2Finterfaces%2Fclu_GATE_HTTP_ft00000003_fv00000456_ht00000012_hv00000002.xml)
[object_gate_timer_v2.xml](http%2Finterfaces%2Fobject_gate_timer_v2.xml)
[object_http_listener_v1.xml](http%2Finterfaces%2Fobject_http_listener_v1.xml)
[object_http_request_v1.xml](http%2Finterfaces%2Fobject_http_request_v1.xml)

[clu_GATE_MODBUS_ft00000001_fv00000456_ht00000012_hv00000002.xml](modbus%2Finterfaces%2Fclu_GATE_MODBUS_ft00000001_fv00000456_ht00000012_hv00000002.xml)
[object_modbus_v2.xml](modbus%2Finterfaces%2Fobject_modbus_v2.xml)
[object_modbus_val_v1.xml](modbus%2Finterfaces%2Fobject_modbus_val_v1.xml)