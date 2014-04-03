package net.floodlightcontroller.sos;

import java.util.ArrayList;

public class SOSActiveConnections  {

	private static ArrayList<SOSConnection> ACTIVE_CONNECTIONS = null;
	
	public SOSActiveConnections() {
		if (ACTIVE_CONNECTIONS == null) {
			ACTIVE_CONNECTIONS = new ArrayList<SOSConnection>();
		}
	}
	
	/*public SOSConnection addConnection(SOSClient srcC, SOSAgent srcA, short srcP, SOSSwitch srcNtwkS, SOSSwitch srcAgentS,
			SOSClient dstC, SOSAgent dstA, short dstP, SOSSwitch dstNtwkS, SOSSwitch dstAgentS, int numSockets, int queueCap, int bufSize) {
		ACTIVE_CONNECTIONS.add(new SOSConnection(srcC, srcA, srcP, srcAgentS, 
				dstC, dstA, dstP, dstAgentS, srcNtwkS, dstNtwkS, numSockets, queueCap, bufSize)); 
		return getConnectionFromIP(srcC.getIPAddr(), srcP);
	}*/
	public boolean removeConnection(int ip, short port) {
		for (SOSConnection conn : ACTIVE_CONNECTIONS) {
			if (conn.getSrcClient().getIPAddr() == ip && conn.getSrcPort() == port) {
				ACTIVE_CONNECTIONS.remove(conn);
				return true;
			}
		}
		return false;
	}
	
	public SOSConnection getConnectionFromIP(int ip, short port) {
		for (SOSConnection conn : ACTIVE_CONNECTIONS) {
			if (conn.getSrcClient().getIPAddr() == ip && conn.getSrcPort() == port) {
				return conn;
			} else if (conn.getDstClient().getIPAddr() == ip && conn.getDstPort() == port) {
				return conn;
			}
		}
		return null;
	}
	
	public SOSConnectionPacketMembership isPacketMemberOfActiveConnection(int srcIP, int dstIP, short srcPort, short dstPort) {
		for (SOSConnection conn : ACTIVE_CONNECTIONS) {
			if (conn.getSrcClient().getIPAddr() == srcIP && conn.getSrcPort() == srcPort &&
					conn.getDstClient().getIPAddr() == dstIP ) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_SRC_CLIENT_TO_SRC_AGENT;
				
			} else if (conn.getDstClient().getIPAddr() == srcIP && conn.getDstPort() == srcPort &&
					conn.getSrcClient().getIPAddr() == dstIP) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_DST_CLIENT_TO_DST_AGENT;
				
			} else if (conn.getDstClient().getIPAddr() == dstIP && conn.getDstPort() == dstPort &&
					conn.getDstAgent().getIPAddr() == srcIP) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_DST_AGENT_TO_DST_CLIENT;
				
			} else if (conn.getSrcClient().getIPAddr() == dstIP && conn.getSrcPort() == dstPort && 
					conn.getSrcAgent().getIPAddr() == srcIP) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_SRC_AGENT_TO_SRC_CLIENT;
				
			} else if (conn.getSrcAgent().getIPAddr() == srcIP && conn.getDstAgent().getIPAddr() == dstIP) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_SRC_AGENT_TO_DST_AGENT;
				
			} else if (conn.getDstAgent().getIPAddr() == srcIP && conn.getSrcAgent().getIPAddr() == dstIP) {
				
				return SOSConnectionPacketMembership.ASSOCIATED_DST_AGENT_TO_SRC_AGENT;
			}
		}
		return SOSConnectionPacketMembership.NOT_ASSOCIATED_WITH_ACTIVE_SOS_CONNECTION;
	}
	
	
	
}
