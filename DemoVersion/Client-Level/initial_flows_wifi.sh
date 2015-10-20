ovs-ofctl del-flows br_tap
ovs-ofctl add-flow br_tap priority=20000,in_port=2,actions=LOCAL
ovs-ofctl add-flow br_tap priority=20000,in_port=LOCAL,actions=output:2
#ovs-ofctl add-flow br_tap priority=20000,in_port=LOCAL,actions=mod_dl_src:00:15:6d:84:df:10,output:2
