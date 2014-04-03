


ovs-vsctl del-br br_wlan0
ovs-vsctl del-br br_wimax
ovs-vsctl del-br br_tap

openvpn --rmtun --dev tap0

pkill -9 ovs
pkill -9 "java -jar /root/floodlight-0.90/target/floodlight.jar"

exit 0
