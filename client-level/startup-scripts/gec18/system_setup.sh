#!/bin/sh -e
#
# rc.local
#
# This script is executed at the end of each multiuser runlevel.
# Make sure that the script will "exit 0" on success or any other
# value on error.
#
# In order to enable or disable this script just change the execution
# bits.
#
# By default this script does nothing.

## exit 0
###################
#USR DEF VARIABLES#
###################
#IFACE_bridge_A=br_eth
IFACE_bridge_B=br_wifi0
#IFACE_bridge_C=br_wifi1
IFACE_bridge_D=br_wimax
IFACE_bridge_E=br_tap
IFACE_E=tap0
IFACE_E_IP=10.41.14.1
IFACE_E_MAC=12:51:16:90:8f:ee
IFACE_D=wmx0
#IFACE_A=eth0
IFACE_B=wlan0
#IFACE_C=wlan1
#IFACE_patch_tap_to_eth=tap-eth
IFACE_patch_tap_to_wlan0=tap-wlan0
#IFACE_patch_tap_to_wlan1=tap-wlan1
IFACE_patch_tap_to_wimax=tap-wimax
#IFACE_patch_eth_to_tap=eth-tap
IFACE_patch_wlan0_to_tap=wlan0-tap
#IFACE_patch_wlan1_to_tap=wlan1-tap
IFACE_patch_wimax_to_tap=wimax-tap
OVS_kernel_module_path=/root/openvswitch-1.9.0/datapath/linux/openvswitch.ko
#OVS_switchDPID_eth=0000000000000001
OVS_switchDPID_wlan0=0000000000000002
#OVS_switchDPID_wlan1=0000000000000003
OVS_switchDPID_wimax=0000000000000004
OVS_switchDPID_tap=0000000000000005

port_tap0=1
port_tap_to_eth=15
port_tap_to_wimax=3
port_tap_to_wlan0=4
port_tap_to_wlan1=5
port_eth3=1
port_eth_to_tap=20
port_wimax=1
port_wimax_to_tap=2
port_ath4=1
port_wlan0_to_tap=2
port_ath5=1
port_wlan1_to_tap=2

OVS_controllerIP=127.0.0.1:6633
FL_contRESTIP=127.0.0.1
FL_path=/root/floodlight-0.90/target/floodlight.jar
FL_log=/root/floodlight-output
FL_clear_flows_script=/root/SwitchingScripts/clear_flows.sh
FL_initial_flows_script=/root/SwitchingScripts/gec18_insert_flows_wifi.sh

WIMAX_DEV_NAME=/dev/sg1

IFACE_B_AP_ESSID=GENI_WiFi_AP
IFACE_B_AP_MAC=00:23:15:48:74:9c
IFACE_B_AP_channel=11
IFACE_B_AP_mode=Managed

#################
#ADD WIMAX IFACE#
#################

echo "WM: Installing WiMAX device on $WIMAX_DEV_NAME..."
modprobe i2400m_usb
wimaxcu roff
sleep 2
wimaxcu ron
sleep 2
wimaxcu connect network 51

#if [ -e $WIMAX_DEV_NAME ]
#then
#	echo "WM: ...done"
#	sdparm --command=eject $WIMAX_DEV_NAME &
#else
#	echo "WM: ...already found $WIMAX_DEV_NAME"
#fi
#sleep 3
## echo "WM: Initializing interface $IFACE_D"
## dhclient $IFACE_D

###############
#ADD TAP IFACE#
###############

echo "OVPN: Installing tap interface, $IFACE_E"
openvpn --mktun --dev $IFACE_E --lladdr $IFACE_E_MAC

##################
#START FLOODLIGHT#
##################

## echo "FL: Killing running Floodlight process..."
# pkill -9 -f floodlight

echo "FL: Starting Floodlight..."
#Modify the following script to change debugging output (set to WARN)
(java -jar $FL_path) > $FL_log 2>&1 &
echo "FL: Finished!"

#cd root

###################
#START OPENVSWITCH#
###################
#echo "Killing all OVS processes (except ovs_workq for some reason)..."
#pkill -9 ovs

echo "OVS: Configuring OVS..."
echo "OVS: Checking for kernel module..."
if [ -e $(lsmod | grep openvswitch) ]
then
	echo "OVS: ...inserting kernel module"
	insmod $OVS_kernel_module_path
else
	echo "OVS: ...kernel module already present"
