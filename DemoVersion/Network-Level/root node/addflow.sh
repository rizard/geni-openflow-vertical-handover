sudo ovs-ofctl add-flow br_AP1 priority=32767,in_port=LOCAL,eth_type=0x800,ip_proto=6,tp_src=6633,actions=output:2
sudo ovs-ofctl add-flow br_AP1 priority=32767,in_port=2,eth_type=0x800,ip_proto=6,tp_dst=6633,actions=output:LOCAL
sudo ovs-ofctl add-flow br_AP2 priority=32767,in_port=LOCAL,eth_type=0x800,ip_proto=6,tp_src=6633,actions=output:2
sudo ovs-ofctl add-flow br_AP2 priority=32767,in_port=2,eth_type=0x800,ip_proto=6,tp_dst=6633,actions=output:LOCAL
#sudo ovs-ofctl del-flows br_root ip,in_port=LOCAL,nw_src=130.127.49.128
#sudo ovs-ofctl add-flow br_root priority=32500,in_port=LOCAL,actions=output:1

