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
IFACE_bridge_eth=br_eth
IFACE_bridge_wlan0=br_wifi0
IFACE_bridge_wlan1=br_wifi1
IFACE_bridge_wimax=br_wimax
IFACE_bridge_int=br_tap
IFACE_bridge=bridge
IFACE_tap=tap0
IFACE_tap_IP=10.0.0.10
IFACE_tap_MAC=12:51:16:90:8f:ee
IFACE_wimax=eth1
IFACE_ethernet=eth0
IFACE_wlan0=wlan0
IFACE_wlan1=wlan1
IFACE_patch_tap_to_eth=tap-eth
IFACE_patch_tap_to_wlan0=tap-wlan0
IFACE_patch_tap_to_wlan1=tap-wlan1
IFACE_patch_tap_to_wimax=tap-wimax
IFACE_patch_eth_to_tap=eth-tap
IFACE_patch_wlan0_to_tap=wlan0-tap
IFACE_patch_wlan1_to_tap=wlan1-tap
IFACE_patch_wimax_to_tap=wimax-tap
OVS_kernel_module_path=/home/rizard/Downloads/openvswitch-1.7.3/datapath/linux/openvswitch.ko
OVS_switchDPID_eth=0000000000000001
OVS_switchDPID_wlan0=0000000000000002
OVS_switchDPID_wlan1=0000000000000003
OVS_switchDPID_wimax=0000000000000004
OVS_switchDPID_tap=0000000000000005
port_tap0=1
port_tap_to_eth=15
port_tap_to_wimax=3
port_tap_to_wlan0=4
port_tap_to_wlan1=5
port_eth3=1
port_eth_to_tap=20
port_eth4=1
port_wimax_to_tap=2
port_ath4=1
port_wlan0_to_tap=2
port_ath5=1
port_wlan1_to_tap=2
OVS_controllerIP=127.0.0.1:6633
FL_contRESTIP=127.0.0.1
#FL_initial_flows_script=/root/gec_demo/AP1_eth/initial_flows_eth.py
FL_initial_flows_script=/home/rizard/Desktop/AP1_eth/initial_flows_wifi0_ArpMod.py
WIMAX_DEV_NAME=/dev/sg1

#################
#ADD WIMAX IFACE#
#################

#echo "WM: Installing WiMAX device on $WIMAX_DEV_NAME..."
#if [ -e $WIMAX_DEV_NAME ]
#then
#	echo "WM: ...done"
#	sdparm --command=eject $WIMAX_DEV_NAME &
#else
#	echo "WM: ...already found $WIMAX_DEV_NAME"
#fi
#sleep 3
## echo "WM: Initializing interface $IFACE_wimax"
## dhclient $IFACE_wimax

###############
#ADD TAP IFACE#
###############

echo "OVPN: Installing tap interface, $IFACE_tap"
openvpn --mktun --dev $IFACE_tap --lladdr $IFACE_tap_MAC

##################
#START FLOODLIGHT#
##################

## echo "FL: Killing running Floodlight process..."
# pkill -9 -f floodlight

#echo "FL: Starting Floodlight..."
#Modify the following script to change debugging output (set to WARN)
#cd / && ((java -jar ./root/floodlight-sfp/target/floodlight.jar) > floodlight-output 2>&1 &)
#echo "FL: Finished!"

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
echo "OVS: Removing any existing bridge, $IFACE_bridge_eth $IFACE_bridge_wlan0"
echo "OVS: $IFACE_bridge_wlan1 $IFACE_bridge_wimax ..."

## Generic "bridge"
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge)" ]
then
	echo "OVS: ...removing $IFACE_bridge"
	ovs-vsctl del-br $IFACE_bridge
fi

## Ethernet
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_eth)" ]
then
	echo "OVS: ...removing $IFACE_bridge_eth"
	ovs-vsctl del-br $IFACE_bridge_eth
fi

## Wlan0
echo "OVS: Removing any existing bridge, $IFACE_bridge_wlan0..."
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_wlan0)" ]
then
        echo "OVS: ...removing $IFACE_bridge_wlan0"
        ovs-vsctl del-br $IFACE_bridge_wlan0
fi

## Wlan1
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_wlan1)" ]
then
	echo "OVS: ...removing $IFACE_bridge_wlan1"
	ovs-vsctl del-br $IFACE_bridge_wlan1
