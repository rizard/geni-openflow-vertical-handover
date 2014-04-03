#!/bin/sh

arp -s 12.0.0.1 00:22:64:cb:13:2d
python eth_switch.py

