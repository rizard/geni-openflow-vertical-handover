./clear_wif1_flows.sh
ifconfig wlan0 down
ifconfig wlan0 up
iwconfig wlan0 essid "MyAP" ap 00:0C:42:6A:4F:5E channel 11 mode Managed
./insert_wifi0_flows.sh
./dhclient_tap.sh

ovs-ofctl dump-flows br_tap
