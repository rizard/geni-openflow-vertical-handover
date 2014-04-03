ifconfig ath4 down
ifconfig br_tap down
ifconfig br_wifi0 down
ifconfig tap0 down
dhclient -r ath4
dhclient -r br_tap
dhclient -r br_wifi0
dhclient -r tap0
ifconfig -v ath4 promisc allmulti up
ifconfig -v br_wifi0 promisc allmulti up
ifconfig -v br_tap promisc allmulti up
ifconfig -v tap0 promisc allmulti up
iwconfig ath4 essid MyAP ap 00:0C:42:6A:4F:5E channel 11 mode Managed
route add default dev br_tap
dhclient -4 br_tap -d
exit 0
