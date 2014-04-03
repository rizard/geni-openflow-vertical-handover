echo "Flow tables by switch according to OVS..."
echo "br_tap:"
ovs-ofctl dump-flows br_tap

echo "br_eth:"
ovs-ofctl dump-flows br_eth

echo "br_wifi0:"
ovs-ofctl dump-flows br_wifi0

echo "br_wifi1:"
ovs-vsctl dump-flows br_wifi1

#echo "br_wimax:"
#ovs-vsctl dump-flows br_wimax

exit 0

