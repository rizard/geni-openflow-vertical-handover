
./clear_flows.sh
python gec18_switch_to_wifi.py

echo "********br_tap flows********"
ovs-ofctl dump-flows br_tap
echo "********br_wimax flows********"
ovs-ofctl dump-flows br_wimax
echo "********br_wifi0 flows********"
ovs-ofctl dump-flows br_wifi0


exit 0
