IFACE_C_AP_ESSID=AP2
IFACE_C_AP_MAC=cc:af:78:03:3e:4b
IFACE_C_AP_channel=11
IFACE_C_AP_mode=Managed

iwconfig wlan0 essid $IFACE_C_AP_ESSID ap $IFACE_C_AP_MAC channel $IFACE_C_AP_channel mode $IFACE_C_AP_mode