fi

## WiMAX
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_wimax)" ]
then
	echo "OVS: ...removing $IFACE_bridge_wimax"
	ovs-vsctl del-br $IFACE_bridge_wimax
fi

## Internal
if [ -n "$(ovs-vsctl show | grep $IFACE_bridge_int)" ]
then
	echo "OVS: ...removing $IFACE_bridge_int"
	ovs-vsctl del-br $IFACE_bridge_int
fi

## All
## echo "OVS: Adding interface bridge, $IFACE_bridge..."
## ovs-vsctl add-br $IFACE_bridge
## echo "OVS: ...with port $IFACE_ethernet"
## ovs-vsctl add-port $IFACE_bridge $IFACE_ethernet
## echo "OVS: ...with port $IFACE_wlan0"
## ovs-vsctl add-port $IFACE_bridge $IFACE_wlan0
## echo "OVS: ...with port $IFACE_wlan1"
## ovs-vsctl add-port $IFACE_bridge $IFACE_wlan1
## echo "OVS: ...with port $IFACE_wimax"
## ovs-vsctl add-port $IFACE_bridge $IFACE_wimax
## echo "OVS: ...with port $IFACE_tap"
## ovs-vsctl add-port $IFACE_bridge $IFACE_tap

## Ethernet
#echo "OVS: Adding interface bridge, $IFACE_bridge_eth..."
#ovs-vsctl add-br $IFACE_bridge_eth
#echo "OVS: ...with port $IFACE_eth"
#ovs-vsctl add-port $IFACE_bridge_eth $IFACE_ethernet -- set Interface $IFACE_ethernet ofport=$port_eth3
#echo "OVS: ...with port $IFACE_patch_eth_to_tap"
#ovs-vsctl add-port $IFACE_bridge_eth $IFACE_patch_eth_to_tap -- set Interface $IFACE_patch_eth_to_tap ofport=$port_eth_to_tap

## Wlan0
echo "OVS: Adding interface bridge, $IFACE_bridge_wlan0..."
ovs-vsctl add-br $IFACE_bridge_wlan0
echo "OVS: ...with port $IFACE_wlan0"
ovs-vsctl add-port $IFACE_bridge_wlan0 $IFACE_wlan0
echo "OVS: ...with port $IFACE_patch_wlan0_to_tap"
ovs-vsctl add-port $IFACE_bridge_wlan0 $IFACE_patch_wlan0_to_tap

## Wlan1
echo "OVS: Adding interface bridge, $IFACE_bridge_wlan1..."
ovs-vsctl add-br $IFACE_bridge_wlan1
echo "OVS: ...with port $IFACE_wlan1"
ovs-vsctl add-port $IFACE_bridge_wlan1 $IFACE_wlan1
echo "OVS: ...with port $IFACE_patch_wlan1_to_tap"
ovs-vsctl add-port $IFACE_bridge_wlan1 $IFACE_patch_wlan1_to_tap

## WiMAX
echo "OVS: Adding interface bridge, $IFACE_bridge_wimax..."
ovs-vsctl add-br $IFACE_bridge_wimax
echo "OVS: ...with port $IFACE_wimax"
ovs-vsctl add-port $IFACE_bridge_wimax $IFACE_wimax -- set Interface $IFACE_wimax ofport=$port_eth4
echo "OVS: ...with port $IFACE_patch_wimax_to_tap"
ovs-vsctl add-port $IFACE_bridge_wimax $IFACE_patch_wimax_to_tap -- set Interface $IFACE_patch_wimax_to_tap ofport=$port_wimax_to_tap

