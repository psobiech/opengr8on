# Discover CLU(s) / CLU Initialization

All commands below are using port `1234/udp`.

Local machine IP is 10.72.144.72/16.

1. Use previously saved AES key and IV or generate new random AES Key and IV (both 16 bytes long) - we will call them OWN_KEY and OWN_IV
1. Broadcast the `req_discovery_clu` port using broadcast addresses in the network (eg. 255.255.255.255).
   (The request itself is encrypted using Grenton special KEY/IV.)
   ```
   REQUEST:
   <AES(GRENTON_KEY, OWN_IV, randomBytes(30))>:<OWN_IV>:req_discovery_clu:<HOST_IP_ADDRESS>
   eg. <BINARY_32>:<BINARY_16>:req_discovery_clu:10.72.144.72
   ```
1. Listen for responses from CLUs (`resp_discovery_clu`)
   (The response itself is encrypted using Grenton special KEY, but with IV provided in the discovery request - OWN_IV)
   ```
   RESPONSE:
   <AES(CLU_KEY, CLU_IV, HASH(REQUEST->randomBytes))>:<CLU_IV>:resp_discovery_clu:<CLU_SERIAL_NUMBER_HEX>:<CLU_MAC_ADDRESS>
   eg. <BINARY_32>:<BINARY_16>:resp_discovery_clu:0dxxxxxx:80XXXXXXXXXX
   ```
    * Detect if CLU has proper AES/IV key already configured if not then calculate default AES key using private key (using KEY from the sticker on the CLU), IV
      is randomized by CLU on reset
1. Broadcast the command `req_set_clu_ip` to set the IP address of the device
   (The response itself is encrypted using Grenton special KEY, but with IV provided in the discovery request - OWN_IV)
   ```
   REQUEST:
   req_set_clu_ip:<CLU_SERIAL_NUMBER_HEX>:<NEW_CLU_IP_ADDRESS>:<NEW_GATEWAY_IP_ADDRESS>
   eg. req_set_clu_ip:0dxxxxxx:10.72.144.1:10.72.72.1
   
   RESPONSE:
   resp_set_clu_ip:<CLU_SERIAL_NUMBER_HEX>:<NEW_CLU_IP_ADDRESS>
   eg. resp_set_clu_ip:0dxxxxxx:10.72.144.1
   ```
1. Send command `req_set_key` to overwrite the key
   (The request and response are encrypted using CLU KEY / IV from now on all messages will be encrypted using this key)
      ```
      REQUEST:
      <AES(OWN_KEY, OWN_IV, randomBytes(30))>:<OWN_IV>:req_set_key:<OWN_KEY>
      eg. <BINARY_32>:<BINARY_16>:req_set_key:<BINARY_16>

      RESPONSE:
      resp:OK 
      ```
1. Send the command `req_start_ftp` to enable TFTP server on the CLU (if necessary this command is repeated before _every_ file transfer)
   ```
   REQUEST:
   req_start_ftp
   
   RESPONSE:
   resp:OK
   ```
1. TFTP Download a:\config.txt and a:\CONFIG.JSON files that contains versions of hardware and firmware, plus some other information
1. Load device interfaces for the CLU (using the hardware and software versions from config.txt and/or CONFIG.JSON)
1. Download a:\OM.LUA, a:\MAIN.LUA, a:\USER.LUA
1. Parse contents of the LUA files and check for errors / outdated features / etc or clean them.
1. Generate new contents of LUA files
1. Upload a:\MAIN.LUA, a:\OM.LUA, a:\USER.LUA
1. Send the command `req_reset` to reset the CLU and use the new scripts
   ```
   REQUEST:
   req_reset:<HOST_IP_ADDRESS>
   eg. req_reset:10.72.144.72
   
   RESPONSE:
   resp_reset:<CLU_IP_ADDRESS>
   eg. resp_reset:10.72.144.8
   ```

# Check Alive

   ```
   REQUEST:
   req:<HOST_IP_ADDRESS>:<randomCharacters(8)>:checkAlive()
   eg. req:10.72.144.72:003649:checkAlive()
  
   RESPONSE CLU:
   resp:<CLU_IP_ADDRESS>:<REQUEST->randomCharacters>:<CLU_SERIAL_NUMBER_HEX>
   eg. resp:10.72.144.1:00003649:<CLU_SERIAL_NUMBER_HEX>
   
   RESPONSE GATE:
   resp:<CLU_IP_ADDRESS>:<REQUEST->randomCharacters>:true
   eg. resp:10.72.144.1:000082b2:true
   
   RESPONSE CLU/GATE in Emergency mode:
   resp:<CLU_IP_ADDRESS>:<REQUEST->randomCharacters>:emergency
   eg. resp:10.72.144.1:00004b35:emergency
   ```

