# CLU Example Client

## Discover CLUs (and assign random PROJECT KEY)

```bash
java -jar client.jar -di "./runtime/device-interfaces" -i wlp7s0 --discover -l 1
```

Output:

```
2024-01-06T21:20:39,367Z [DEBUG] p.p.o.c.Main - Using network interface: NetworkInterfaceDto{networkInterface=wlp7s0 (wlp7s0) ['.....- # 8C1D 96EE FC2D # jB2W7vwt'], address=192.168.31.39::0xC0A81F27, broadcastAddress=192.168.255.255::0xC0A8FFFF, networkAddress=192.168.0.0::0xC0A80000, networkMask=255.255.0.0::0xFFFF0000}
2024-01-06T21:20:39,490Z [DEBUG] p.p.o.x.i.InterfaceRegistry - Loaded Interfaces: clus: 1, modules: 0, objects: 1
2024-01-06T21:20:39,546Z [DEBUG] p.p.o.c.Main - Generated random project key: CipherKey{key=p3iinuks33viFFSdPtZfpA==, iv=sbULfeV/RipaJJD3a6Uq1Q==}
2024-01-06T21:20:40,567Z [DEBUG] p.p.o.c.Main - Discovered device: GrentonDevice{name=CLU0, serialNumber=0::0x00, macAddress=0eaa55aa55aa, address=192.168.31.39::0xC0A81F27, cipherType=NONE, iv='...)...?....h@.P # 20C9 EE29 860F 843F B4F8 02AD 6840 1F50 # IMnuKYYPhD+0+AKtaEAfUA==', privateKey='00000000 # 3030 3030 3030 3030 # MDAwMDAwMDA=', cipherKey=CipherKey{key=iZ/Tt8kpk+OQ56ruviGRJg==, iv=IMnuKYYPhD+0+AKtaEAfUA==}}
2024-01-06T21:20:43,599Z [INFO] p.p.o.c.Main - GrentonDevice{name=CLU0, serialNumber=0::0x00, macAddress=0eaa55aa55aa, address=192.168.31.39::0xC0A81F27, cipherType=NONE, iv='...)...?....h@.P # 20C9 EE29 860F 843F B4F8 02AD 6840 1F50 # IMnuKYYPhD+0+AKtaEAfUA==', privateKey='00000000 # 3030 3030 3030 3030 # MDAwMDAwMDA=', cipherKey=CipherKey{key=iZ/Tt8kpk+OQ56ruviGRJg==, iv=IMnuKYYPhD+0+AKtaEAfUA==}}
2024-01-06T21:20:43,600Z [INFO] p.p.o.c.Main - CLUDeviceConfig{macAddress=0eaa55aa55aa, tfBusDevices=[]} DeviceConfig{serialNumber=0::0x00, hardwareType=19::0x13, hardwareVersion=1::0x01, firmwareType=3::0x03, firmwareVersion=11163050::0xAA55AA, status=OK}
2024-01-06T21:20:43,601Z [INFO] p.p.o.c.Main - CLU_VIRTUAL_OPENGR8ON
```

## Discover CLUs (loading PROJECT KEY from file)

```bash
java -jar client.jar -di "./runtime/device-interfaces" -pr "$OM_HOME/projects/xxxx.omp" -i wlp7s0 --discover -l 1
```

Output:

