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
IFACE_bridge_E=br_tap

IFACE_E_MAC=12:51:16:90:8f:ee
IFACE_B=wmaxtun
IFACE_C=wlan0


IFACE_C_AP_ESSID=AP2
IFACE_C_AP_MAC=cc:af:78:03:3e:4b
IFACE_C_AP_channel=11
IFACE_C_AP_mode=Managed

OVS_switchDPID_tap=0000000000000005


OVS_controllerIP=127.0.0.1:6653

##################################################################
###################################################################

## Tap
echo "OVS: Removing any existing bridge, $IFACE_bridge_E..."
if [ -n "$(sudo ovs-vsctl show | grep $IFACE_bridge_E)" ]
then
	echo "OVS: ...removing $IFACE_bridge_E"
	sudo ovs-vsctl del-br $IFACE_bridge_E
fi



## Tap
echo "OVS: Adding interface bridge, $IFACE_bridge_E..."
sudo ovs-vsctl add-br $IFACE_bridge_E
echo "OVS: ...with port $IFACE_B"
sudo ovs-vsctl add-port $IFACE_bridge_E $IFACE_B  -- set Interface $IFACE_B type=gre options:remote_ip=130.127.38.133
echo "OVS: ...with port $IFACE_C"
sudo ovs-vsctl add-port $IFACE_bridge_E $IFACE_C  
sudo ifconfig lo up

echo "OVS: Setting $IFACE_bridge_E DPID to $OVS_switchDPID_tap..."
sudo ovs-vsctl set bridge $IFACE_bridge_E other-config:datapath-id=$OVS_switchDPID_tap

## Tap
echo "OVS: Connecting $IFACE_bridge_E to controller at $OVS_controllerIP"
sudo ovs-vsctl set-controller $IFACE_bridge_E tcp:$OVS_controllerIP
sudo ovs-vsctl set controller $IFACE_bridge_E  connection-mode=out-of-band
sudo ovs-vsctl set-fail-mode $IFACE_bridge_E secure
sudo ovs-vsctl set bridge br_tap other-config:hwaddr=$IFACE_E_MAC

echo "OVS: Finished!"


##########################
#CONFIGURE NETWORK ACCESS#
##########################

## Turn off IP Forwarding
echo "NTWK: Disabling IP Forwarding..."
sudo echo "0" > /proc/sys/net/ipv4/ip_forward
sudo echo "0" > /proc/sys/net/ipv4/conf/all/forwarding

## Disable IPv6
echo "NTWK: Disabling IPv6..."
echo "1" > /proc/sys/net/ipv6/conf/all/disable_ipv6 

## Disable IP on interfaces
#echo "NTWK: Taking down $IFACE_B..."
#ifconfig $IFACE_B down


echo "NTWK: Setting WiFi interfaces into promiscuous mode to receive packets for $IFACE_bridge_E"
ip link set dev $IFACE_C down
ip link set dev $IFACE_C address 12:51:16:90:8f:ee
ip link set dev $IFACE_C promisc on up
sleep 2

echo "NTWK: Connecting $IFACE_bridge_C to AP via iwconfig..."
iwconfig $IFACE_C essid $IFACE_C_AP_ESSID ap $IFACE_C_AP_MAC channel $IFACE_C_AP_channel mode $IFACE_C_AP_mode

ifconfig $IFACE_bridge_E up

ip route del 130.127.38.133/32 dev eth2
ip route add 130.127.38.133/32 via 192.168.0.1 dev eth2
exit 0
