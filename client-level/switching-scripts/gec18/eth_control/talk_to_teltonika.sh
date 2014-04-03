#!/bin/bash

## This is a script that can leverage the CLI of the Teltonika UM6225 USB Modem.
## All arguments following this script will be fed to the WiMAX modem command line.
## To get a list of possible commands: ./talk_to_teltonika help

USERNAME=admin
PASSWORD=wangOF10
WIMAX_IP=192.168.0.1

wget --http-user $USERNAME --http-password $PASSWORD -qO - "http://$WIMAX_IP/cgi/cli?$@"
