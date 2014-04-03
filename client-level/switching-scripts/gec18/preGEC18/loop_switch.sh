#!/bin/bash

# This script opens 4 terminal windows.

i="0"

while [ $i -lt 4 ]
do
./switch_from_wifi0_to_wifi1.sh
sleep 30
./switch_from_wifi1_to_wifi0.sh
sleep 30
done
