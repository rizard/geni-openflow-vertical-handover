pkill -9 dhclient
rm /var/lib/dhcp/dhclient.leases
dhclient br_tap
exit 0
