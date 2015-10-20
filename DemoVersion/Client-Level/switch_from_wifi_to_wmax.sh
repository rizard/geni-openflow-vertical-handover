sh ./initial_flows_wmax.sh
sh ./dhclient_tap.sh

ovs-ofctl dump-flows br_tap
