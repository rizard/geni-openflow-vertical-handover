ovs-ofctl del-flows br_tap
ovs-ofctl add-flow br_tap priority=20000,in_port=1,actions=LOCAL
ovs-ofctl add-flow br_tap priority=20000,in_port=LOCAL,actions=output:1
