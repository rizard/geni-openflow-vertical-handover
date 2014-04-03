package net.floodlightcontroller.sos;

import java.util.ArrayList;
import java.util.UUID;

import net.floodlightcontroller.packet.IPv4;

public class SOSConnection {
	private SOSClient SRC_CLIENT;
	private SOSAgent SRC_AGENT;
	private short SRC_PORT;
	private SOSSwitch SRC_AGENT_SWITCH;
	private SOSClient DST_CLIENT;
	private SOSAgent DST_AGENT;
	private short DST_PORT;
	private short DST_AGENT_L4PORT;
	private SOSSwitch DST_AGENT_SWITCH;
	private SOSSwitch SRC_NTWK_SWITCH;
	private SOSSwitch DST_NTWK_SWITCH;
	private UUID TRANSFER_ID;
	private int NUM_PARALLEL_SOCKETS;
	private int QUEUE_CAPACITY;
	private int BUFFER_SIZE;
	private ArrayList<String> FLOW_NAMES;
	
	public SOSConnection(SOSClient srcC, SOSAgent srcA, short srcP, SOSSwitch srcS, 
			SOSClient dstC, SOSAgent dstA, short dstP, SOSSwitch dstS, SOSSwitch srcNtwkS, SOSSwitch dstNtwkS, int numSockets, int queueCap, int bufSize) {
		SRC_CLIENT = srcC;
		SRC_AGENT = srcA;
		SRC_PORT = srcP;
		SRC_AGENT_SWITCH = srcS;
		DST_CLIENT = dstC;
		DST_AGENT = dstA;
		DST_PORT = dstP;
		DST_AGENT_L4PORT = 0; // This cannot be known when the first TCP packet is received. It will be learned on the dst-side
		DST_AGENT_SWITCH = dstS;
		SRC_NTWK_SWITCH = srcNtwkS;
		DST_NTWK_SWITCH = dstNtwkS;
		TRANSFER_ID = UUID.randomUUID();
		NUM_PARALLEL_SOCKETS = numSockets;
		QUEUE_CAPACITY = queueCap;
		BUFFER_SIZE = bufSize;
		FLOW_NAMES = new ArrayList<String>();
	}
	
	/**
	 * Floodlight stores switch and L4 port numbers as shorts. In Java, there is no such thing
	 * as an unsigned short. This means all port numbers greater than 32768 or 2^15 (for 2-byte
	 * shorts) will appear as negative numbers. This poses a problem when printing the ports in
	 * the debugger or converting them to strings (such as the SFP does). If you have a port
	 * that ***might*** be larger than the largest possible number in a signed short (given by
	 * Short.MAX_VALUE), use these methods to make sure the port value does not wrap around and
	 * appear negative as a signed short. The SFP is patched to fix this issue. Use these functions
	 * for local port operations that require the port number appear as positive.
	 * 
	 * @param port
	 * @return
	 */
	public int portToInteger(short port) {
		int temp = (int) port;
    	if (temp < 0 ) {
    		temp = Short.MAX_VALUE*2 + temp + 2;
    	}
    	return temp;
	}
	public String portToString(short port) {
    	return Integer.toString(portToInteger(port));
	}
	
	public short getDstAgentL4Port() {
		return DST_AGENT_L4PORT;
	}
	public void setDstAgentL4Port(short l4port) {
		DST_AGENT_L4PORT = l4port;
	}
	
	public SOSSwitch getSrcAgentSwitch() {
		return SRC_AGENT_SWITCH;
	}
	public SOSSwitch getDstAgentSwitch() {
		return DST_AGENT_SWITCH;
	}
	public SOSSwitch getSrcNtwkSwitch() {
		return SRC_NTWK_SWITCH;
	}
	public SOSSwitch getDstNtwkSwitch() {
		return DST_NTWK_SWITCH;
	}
	
	public SOSAgent getSrcAgent() {
		return SRC_AGENT;
	}
	public SOSAgent getDstAgent() {
		return DST_AGENT;
	}
	
	public SOSClient getSrcClient() {
		return SRC_CLIENT;
	}
	public SOSClient getDstClient() {
		return DST_CLIENT;
	}
	
	public short getSrcPort() {
		return SRC_PORT;
	}
	public short getDstPort() {
		return DST_PORT;
	}
	
	public UUID getTransferID() {
		return TRANSFER_ID;
	}
	
	public int getNumParallelSockets() {
		return NUM_PARALLEL_SOCKETS;
	}
	
	public int getQueueCapacity() {
		return QUEUE_CAPACITY;
	}
	
	public int getBufferSize() {
		return BUFFER_SIZE;
	}
	
	public ArrayList<String> getFlowNames() {
		return FLOW_NAMES;
	}
	public void removeFlow(String flowName) {
		FLOW_NAMES.remove(flowName);
	}
	public void removeFlows() {
		FLOW_NAMES.clear();
	}
	public void addFlow(String flowName) {
		if (!FLOW_NAMES.contains(flowName)) {
			FLOW_NAMES.add(flowName);
		}
	}
	public void addFlows(ArrayList<String> flowNames) {
		for (String flow : flowNames) {
			addFlow(flow);
		}
	}
	public void replaceFlowsWith(ArrayList<String> flowNames) {
		removeFlows();
		addFlows(flowNames);
	}
	
	@Override
	public String toString() {
		/*
		SRC_PORT = srcP;
		DST_PORT = dstP;
		FLOW_NAMES = new ArrayList<String>();*/
		String output;
		output = "Transfer ID: " + TRANSFER_ID.toString() + "\r\n" +
					"|| Sockets: " + NUM_PARALLEL_SOCKETS + "\r\n" +
					"Queue Capacity: " + QUEUE_CAPACITY + "\r\n" +
					"Buffer Size: " + BUFFER_SIZE + "\r\n" +
					"Source Agent Switch: " + SRC_AGENT_SWITCH.getSwitch().getStringId() + "\r\n" +
					"Source Network Switch: " + SRC_NTWK_SWITCH.getSwitch().getStringId() + "\r\n" +
					"Destination Agent Switch: " + DST_AGENT_SWITCH.getSwitch().getStringId() + "\r\n" +
					"Destination Network Switch: " + DST_NTWK_SWITCH.getSwitch().getStringId() + "\r\n" +
					"Source Agent: (" + IPv4.fromIPv4Address(SRC_AGENT.getIPAddr()) + ", " + portToString(SRC_AGENT.getSwitchPort()) + ")\r\n" +
					"Destination Agent: (" + IPv4.fromIPv4Address(DST_AGENT.getIPAddr()) + ", " + portToString(DST_AGENT.getSwitchPort()) + ")\r\n" +
					"Source Client: (" + IPv4.fromIPv4Address(SRC_CLIENT.getIPAddr()) + ", " + portToString(SRC_CLIENT.getSwitchPort()) + ")\r\n" +
					"Destination Client: (" + IPv4.fromIPv4Address(DST_CLIENT.getIPAddr()) + ", " + portToString(DST_CLIENT.getSwitchPort()) + ")\r\n" +
					"Source L4 Port: " + portToString(SRC_PORT) + "\r\n" +
					"Destination L4 Port: " + portToString(DST_PORT) + "\r\n" +
		    		"Destination Agent L4 Port: " + portToString(DST_AGENT_L4PORT) + "\r\n";

		return output;
	}
}