```
2024-01-06T21:25:44,139Z [DEBUG] p.p.o.c.Main - Using network interface: NetworkInterfaceDto{networkInterface=wlp7s0 (wlp7s0) ['.....- # 8C1D 96EE FC2D # jB2W7vwt'], address=192.168.31.39::0xC0A81F27, broadcastAddress=192.168.255.255::0xC0A8FFFF, networkAddress=192.168.0.0::0xC0A80000, networkMask=255.255.0.0::0xFFFF0000}
2024-01-06T21:25:44,263Z [DEBUG] p.p.o.x.i.InterfaceRegistry - Loaded Interfaces: clus: 1, modules: 0, objects: 1
2024-01-06T21:25:44,317Z [DEBUG] p.p.o.x.o.OmpReader - Loaded project key: CipherKey{key=vd/TDJU8awHhOBn6h3YsxQ==, iv=oykoaQgbuuhYOnE9CUdrnQ==}
2024-01-06T21:25:46,622Z [DEBUG] p.p.o.c.Main - Discovered device: GrentonDevice{name=CLU0, serialNumber=0::0x00, macAddress=0eaa55aa55aa, address=192.168.31.39::0xC0A81F27, cipherType=NONE, iv='.6..V.%......!&. # AB36 EBF0 561F 2595 0D0F 0608 9F21 2612 # qzbr8FYfJZUNDwYInyEmEg==', privateKey='00000000 # 3030 3030 3030 3030 # MDAwMDAwMDA=', cipherKey=CipherKey{key=iZ/Tt8kpk+OQ56ruviGRJg==, iv=qzbr8FYfJZUNDwYInyEmEg==}}
2024-01-06T21:25:49,653Z [INFO] p.p.o.c.Main - GrentonDevice{name=CLU0, serialNumber=0::0x00, macAddress=0eaa55aa55aa, address=192.168.31.39::0xC0A81F27, cipherType=NONE, iv='.6..V.%......!&. # AB36 EBF0 561F 2595 0D0F 0608 9F21 2612 # qzbr8FYfJZUNDwYInyEmEg==', privateKey='00000000 # 3030 3030 3030 3030 # MDAwMDAwMDA=', cipherKey=CipherKey{key=iZ/Tt8kpk+OQ56ruviGRJg==, iv=qzbr8FYfJZUNDwYInyEmEg==}}
2024-01-06T21:25:49,654Z [INFO] p.p.o.c.Main - CLUDeviceConfig{macAddress=0eaa55aa55aa, tfBusDevices=[]} DeviceConfig{serialNumber=0::0x00, hardwareType=19::0x13, hardwareVersion=1::0x01, firmwareType=3::0x03, firmwareVersion=11163050::0xAA55AA, status=OK}
2024-01-06T21:25:49,654Z [INFO] p.p.o.c.Main - CLU_VIRTUAL_OPENGR8ON
```

## Fetch CLU information (by known IP, loading PROJECT KEY from file)

```bash
java -jar client.jar -di "./runtime/device-interfaces" -pr "$OM_HOME/projects/xxxx.omp" -i wlp7s0 -a 192.168.31.39 --fetch
```

Output:

```
2024-01-06T21:27:05,536Z [DEBUG] p.p.o.c.Main - Using network interface: NetworkInterfaceDto{networkInterface=wlp7s0 (wlp7s0) ['.....- # 8C1D 96EE FC2D # jB2W7vwt'], address=192.168.31.39::0xC0A81F27, broadcastAddress=192.168.255.255::0xC0A8FFFF, networkAddress=192.168.0.0::0xC0A80000, networkMask=255.255.0.0::0xFFFF0000}
2024-01-06T21:27:05,658Z [DEBUG] p.p.o.x.i.InterfaceRegistry - Loaded Interfaces: clus: 1, modules: 0, objects: 1
2024-01-06T21:27:05,710Z [DEBUG] p.p.o.x.o.OmpReader - Loaded project key: CipherKey{key=vd/TDJU8awHhOBn6h3YsxQ==, iv=oykoaQgbuuhYOnE9CUdrnQ==}
2024-01-06T21:27:05,752Z [DEBUG] p.p.o.c.Main - GrentonDevice{name=CLU0, serialNumber=0::0x00, macAddress=0eaa55aa55aa, address=192.168.31.39::0xC0A81F27, cipherType=PROJECT, iv=null, privateKey=null, cipherKey=null}
2024-01-06T21:27:05,753Z [DEBUG] p.p.o.c.Main - CLUDeviceConfig{macAddress=0eaa55aa55aa, tfBusDevices=[]} DeviceConfig{serialNumber=0::0x00, hardwareType=19::0x13, hardwareVersion=1::0x01, firmwareType=3::0x03, firmwareVersion=11163050::0xAA55AA, status=OK}
2024-01-06T21:27:05,753Z [DEBUG] p.p.o.c.Main - CLU_VIRTUAL_OPENGR8ON
```

## Execute remote LUA script (by known IP, loading PROJECT KEY from file)

```bash
java -jar client.jar -di "./runtime/device-interfaces" -pr "$OM_HOME/projects/xxxx.omp" -i wlp7s0 -a 192.168.31.39 --execute "HTT5497:get(0)"
```

Output:

```
2024-01-06T21:30:55,637Z [DEBUG] p.p.o.c.Main - Using network interface: NetworkInterfaceDto{networkInterface=wlp7s0 (wlp7s0) ['.....- # 8C1D 96EE FC2D # jB2W7vwt'], address=192.168.31.39::0xC0A81F27, broadcastAddress=192.168.255.255::0xC0A8FFFF, networkAddress=192.168.0.0::0xC0A80000, networkMask=255.255.0.0::0xFFFF0000}
2024-01-06T21:30:55,783Z [DEBUG] p.p.o.x.o.OmpReader - Loaded project key: CipherKey{key=vd/TDJU8awHhOBn6h3YsxQ==, iv=oykoaQgbuuhYOnE9CUdrnQ==}
2024-01-06T21:30:56,761Z [INFO] p.p.o.c.Main - http://127.0.0.1
```
