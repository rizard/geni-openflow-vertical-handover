#!/bin/sh


route del -net 192.168.2.0 netmask 255.255.255.0 dev br_wifi0
route del -net 192.168.0.0 netmask 255.255.255.0 dev br_wimax
route delete default gw 192.168.0.1 dev br_wimax
route delete default gw 192.168.2.1 dev br_wifi0
route delete default gw 130.127.39.129 dev eth3
route add default dev br_tap
route -n