fi
##################################################################
## echo "OVS: Killing all running OVS processes..."
#(pkill -9 -f ovsdb-server > /dev/null 2>&1)
#(pkill -9 -f ovs-vswitchd > /dev/null 2>&1)
#(pkill -9 -f ovs_workq > /dev/null 2>&1)
#sleep 3
##################################################################
echo "OVS: Creating database"
ovsdb-server --remote=punix:/usr/local/var/run/openvswitch/db.sock \
--remote=db:Open_vSwitch,manager_options \
--private-key=db:SSL,private_key \
--certificate=db:SSL,certificate \
--bootstrap-ca-cert=db:SSL,ca_cert \
--pidfile --detach
echo "OVS: Initializing OVS..."
ovs-vsctl --no-wait init
echo "OVS: Starting OVS..."
ovs-vswitchd --pidfile --detach
###################################################################
echo "OVS: Removing any existing bridge, $IFACE_bridge_A $IFACE_bridge_B"
echo "OVS: $IFACE_bridge_C $IFACE_bridge_D ..."

## Ethernet
#if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_A)" ]
#then
#	echo "OVS: ...removing $IFACE_bridge_A"
#	ovs-vsctl del-br $IFACE_bridge_A
#fi

## Wlan0
echo "OVS: Removing any existing bridge, $IFACE_bridge_B..."
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_B)" ]
then
        echo "OVS: ...removing $IFACE_bridge_B"
        ovs-vsctl del-br $IFACE_bridge_B
fi

## Wlan1
#if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_C)" ]
#then
#	echo "OVS: ...removing $IFACE_bridge_C"
#	ovs-vsctl del-br $IFACE_bridge_C
#fi

## WiMAX
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_D)" ]
then
	echo "OVS: ...removing $IFACE_bridge_D"
	ovs-vsctl del-br $IFACE_bridge_D
fi

## Tap
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_E)" ]
then
	echo "OVS: ...removing $IFACE_bridge_E"
	ovs-vsctl del-br $IFACE_bridge_E
fi

## Wlan0
echo "OVS: Adding interface bridge, $IFACE_bridge_B..."
ovs-vsctl add-br $IFACE_bridge_B
echo "OVS: ...with port $IFACE_B"
ovs-vsctl add-port $IFACE_bridge_B $IFACE_B
echo "OVS: ...with port $IFACE_patch_wlan0_to_tap"
ovs-vsctl add-port $IFACE_bridge_B $IFACE_patch_wlan0_to_tap

## Wlan1
#echo "OVS: Adding interface bridge, $IFACE_bridge_C..."
#ovs-vsctl add-br $IFACE_bridge_C
#echo "OVS: ...with port $IFACE_C"
#ovs-vsctl add-port $IFACE_bridge_C $IFACE_C
#echo "OVS: ...with port $IFACE_patch_wlan1_to_tap"
#ovs-vsctl add-port $IFACE_bridge_C $IFACE_patch_wlan1_to_tap

## WiMAX
echo "OVS: Adding interface bridge, $IFACE_bridge_D..."
ovs-vsctl add-br $IFACE_bridge_D
echo "OVS: ...with port $IFACE_D"
ovs-vsctl add-port $IFACE_bridge_D $IFACE_D -- set Interface $IFACE_D ofport=$port_wimax
echo "OVS: ...with port $IFACE_patch_wimax_to_tap"
ovs-vsctl add-port $IFACE_bridge_D $IFACE_patch_wimax_to_tap -- set Interface $IFACE_patch_wimax_to_tap ofport=$port_wimax_to_tap

## Tap
echo "OVS: Adding interface bridge, $IFACE_bridge_E..."
ovs-vsctl add-br $IFACE_bridge_E
echo "OVS: ...with port $IFACE_E"
ovs-vsctl add-port $IFACE_bridge_E $IFACE_E -- set Interface $IFACE_E ofport=$port_tap0
#echo "OVS: ...with port $IFACE_patch_tap_to_eth"
#ovs-vsctl add-port $IFACE_bridge_E $IFACE_patch_tap_to_eth -- set Interface $IFACE_patch_tap_to_eth ofport=$port_tap_to_eth
echo "OVS: ...with port $IFACE_patch_tap_to_wlan0"
ovs-vsctl add-port $IFACE_bridge_E $IFACE_patch_tap_to_wlan0 -- set Interface $IFACE_patch_tap_to_wlan0 ofport=$port_tap_to_wlan0
#echo "OVS: ...with port $IFACE_patch_tap_to_wlan1"
#ovs-vsctl add-port $IFACE_bridge_E $IFACE_patch_tap_to_wlan1 -- set Interface $IFACE_patch_tap_to_wlan1 ofport=$port_tap_to_wlan1
echo "OVS: ...with port $IFACE_patch_tap_to_wimax"
ovs-vsctl add-port $IFACE_bridge_E $IFACE_patch_tap_to_wimax -- set Interface $IFACE_patch_tap_to_wimax ofport=$port_tap_to_wimax