## Internal
echo "OVS: Adding interface bridge, $IFACE_bridge_int..."
ovs-vsctl add-br $IFACE_bridge_int
echo "OVS: ...with port $IFACE_tap"
ovs-vsctl add-port $IFACE_bridge_int $IFACE_tap -- set Interface $IFACE_tap ofport=$port_tap0
#echo "OVS: ...with port $IFACE_patch_tap_to_eth"
#ovs-vsctl add-port $IFACE_bridge_int $IFACE_patch_tap_to_eth -- set Interface $IFACE_patch_tap_to_eth ofport=$port_tap_to_eth
echo "OVS: ...with port $IFACE_patch_tap_to_wlan0"
ovs-vsctl add-port $IFACE_bridge_int $IFACE_patch_tap_to_wlan0 -- set Interface $IFACE_patch_tap_to_wlan0 ofport=$port_tap_to_wlan0
echo "OVS: ...with port $IFACE_patch_tap_to_wlan1"
ovs-vsctl add-port $IFACE_bridge_int $IFACE_patch_tap_to_wlan1 -- set Interface $IFACE_patch_tap_to_wlan1 ofport=$port_tap_to_wlan1
echo "OVS: ...with port $IFACE_patch_tap_to_wimax"
ovs-vsctl add-port $IFACE_bridge_int $IFACE_patch_tap_to_wimax -- set Interface $IFACE_patch_tap_to_wimax ofport=$port_tap_to_wimax


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

echo "OVS: Patching ports $IFACE_patch_tap_to_wlan1, $IFACE_patch_wlan1_to_tap"
ovs-vsctl set interface $IFACE_patch_tap_to_wlan1 type=patch
ovs-vsctl set interface $IFACE_patch_tap_to_wlan1 options:peer=$IFACE_patch_wlan1_to_tap
ovs-vsctl set interface $IFACE_patch_wlan1_to_tap type=patch
ovs-vsctl set interface $IFACE_patch_wlan1_to_tap options:peer=$IFACE_patch_tap_to_wlan1

echo "OVS: Patching ports $IFACE_patch_tap_to_wimax, $IFACE_patch_wimax_to_tap"
ovs-vsctl set interface $IFACE_patch_tap_to_wimax type=patch
ovs-vsctl set interface $IFACE_patch_tap_to_wimax options:peer=$IFACE_patch_wimax_to_tap
ovs-vsctl set interface $IFACE_patch_wimax_to_tap type=patch
ovs-vsctl set interface $IFACE_patch_wimax_to_tap options:peer=$IFACE_patch_tap_to_wimax

ifconfig lo up

## Set Eth DPID
#echo "OVS: Setting $IFACE_bridge_eth DPID to $OVS_switchDPID_eth..."
#ovs-vsctl set bridge $IFACE_bridge_eth other-config:datapath-id=$OVS_switchDPID_eth

## Set Wlan0 DPID
echo "OVS: Setting $IFACE_bridge_wlan0 DPID to $OVS_switchDPID_wlan0..."
ovs-vsctl set bridge $IFACE_bridge_wlan0 other-config:datapath-id=$OVS_switchDPID_wlan0

## Set Wlan1 DPID
echo "OVS: Setting $IFACE_bridge_wlan1 DPID to $OVS_switchDPID_wlan1..."
ovs-vsctl set bridge $IFACE_bridge_wlan1 other-config:datapath-id=$OVS_switchDPID_wlan1

## Set WiMAX DPID
echo "OVS: Setting $IFACE_bridge_wimax DPID to $OVS_switchDPID_wimax..."
ovs-vsctl set bridge $IFACE_bridge_wimax other-config:datapath-id=$OVS_switchDPID_wimax

## Set Tap DPID
echo "OVS: Setting $IFACE_bridge_int DPID to $OVS_switchDPID_tap..."
ovs-vsctl set bridge $IFACE_bridge_int other-config:datapath-id=$OVS_switchDPID_tap

## Ethernet
#echo "OVS: Connecting $IFACE_bridge_eth to controller at $OVS_controllerIP"
#ovs-vsctl set-controller $IFACE_bridge_eth tcp:$OVS_controllerIP

## Wlan0
echo "OVS: Connecting $IFACE_bridge_wlan0 to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_wlan0 tcp:$OVS_controllerIP

## Wlan1
echo "OVS: Connecting $IFACE_bridge_wlan1 to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_wlan1 tcp:$OVS_controllerIP

## WiMAX
echo "OVS: Connecting $IFACE_bridge_wimax to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_wimax tcp:$OVS_controllerIP

## Internal
echo "OVS: Connecting $IFACE_bridge_int to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_bridge_int tcp:$OVS_controllerIP

ovs-vsctl set bridge br_tap other-config:hwaddr=12:51:16:90:8f:ee

echo "OVS: Finished!"


##########################
#CONFIGURE NETWORK ACCESS#
##########################

