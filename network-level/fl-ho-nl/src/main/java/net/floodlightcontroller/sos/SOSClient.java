package net.floodlightcontroller.sos;

import java.util.ArrayList;
import java.util.Arrays;

public class SOSClient {
	private int IP_ADDR;
	private byte[] MAC_ADDR;
	private SOSAgent MY_AGENT;
	private short SWITCH_PORT;
	private ArrayList<SOSConnection> ACTIVE_CONNECTIONS;
	
	public SOSClient() {
		IP_ADDR = 0;
		MAC_ADDR = null;
		MY_AGENT = null;
		SWITCH_PORT = 0;
		ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
	}
	public SOSClient(int ip, byte[] mac) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		MY_AGENT = null;
		SWITCH_PORT = 0;
		ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
	}
	public SOSClient(int ip, byte[] mac, SOSAgent agent) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		MY_AGENT = agent;
		SWITCH_PORT = 0;
		ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
	}
	public SOSClient(int ip, byte[] mac, short switchPort) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		MY_AGENT = null;
		SWITCH_PORT = switchPort;
		ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
	}
	public SOSClient(int ip, byte[] mac, SOSAgent agent, short switchPort) {
		IP_ADDR = ip;
		MAC_ADDR = Arrays.copyOf(mac, mac.length);
		MY_AGENT = agent;
		SWITCH_PORT = switchPort;
		ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
	}
	
	public boolean addConnection(SOSConnection conn) {
		if (!ACTIVE_CONNECTIONS.contains(conn)) {
			return ACTIVE_CONNECTIONS.add(conn);
		} else {
			return false;
		}
	}
	public boolean removeConnection(SOSConnection conn) {
		return ACTIVE_CONNECTIONS.remove(conn);
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
	
	public void setAgent(SOSAgent agent) {
		MY_AGENT = agent;
	}
	public SOSAgent getAgent() {
		return MY_AGENT;
	}
	
	public void setSwitchPort(short port) {
		SWITCH_PORT = port;
	}
	public short getSwitchPort() {
		return SWITCH_PORT;
	}
	
}
