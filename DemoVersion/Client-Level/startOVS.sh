echo "Loading OVS modules"
modprobe gre
modprobe openvswitch
modprobe libcrc32c

rm /usr/local/etc/openvswitch/conf.db
ovsdb-tool create /usr/local/etc/openvswitch/conf.db /usr/local/share/openvswitch/vswitch.ovsschema

echo "OVS: Creating database"
ovsdb-server --remote=punix:/usr/local/var/run/openvswitch/db.sock \
--remote=db:Open_vSwitch,Open_vSwitch,manager_options \
--private-key=db:Open_vSwitch,SSL,private_key \
--certificate=db:Open_vSwitch,SSL,certificate \
--bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
--pidfile --detach --log-file
echo "OVS: Initializing OVS..."
ovs-vsctl --no-wait init
echo "OVS: Starting OVS..."
ovs-vswitchd --pidfile --detach

