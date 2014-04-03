#!/bin/sh

# For Orbit Testbed, do nothing, since we don't have to worry about DHCP adding routes from other ifaces

#route del -net 10.41.14.0 netmask 255.255.255.0 dev br_tap

route -n
