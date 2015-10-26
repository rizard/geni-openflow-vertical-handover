#!/bin/sh -e

# setting up a WiFi AP 
# to be included in rc.local for root. 
# thanks a ton to rizard for client startup scripts on which this
# is highly based on
# return with exit 0 if successful


#####################################
# User Defined Variables
#####################################

IFACE_br_wifi=br_wifi
IFACE_br_eth=br_eth
IFACE_br_wifi_mac=cc:af:78:03:3e:4b
IFACE_br_eth_mac=22:dc:13:80:f2:48

IFACE_wifi=wlan0
IFACE_eth=eth0_tun
#IFACE_eth_tun=eth_tun

IFACE_patch_wifi_to_eth=wifi_eth
IFACE_patch_eth_to_wifi=eth_wifi

OVS_kernel_module_path=/home/netlab/Downloads/openvswitch-2.3.0/datapath/openvswitch.ko
OVS_switchDPID_eth=0000000000000001
OVS_switchDPID_wifi=0000000000000002

port_eth=1
port_wifi=1
port_wifi_to_eth=21
port_eth_to_wifi=12

OVS_controllerIP=192.168.4.200:6633:6633 # this has to be edited, need root ipi
#OVS_controllerIP=130.127.49.128:6633

Remote_IP=130.127.38.77 #ip of eth6@root node


###########################
# START OPENVSWITCH
####################################

echo "OVS: Configuring OVS..."
echo "OVS: Checking for kernel module..."
if [ -e $(lsmod | grep openvswitch) ]
then
        echo "OVS: ...inserting kernel module"
        insmod $OVS_kernel_module_path
else
        echo "OVS: ...kernel module already present"
fi

echo "OVS: Creating database"


modprobe openvswitch
modinfo openvswitch

ovsdb-server    --remote=punix:/usr/local/var/run/openvswitch/db.sock \
                --remote=db:Open_vSwitch,Open_vSwitch,manager_options \
                --private-key=db:Open_vSwitch,SSL,private_key \
                --certificate=db:Open_vSwitch,SSL,certificate \
                --bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
                --pidfile --detach
ovs-vsctl --no-wait init
ovs-vswitchd --pidfile --detach
ovs-vsctl show


echo "OVS: Removing any existing bridge, $IFACE_br_wifi $IFACE_br_eth"

# Ethernet
if [ -n "$(ovs-vsctl show | grep $IFACE_br_eth)" ]
then
       echo "OVS: ...removing $IFACE_br_eth"
       ovs-vsctl del-br $IFACE_br_eth
fi

## Wlan0
echo "OVS: Removing any existing bridge, $IFACE_br_wifi..."
if [ -n "$(ovs-vsctl show | grep $IFACE_br_wifi)" ]
then
        echo "OVS: ...removing $IFACE_br_wifi"
        ovs-vsctl del-br $IFACE_br_wifi
fi

echo "Creating Bridges "

## Wlan0
echo "OVS: Adding interface bridge, $IFACE_br_wifi..."
ovs-vsctl -- --may-exist add-br $IFACE_br_wifi -- set bridge $IFACE_br_wifi other-config:hwaddr=$IFACE_br_wifi_mac
echo "OVS: ...with port $IFACE_wifi"
ovs-vsctl add-port $IFACE_br_wifi $IFACE_wifi -- set Interface $IFACE_wifi ofport=$port_wifi
echo "OVS: ...with port $IFACE_patch_wifi_to_eth"
ovs-vsctl add-port $IFACE_br_wifi $IFACE_patch_wifi_to_eth -- set Interface $IFACE_patch_wifi_to_eth type=patch options:peer=$IFACE_patch_eth_to_wifi ofport=$port_wifi_to_eth

##Ethernet
echo "OVS: Adding interface bridge, $IFACE_br_eth..."
ovs-vsctl -- --may-exist add-br $IFACE_br_eth -- set bridge $IFACE_br_eth other-config:hwaddr=$IFACE_br_eth_mac
echo "OVS: ...with port $IFACE_eth"
ovs-vsctl add-port $IFACE_br_eth $IFACE_eth -- set Interface $IFACE_eth ofport=$port_eth 
echo "OVS: ...with port $IFACE_patch_eth_to_wifi"
ovs-vsctl add-port $IFACE_br_eth $IFACE_patch_eth_to_wifi -- set Interface $IFACE_patch_eth_to_wifi type=patch options:peer=$IFACE_patch_wifi_to_eth ofport=$port_eth_to_wifi


echo "OVS: Setting $IFACE_br_eth  DPID to $OVS_switchDPID_eth"
ovs-vsctl set bridge $IFACE_br_eth other-config:datapath-id=$OVS_switchDPID_eth


echo "OVS: Setting $IFACE_br_wifi  DPID to $OVS_switchDPID_wifi"
ovs-vsctl set bridge $IFACE_br_wifi other-config:datapath-id=$OVS_switchDPID_wifi

echo "Setting lo  UP"

ifconfig lo up

##Wlan0

echo "OVS: Connecting $IFACE_br_wifi to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_br_wifi tcp:$OVS_controllerIP
ovs-vsctl set-fail-mode $IFACE_br_wifi standalone

#Ethernet


echo "OVS: Connecting $IFACE_br_eth to controller at $OVS_controllerIP"
ovs-vsctl set-controller $IFACE_br_eth tcp:$OVS_controllerIP
ovs-vsctl set-fail-mode $IFACE_br_eth standalone

echo " OVS : DONE!"


ifconfig $IFACE_eth up
ifconfig $IFACE_wifi up

#sleep 2

echo "inserting initial flows: Wifi -> Eth and Eth -> Wifi" 
ovs-ofctl add-flow br_eth in_port=LOCAL,priority=32767,dl_type=0x800,ip_proto=6,tp_dst=6633,actions=output:$port_eth
ovs-ofctl add-flow br_eth in_port=$port_eth,priority=32767,dl_type=0x800,ip_proto=6,tp_src=6633,actions=output:LOCAL
ovs-ofctl add-flow br_wifi in_port=$port_wifi,priority=3000,actions=output:$port_wifi_to_eth
ovs-ofctl add-flow br_wifi in_port=$port_wifi_to_eth,priority=3000,actions=output:$port_wifi
ovs-ofctl add-flow br_eth in_port=$port_eth,priority=3000,actions=output:$port_eth_to_wifi
ovs-ofctl add-flow br_eth in_port=$port_eth_to_wifi,priority=3000,actions=output:$port_eth


ifconfig $IFACE_br_eth 192.168.4.201/24 up
ifconfig $IFACE_br_wifi 192.168.4.202/24 up


echo 'nameserver 8.8.8.8' > /etc/resolv.conf

echo "1" > /proc/sys/net/ipv4/ip_forward

arp -s 192.168.4.200 5e:d8:bf:6c:48:4d
route add $Remote_IP/32 dev wlan1
ip route del 192.168.4.0/24 dev br_wifi
arp -s $Remote_IP/32 00:1a:a0:c8:56:d1 

echo "add tunnel port" 
#ifconfig $IFACE_eth $Local_IP up
#ovs-vsctl add-port $IFACE_br_eth $IFACE_eth_tun -- set interface $IFACE_eth_tun type=gre options:remote_ip=$Remote_IP
ovs-vsctl -- set interface $IFACE_eth type=gre options:remote_ip=$Remote_IP

echo "starting hostapd"
hostapd -dd hostapd_simple.conf




exit 0
