./clear_wif0_flows.sh
./insert_wifi1_flows.sh
./dhclient_tap.sh

ovs-ofctl dump-flows br_tap
