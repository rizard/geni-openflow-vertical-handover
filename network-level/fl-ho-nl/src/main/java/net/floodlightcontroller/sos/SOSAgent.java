package net.floodlightcontroller.sos;

import java.util.ArrayList;
import java.util.Arrays;

public class SOSAgent {
	private int IP_ADDR;
	private byte[] MAC_ADDR;
	private int AGENT_ID;
	private short SWITCH_PORT;
	private ArrayList<SOSClient> CLIENTS;
	
	public SOSAgent() {
		IP_ADDR = 0;
		MAC_ADDR = null;
		AGENT_ID = -1;
		SWITCH_PORT = 0;
		CLIENTS = new ArrayList<SOSClient>();
	}
	public SOSAgent(int ip, byte[] mac, int id) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		AGENT_ID = id;
		SWITCH_PORT = 0;
		CLIENTS = new ArrayList<SOSClient>();
	}
	public SOSAgent(int ip, byte[] mac, short port) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		AGENT_ID = -1;
		SWITCH_PORT = port;
		CLIENTS = new ArrayList<SOSClient>();
	}
	public SOSAgent(int ip, byte[] mac, int id, short port) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		AGENT_ID = id;
		SWITCH_PORT = port;
		CLIENTS = new ArrayList<SOSClient>();
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
	
	public void setIPAddr(int ip) {
		IP_ADDR = ip;
	}
	public int getIPAddr() {
		return IP_ADDR;
	}
	
	public void setMACAddr(byte[] mac) {
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
	}
	public byte[] getMACAddr() {
		return MAC_ADDR;
	}
	
	public void setID(int id) {
		AGENT_ID = id;
	}
	public int getID() {
		return AGENT_ID;
	}
	
	public void setSwitchPort(short port) {
		SWITCH_PORT = port;
	}
	public short getSwitchPort() {
		return SWITCH_PORT;
	}
}
