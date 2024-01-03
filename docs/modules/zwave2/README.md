# Service Port

The CLU has an exposed 3 pin (2.54mm) header that contains ground and UART pins.

When the CLU is facing forward the pinouts are as follows (GND is the closest to the Grenton logo): GND, TX, RX (?)

The UART voltage level is 3V3.

Here are the logs from CLU bootup sequence:
[logs/bootloader.log](logs/bootloader.log)

# Images

![clu_zwave2_base.JPG](img%2Fclu_zwave2_base.JPG)

![clu_zwave2_mcu.JPG](img%2Fclu_zwave2_mcu.JPG)

# Logs

[boot.log](logs%2Fboot.log)
[firmware.log](logs%2Ffirmware.log)
[telnet_port_24.log](logs%2Ftelnet_port_24.log)

# Datasheets

# Interfaces

[clu_ZWAVE_2_ft00000003_fv200_ht00000013_hv00000001.xml](interfaces%2Fclu_ZWAVE_2_ft00000003_fv200_ht00000013_hv00000001.xml)
[object_calendar_v1.xml](interfaces%2Fobject_calendar_v1.xml)
[object_event_scheduler_v1.xml](interfaces%2Fobject_event_scheduler_v1.xml)
[object_PIDcontroller_v1.xml](interfaces%2Fobject_PIDcontroller_v1.xml)
[object_presence_sensor_v2.xml](interfaces%2Fobject_presence_sensor_v2.xml)
[object_push_v1.xml](interfaces%2Fobject_push_v1.xml)
[object_scheduler_v1.xml](interfaces%2Fobject_scheduler_v1.xml)
[object_sunrise_sunset_calendar_v3.xml](interfaces%2Fobject_sunrise_sunset_calendar_v3.xml)
[object_thermostat_v2.xml](interfaces%2Fobject_thermostat_v2.xml)
[object_timer_v1.xml](interfaces%2Fobject_timer_v1.xml)