OM us using only 6 random characters, but CLU always responds using 8 characters (left padding with zero) - the communication seems to work correctly if using 8
characters for both request and response.

In fact this command can be used to execute any LUA function declared in the script (including communication between multiple CLUs).

## Fetch Data (Bulk)

This command effectively executes the following LUA functions:

* SYSTEM:clientRegister()
* SYSTEM:clientDestroy()

It seems to subscribe for some specific feature value changes, that are then pushed by the CLU into the specified IP/PORT every client report interval.
The default port is 4344.

req:10.72.144.72:000616:SYSTEM:clientRegister("10.72.144.72"
,4344,49064,{{CLU22XXXXXXX,0},{CLU22XXXXXXX,1},{CLU22XXXXXXX,2},{CLU22XXXXXXX,3},{CLU22XXXXXXX,5},{CLU22XXXXXXX,6},{CLU22XXXXXXX,7},{CLU22XXXXXXX,8},{CLU22XXXXXXX,9},{CLU22XXXXXXX,10},{CLU22XXXXXXX,11},{CLU22XXXXXXX,12},{CLU22XXXXXXX,13},{CLU22XXXXXXX,17},{CLU22XXXXXXX,18},{CLU22XXXXXXX,19},{CLU22XXXXXXX,20},{CLU22XXXXXXX,21},{CLU22XXXXXXX,22},{CLU22XXXXXXX,23},{CLU22XXXXXXX,24},{CLU22XXXXXXX,25},{CLU22XXXXXXX,26},{CLU22XXXXXXX,27},{CLU22XXXXXXX,28},{CLU22XXXXXXX,29},{CLU22XXXXXXX,30},{CLU22XXXXXXX,31}})
resp:10.72.144.1:00000616:clientReport:49064:{27,nil,1,true,"2023-12-05","00:20:47",5,12,2023,2,0,20,1701735647,"05.12.01-2330",false,false,50,230,"
tempus1.gum.gov.pl",0,0,"8.8.8.8","8.8.4.4",24.09,0.50,25,23,0}
resp:10.72.144.1:00000000:clientReport:49064:{31,nil,1,true,"2023-12-05","00:20:52",5,12,2023,2,0,20,1701735652,"05.12.01-2330",false,false,50,230,"
tempus1.gum.gov.pl",0,0,"8.8.8.8","8.8.4.4",24.14,0.50,25,23,0}
req:10.72.144.72:004716:SYSTEM:clientDestroy("10.72.144.72",4344,49064)
resp:10.72.144.1:00004716:49064

req:10.72.144.72:00f0d4:SYSTEM:clientRegister("10.72.144.72",4344,3903,{{PAN9821,1},{PAN9821,2},{PAN9821,3},{PAN9821,4},{PAN9821,0}})
resp:10.72.144.1:0000f0d4:clientReport:3903:{0.50,500,0,100,0.50}
resp:10.72.144.1:00000000:clientReport:3903:{0.50,500,0,100,1.90}
resp:10.72.144.1:00000000:clientReport:3903:{0.50,500,0,100,0.70}
req:10.72.144.72:00b9fa:SYSTEM:clientDestroy("10.72.144.72",4344,3903)
resp:10.72.144.1:0000b9fa:3903

# Notes

1. It does seem like CLU supports all netmasks, but it's impossible to set a netmask during the initialization - it always defaults to 255.255.255.0, one can
   only override the netmask when setting the ip address using telnet command line.

# Other possible commands to be investigated (some might not work or be false positives)

req_stop_ftp -- does not work?
req_tftp_stop -- does not work?
req_refresh_modules / resp_refresh_modules
req_gen_measurements
req_diagnostic_refresh
req_cert_gen
monitor_req_control / monitor_resp_state
monitor_resp_values / monitor_resp_packages
req_start_fw_upgrade / resp_fw_upgrade_status
req_fw_upgrade
req_refresh_config / resp_refresh_config
req_reload_scripts
req_check_alive / resp_check_alive