## Set patch ports
#echo "OVS: Patching ports $IFACE_patch_tap_to_eth, $IFACE_patch_eth_to_tap"
#ovs-vsctl set interface $IFACE_patch_tap_to_eth type=patch
#ovs-vsctl set interface $IFACE_patch_tap_to_eth options:peer=$IFACE_patch_eth_to_tap
#ovs-vsctl set interface $IFACE_patch_eth_to_tap type=patch
#ovs-vsctl set interface $IFACE_patch_eth_to_tap options:peer=$IFACE_patch_tap_to_eth

echo "OVS: Patching ports $IFACE_patch_tap_to_wlan0, $IFACE_patch_wlan0_to_tap"
ovs-vsctl set interface $IFACE_patch_tap_to_wlan0 type=patch
ovs-vsctl set interface $IFACE_patch_tap_to_wlan0 options:peer=$IFACE_patch_wlan0_to_tap
ovs-vsctl set interface $IFACE_patch_wlan0_to_tap type=patch
ovs-vsctl set interface $IFACE_patch_wlan0_to_tap options:peer=$IFACE_patch_tap_to_wlan0

#echo "OVS: Patching ports $IFACE_patch_tap_to_wlan1, $IFACE_patch_wlan1_to_tap"
#ovs-vsctl set interface $IFACE_patch_tap_to_wlan1 type=patch
#ovs-vsctl set interface $IFACE_patch_tap_to_wlan1 options:peer=$IFACE_patch_wlan1_to_tap
#ovs-vsctl set interface $IFACE_patch_wlan1_to_tap type=patch
#ovs-vsctl set interface $IFACE_patch_wlan1_to_tap options:peer=$IFACE_patch_tap_to_wlan1

echo "OVS: Patching ports $IFACE_patch_tap_to_wimax, $IFACE_patch_wimax_to_tap"
ovs-vsctl set interface $IFACE_patch_tap_to_wimax type=patch
ovs-vsctl set interface $IFACE_patch_tap_to_wimax options:peer=$IFACE_patch_wimax_to_tap
ovs-vsctl set interface $IFACE_patch_wimax_to_tap type=patch
ovs-vsctl set interface $IFACE_patch_wimax_to_tap options:peer=$IFACE_patch_tap_to_wimax

ifconfig lo up

## Set Eth DPID
#echo "OVS: Setting $IFACE_bridge_A DPID to $OVS_switchDPID_eth..."
#ovs-vsctl set bridge $IFACE_bridge_A other-config:datapath-id=$OVS_switchDPID_eth

## Set Wlan0 DPID
echo "OVS: Setting $IFACE_bridge_B DPID to $OVS_switchDPID_wlan0..."
ovs-vsctl set bridge $IFACE_bridge_B other-config:datapath-id=$OVS_switchDPID_wlan0

## Set Wlan1 DPID
#echo "OVS: Setting $IFACE_bridge_C DPID to $OVS_switchDPID_wlan1..."
#ovs-vsctl set bridge $IFACE_bridge_C other-config:datapath-id=$OVS_switchDPID_wlan1

## Set WiMAX DPID
echo "OVS: Setting $IFACE_bridge_D DPID to $OVS_switchDPID_wimax..."
ovs-vsctl set bridge $IFACE_bridge_D other-config:datapath-id=$OVS_switchDPID_wimax

## Set Tap DPID
echo "OVS: Setting $IFACE_bridge_E DPID to $OVS_switchDPID_tap..."
ovs-vsctl set bridge $IFACE_bridge_E other-config:datapath-id=$OVS_switchDPID_tap

## Ethernet
#echo "OVS: Connecting $IFACE_bridge_A to controller at $OVS_controllerIP"
#ovs-vsctl set-controller $IFACE_bridge_A tcp:$OVS_controllerIP
#ovs-vsctl set-fail-mode $IFACE_bridge_A standalone

## Wlan0
echo "OVS: Connecting $IFACE_bridge_B to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_B tcp:$OVS_controllerIP
ovs-vsctl set-fail-mode $IFACE_bridge_B standalone

## Wlan1
#echo "OVS: Connecting $IFACE_bridge_C to controller at $OVS_controllerIP"
#ovs-vsctl set-controller $IFACE_bridge_C tcp:$OVS_controllerIP
#ovs-vsctl set-fail-mode $IFACE_bridge_C standalone

