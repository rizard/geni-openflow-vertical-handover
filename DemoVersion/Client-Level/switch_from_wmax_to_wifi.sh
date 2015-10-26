sh ./connect_ap2.sh
sh ./initial_flows_wifi.sh
sh ./dhclient_tap.sh

ovs-ofctl dump-flows br_tap
