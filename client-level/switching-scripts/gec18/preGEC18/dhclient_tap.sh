dhclient -r br_tap
pkill -9 dhclient
rm /var/lib/dhcp/dhclient.leases
dhclient br_tap
ifconfig br_tap
exit 0
