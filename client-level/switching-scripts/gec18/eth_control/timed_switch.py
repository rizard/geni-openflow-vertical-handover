#!/usr/bin/python

import subprocess
import time


sleep_time = 30
bash_path = "/bin/bash"
python_path = "/usr/bin/python"
switch_to_wimax = "wimax_switch_0.py"
switch_to_wifi = "wifi_switch_0.py"
switch_to_eth = "eth_switch_0.py"
wimax_horizontal_freq1 = "talk_to_teltonika.sh"
wimax_horizontal_freq2 = "talk_to_teltonika.sh"

while True:
	print "Switching to WiMAX"
	retcode = subprocess.call([python_path, switch_to_wimax], stdout=PIPE, shell=True)
	print "Return code: ", retcode
	time.sleep(sleep_time)

	print "Horizontal Handoff on WiMAX"
	retcode = subprocess.call([bash_path, wimax_horizontal_1], stdout=PIPE, shell=True)
	print "Return code: ", retcode
	time.sleep(sleep_time)
	
	print "Switching to WiFi"
	retcode = subprocess.call([python_path, switch_to_wifi], stdout=PIPE, shell=True)
	print "Return code: ", retcode
	time.sleep(sleep_time)
	
	print "Switching to Ethernet"
	retcode = subprocess.call([python_path, switch_to_eth], stdout=PIPE, shell=True)
	print "Return code: ", retcode
	time.sleep(sleep_time)