## WiMAX
echo "OVS: Connecting $IFACE_bridge_D to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_D tcp:$OVS_controllerIP
ovs-vsctl set-fail-mode $IFACE_bridge_D standalone

## Tap
echo "OVS: Connecting $IFACE_bridge_E to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_E tcp:$OVS_controllerIP
ovs-vsctl set-fail-mode $IFACE_bridge_E standalone
ovs-vsctl set bridge br_tap other-config:hwaddr=$IFACE_E_MAC

echo "OVS: Finished!"


##########################
#CONFIGURE NETWORK ACCESS#
##########################

## Turn off IP Forwarding
echo "NTWK: Disabling IP Forwarding..."
echo "0" > /proc/sys/net/ipv4/ip_forward
echo "0" > /proc/sys/net/ipv4/conf/all/forwarding

## Disable IPv6
echo "NTWK: Disabling IPv6..."
echo "0" > /proc/sys/net/ipv6/conf/all/disable_ipv6 

## Disable IP on interfaces
#echo "NTWK: Taking down $IFACE_A..."
#ifconfig $IFACE_A down
#echo "NTWK: Taking down $IFACE_D..."
#ifconfig $IFACE_D down
echo "NTWK: Taking down $IFACE_B..."
ifconfig $IFACE_B down
#echo "NTWK: Taking down $IFACE_C..."
#ifconfig $IFACE_C down
#echo "NTWK: Taking down $IFACE_bridge_A..."
#ifconfig $IFACE_bridge_A down
echo "NTWK: Taking down $IFACE_bridge_B..."
ifconfig $IFACE_bridge_B down
#echo "NTWK: Taking down $IFACE_bridge_C..."
#ifconfig $IFACE_bridge_C down
echo "NTWK: Taking down $IFACE_bridge_D..."
ifconfig $IFACE_bridge_D down
sleep 2

## Configure all interface MACs the same as the tap's MAC
echo "NTWK: Setting all interfaces into promiscuous mode to receive packets for $IFACE_bridge_E"
#ip link set dev $IFACE_A down
#ip link set dev $IFACE_A promisc on up
ip link set dev $IFACE_B down
ip link set dev $IFACE_B promisc on up
#ip link set dev $IFACE_C down
#ip link set dev $IFACE_C promisc on up
#ip link set dev $IFACE_D down
ip link set dev $IFACE_D promisc on up
##echo "NTWK: Setting and bringing up $IFACE_A's MAC as $IFACE_E_MAC"
##ip link set dev $IFACE_A down
##ip link set dev $IFACE_A address $IFACE_E_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_B's MAC as $IFACE_E_MAC"
##ip link set dev $IFACE_B down
##ip link set dev $IFACE_B address $IFACE_E_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_C's MAC as $IFACE_E_MAC"
##ip link set dev $IFACE_C down
##ip link set dev $IFACE_C address $IFACE_E_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_D's MAC as $IFACE_E_MAC"
##ip link set dev $IFACE_C down
##ip link set dev $IFACE_D address $IFACE_E_MAC promisc on up
##sleep 2

echo "NTWK: Connecting $IFACE_bridge_B to AP via iwconfig..."
ifconfig $IFACE_B down
ifconfig $IFACE_B up promisc multicast
iwconfig $IFACE_B essid $IFACE_B_AP_ESSID ap $IFACE_B_AP_MAC channel $IFACE_B_AP_channel mode $IFACE_B_AP_mode
## Toshiba Laptop ap 00:23:4E:0F:A3:9F channel 1
## PC Engine ap 00:0C:42:6A:4F:5E channel 11

#echo "NTWK: Configuring $IFACE_bridge_C future connection..."
#ifconfig $IFACE_C down
#ifconfig $IFACE_C up promisc multicast
#iwconfig $IFACE_C essid MyAP channel 1 mode managed

#echo "NTWK: Configuring $IFACE_bridge_D future connection..."
#ifconfig $IFACE_D down
#ifconfig $IFACE_D up promisc multicast
## Configure any wimax parameter via "talk_to_teltonika.sh" or telnet (admin/admin01) here...

echo "NTWK: Pushing initial flows for $IFACE_bridge_E <--> $IFACE_B"
$FL_clear_flows_script
python $FL_initial_flows_script
echo "NTWK: Verify flows on $IFACE_bridge_E and $IFACE_bridge_B via ovs-ofctl"
ovs-ofctl dump-flows $IFACE_bridge_E && ovs-ofctl dump-flows $IFACE_bridge_B

#echo "NTWK: Attempting to acquire IP via DHCP on $IFACE_bridge_E"
#dhclient $IFACE_bridge_E

exit 0