## Turn off IP (MC) Forwarding
echo "NTWK: Disabling IP Forwarding..."
echo "0" > /proc/sys/net/ipv4/ip_forward
echo "0" > /proc/sys/net/ipv4/conf/all/forwarding

## Disable IPv6
echo "NTWK: Disabling IPv6..."
echo "0" > /proc/sys/net/ipv6/conf/all/disable_ipv6 

## Disable IP on interfaces
echo "NTWK: Taking down $IFACE_ethernet..."
ifconfig $IFACE_ethernet down
echo "NTWK: Taking down $IFACE_wimax..."
ifconfig $IFACE_wimax down
echo "NTWK: Taking down $IFACE_wlan0..."
ifconfig $IFACE_wlan0 down
echo "NTWK: Taking down $IFACE_wlan1..."
ifconfig $IFACE_wlan1 down
#echo "NTWK: Taking down $IFACE_bridge_eth..."
#ifconfig $IFACE_bridge_eth down
echo "NTWK: Taking down $IFACE_bridge_wlan0..."
ifconfig $IFACE_bridge_wlan0 down
echo "NTWK: Taking down $IFACE_bridge_wlan1..."
ifconfig $IFACE_bridge_wlan1 down
echo "NTWK: Taking down $IFACE_bridge_wimax..."
ifconfig $IFACE_bridge_wimax down
sleep 2

## Configure all interfaces into promisc mode
echo "NTWK: Setting all interfaces into promiscuous mode to receive packets for $IFACE_bridge_int"
ip link set dev $IFACE_ethernet down
ip link set dev $IFACE_ethernet promisc on up
ip link set dev $IFACE_wlan0 down
ip link set dev $IFACE_wlan0 promisc on up
ip link set dev $IFACE_wlan1 down
ip link set dev $IFACE_wlan1 promisc on up
ip link set dev $IFACE_wimax down
ip link set dev $IFACE_wimax promisc on up

##echo "NTWK: Setting and bringing up $IFACE_ethernet's MAC as $IFACE_tap_MAC"
##ip link set dev $IFACE_ethernet down
##ip link set dev $IFACE_ethernet address $IFACE_tap_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_wlan0's MAC as $IFACE_tap_MAC"
##ip link set dev $IFACE_wlan0 down
##ip link set dev $IFACE_wlan0 address $IFACE_tap_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_wlan1's MAC as $IFACE_tap_MAC"
##ip link set dev $IFACE_wlan1 down
##ip link set dev $IFACE_wlan1 address $IFACE_tap_MAC promisc on up
##sleep 2
##echo "NTWK: Setting and bringing up $IFACE_wimax's MAC as $IFACE_tap_MAC"
##ip link set dev $IFACE_wlan1 down
##ip link set dev $IFACE_wimax address $IFACE_tap_MAC promisc on up
##sleep 2

echo "NTWK: Connecting $IFACE_bridge_wlan0 to AP via iwconfig..."
ifconfig $IFACE_wlan0 down
ifconfig $IFACE_wlan0 up promisc multicast
iwconfig $IFACE_wlan0 essid TestAP ap 00:1E:E5:76:AF:41 channel 6 mode Managed

echo "NTWK: Configuring $IFACE_bridge_wlan1 future connection..."
ifconfig $IFACE_wlan1 down
ifconfig $IFACE_wlan1 up promisc multicast
iwconfig $IFACE_wlan1 essid MyAP channel 1 mode managed

echo "NTWK: Configuring $IFACE_bridge_wimax future connection..."
ifconfig $IFACE_wimax down
ifconfig $IFACE_wimax up promisc multicast
## Configure any wimax parameter via "talk_to_teltonika.sh" or telnet (admin/admin01) here...

echo "NTWK: Pushing initial flows for $IFACE_bridge_int <--> $IFACE_wlan0"
python $FL_initial_flows_script
echo "NTWK: Verify flows on $IFACE_bridge_int and $IFACE_bridge_wlan0 via ovs-ofctl"
ovs-ofctl dump-flows $IFACE_bridge_int && ovs-ofctl dump-flows $IFACE_bridge_wlan0

echo "NTWK: Attempting to acquire IP via DHCP on $IFACE_bridge_int"
dhclient $IFACE_bridge_int

exit 0
