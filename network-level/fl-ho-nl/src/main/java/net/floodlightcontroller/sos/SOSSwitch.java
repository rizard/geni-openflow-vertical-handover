package net.floodlightcontroller.sos;

import java.util.ArrayList;

import net.floodlightcontroller.core.IOFSwitch;

public class SOSSwitch {
	private IOFSwitch SWITCH;
	private SOSAgent LOCAL_AGENT;
	private ArrayList<SOSClient> CLIENTS;
	
	public SOSSwitch() {
		SWITCH = null;
		LOCAL_AGENT = null;
		CLIENTS = new ArrayList<SOSClient>();
	}
	public SOSSwitch(IOFSwitch sw) {
		SWITCH = sw;
		LOCAL_AGENT = null;
		CLIENTS = new ArrayList<SOSClient>();
	}
	
	public void setSwitch(IOFSwitch sw) {
		SWITCH = sw;
	}
	public IOFSwitch getSwitch() {
		return SWITCH;
	}
	
	public void setLocalAgent(SOSAgent agent) {
		LOCAL_AGENT = agent;
	}
	public SOSAgent getLocalAgent() {
		return LOCAL_AGENT;
	}
	
	public boolean addClient(SOSClient client) {
		if (!CLIENTS.contains(client)) {
			return CLIENTS.add(client);
		} else {
			return false;
		}
	}
	public boolean removeClient(SOSClient client) {
		return CLIENTS.remove(client);
	}
	
}
