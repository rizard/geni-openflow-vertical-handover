package net.floodlightcontroller.sos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/*TODO: We might eventually use these to automatically remove flows
 * import java.util.concurrent.ScheduledThreadPoolExecutor;
 * import java.util.concurrent.TimeUnit;
 */

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.util.U16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
//import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;


/**
 * Steroid OpenFlow Service Module
 * @author Ryan Izard, rizard@g.clemson.edu
 * 
 */
public class SOS implements IOFMessageListener, IOFSwitchListener, IFloodlightModule  {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	private static byte[] CONTROLLER_MAC;
	private static int CONTROLLER_IP;
	private static short AGENT_UDP_MSG_IN_PORT; // 9998
	private static short AGENT_TCP_IN_PORT; // 9877

	private static SOSAgent SRC_AGENT;
	private static SOSAgent DST_AGENT;

	private static SOSClient SRC_CLIENT;
	private static SOSClient DST_CLIENT;

	private static SOSSwitch SRC_NTWK_SWITCH;
	private static SOSSwitch DST_NTWK_SWITCH;
	private static SOSSwitch SRC_AGENT_SWITCH;
	private static SOSSwitch DST_AGENT_SWITCH;
	/*TODO: maintain a list of SOS switches so we can algorithmically determine the source
	 * and destination agent switches.
	 * private static ArrayList<SOSSwitch> SOS_SWITCHES;
	 */

	private static SOSActiveConnections SOS_CONNECTIONS;

	private static int BUFFER_SIZE;
	private static int QUEUE_CAPACITY;
	private static int PARALLEL_SOCKETS;
	private static short FLOW_TIMEOUT; // TODO: have a timeout/gc thread to clean up old flows (since static flows do not support idle/hard timeouts)
	
	/* These are things that will be automated with a discovery service */
	private static byte[] SRC_CLIENT_MAC;
	private static int SRC_CLIENT_IP;
	private static short SRC_CLIENT_PORT;
	private static byte[] DST_CLIENT_MAC;
	private static int DST_CLIENT_IP;
	private static short DST_CLIENT_PORT;
	
	private static byte[] SRC_AGENT_MAC;
	private static int SRC_AGENT_IP;
	private static short SRC_AGENT_PORT;
	private static byte[] DST_AGENT_MAC;
	private static int DST_AGENT_IP;
	private static short DST_AGENT_PORT;
	
	private static String SRC_AGENT_SWITCH_DPID;
	private static String DST_AGENT_SWITCH_DPID;
	private static String SRC_NTWK_SWITCH_DPID;
	private static String DST_NTWK_SWITCH_DPID;
	
	/* TODO: Use these if we need to to insert flows to the delay machine's ports (or simply to a real network) */
	private static short SRC_NTWK_PORT;
	private static short DST_NTWK_PORT;

	public static final byte[] BROADCAST_MAC = Ethernet.toMACAddress("FF:FF:FF:FF:FF:FF");
	public static final int BROADCAST_IP = IPv4.toIPv4Address(IPv4.toIPv4AddressBytes("255.255.255.255"));
	public static final int UNASSIGNED_IP = IPv4.toIPv4Address(IPv4.toIPv4AddressBytes("0.0.0.0"));

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		log = LoggerFactory.getLogger(SOS.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFSwitchListener(this);

		// Read our config options
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			CONTROLLER_MAC = Ethernet.toMACAddress(configOptions.get("controller-mac"));
			CONTROLLER_IP = IPv4.toIPv4Address(configOptions.get("controller-ip"));

			BUFFER_SIZE = Integer.parseInt(configOptions.get("buffer-size"));
			QUEUE_CAPACITY = Integer.parseInt(configOptions.get("queue-capacity"));
			PARALLEL_SOCKETS = Integer.parseInt(configOptions.get("parallel-tcp-sockets"));
			FLOW_TIMEOUT = Short.parseShort(configOptions.get("flow-timeout"));
			
			AGENT_UDP_MSG_IN_PORT = Short.parseShort(configOptions.get("agent-msg-port"));
			AGENT_TCP_IN_PORT = Short.parseShort(configOptions.get("agent-tcp-port"));
			
			// Get rid of these after we implement a discovery service
			SRC_CLIENT_MAC = Ethernet.toMACAddress(configOptions.get("src-client-mac"));
			SRC_CLIENT_IP = IPv4.toIPv4Address(configOptions.get("src-client-ip"));
			SRC_CLIENT_PORT = Short.parseShort(configOptions.get("src-client-sw-port"));
			DST_CLIENT_MAC = Ethernet.toMACAddress(configOptions.get("dst-client-mac"));
			DST_CLIENT_IP = IPv4.toIPv4Address(configOptions.get("dst-client-ip"));
			DST_CLIENT_PORT = Short.parseShort(configOptions.get("dst-client-sw-port"));
			
			SRC_AGENT_MAC = Ethernet.toMACAddress(configOptions.get("src-agent-mac"));
			SRC_AGENT_IP = IPv4.toIPv4Address(configOptions.get("src-agent-ip"));
			SRC_AGENT_PORT = Short.parseShort(configOptions.get("src-agent-sw-port"));
			DST_AGENT_MAC = Ethernet.toMACAddress(configOptions.get("dst-agent-mac"));
			DST_AGENT_IP = IPv4.toIPv4Address(configOptions.get("dst-agent-ip"));
			DST_AGENT_PORT = Short.parseShort(configOptions.get("dst-agent-sw-port"));
			
			SRC_AGENT_SWITCH_DPID = configOptions.get("src-agent-switch-dpid");
			DST_AGENT_SWITCH_DPID = configOptions.get("dst-agent-switch-dpid");
			SRC_NTWK_SWITCH_DPID = configOptions.get("src-ntwk-switch-dpid");
			DST_NTWK_SWITCH_DPID = configOptions.get("dst-ntwk-switch-dpid");
			
			SRC_NTWK_PORT = Short.parseShort(configOptions.get("src-ntwk-sw-port"));
			DST_NTWK_PORT = Short.parseShort(configOptions.get("dst-ntwk-sw-port"));
			
		} catch(IllegalArgumentException ex) {
			log.error("Incorrect SOS configuration options", ex);
			throw ex;
		} catch(NullPointerException ex) {
			log.error("Incorrect SOS configuration options", ex);
			throw ex;
		}
		
		SOS_CONNECTIONS = new SOSActiveConnections();
		// Do this later when we use discovery SOS_SWITCHES = new ArrayList<SOSSwitch>();
		
		SRC_AGENT = new SOSAgent(SRC_AGENT_IP, SRC_AGENT_MAC, 0, SRC_AGENT_PORT);
		DST_AGENT = new SOSAgent(DST_AGENT_IP, DST_AGENT_MAC, 1, DST_AGENT_PORT);
		
		SRC_CLIENT = new SOSClient(SRC_CLIENT_IP, SRC_CLIENT_MAC, SRC_AGENT, SRC_CLIENT_PORT);
		DST_CLIENT = new SOSClient(DST_CLIENT_IP, DST_CLIENT_MAC, DST_AGENT, DST_CLIENT_PORT);
		
		SRC_NTWK_SWITCH = new SOSSwitch();
		DST_NTWK_SWITCH = new SOSSwitch();
		
		SRC_AGENT_SWITCH = new SOSSwitch();
		DST_AGENT_SWITCH = new SOSSwitch();
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public String getName() {
		return SOS.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// We still want forwarding to insert flows for non-TCP packets (i.e. Forwarding after SOS) 
		if (type == OFType.PACKET_IN && name.equals(Forwarding.class.getSimpleName())) {
			return true;
		} else {
			return false;
		}
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

	/**
	 * Send an OF packet with the UDP packet encapsulated inside. This packet is destined for
	 * the agent. 
	 * 
	 * @param conn, The associated SOSConnection for the UDP info packets.
	 * @param isSourceAgent, Send to source agent (true); send to destination agent (false).
	 */
	public void sendInfoToAgent(FloodlightContext cntx, SOSConnection conn, boolean isSourceAgent) {
		/* OF packet to send to the switches. The switches will be the switch
		 * of the source agent and the switch of the destination agent
		 */
		OFPacketOut ofPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		ofPacket.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		
		/* L2 of packet */
		Ethernet l2 = new Ethernet();
		l2.setSourceMACAddress(CONTROLLER_MAC);
		l2.setDestinationMACAddress(isSourceAgent ? conn.getSrcAgent().getMACAddr() : conn.getDstAgent().getMACAddr());
		l2.setEtherType(Ethernet.TYPE_IPv4);

		/* L3 of packet */
		IPv4 l3 = new IPv4();
		l3.setSourceAddress(isSourceAgent ? conn.getDstAgent().getIPAddr() : conn.getDstClient().getIPAddr());
		l3.setDestinationAddress(isSourceAgent ? conn.getSrcAgent().getIPAddr() : conn.getDstAgent().getIPAddr());
		l3.setProtocol(IPv4.PROTOCOL_UDP);
		l3.setTtl((byte) 64);

		/* L4 of packet */
		UDP l4 = new UDP();
		l4.setSourcePort(conn.getDstPort());
		l4.setDestinationPort(AGENT_UDP_MSG_IN_PORT);


		/* Convert the string into IPacket. Data extends BasePacket, which is an abstract class
		 * that implements IPacket. The only class variable of Data is the byte[] 'data'. The 
		 * deserialize() method of Data is the following:
		 * 
		 *  public IPacket deserialize(byte[] data, int offset, int length) {
		 *      this.data = Arrays.copyOfRange(data, offset, data.length);
		 *      return this;
		 *  }
		 *  
		 *  We provide the byte[] form of the string (ASCII code bytes) as 'data', and 0 as the
		 *  'offset'. The 'length' is not used and instead replaced with the length of the byte[].
		 *  Notice 'this' is returned. 'this' is the current Data object, which only has the single
		 *  byte[] 'data' as a class variable. This means that when 'this' is returned, it will
		 *  simply be a byte[] form of the original string as an IPacket instead.
		 */

		String agentInfo = null;
		if (isSourceAgent) {
			/*payload = "CLIENT " + str(transfer_id) + 
					" " + ip_to_str(packet.next.srcip)  + 
					" " + str(packet.next.next.srcport) + 
					" " +  ip_to_str(inst.Agent[FA]['ip']) + 
					" "  + str(NUM_CONNECTIONS) + 
					" "  + str(BUFSIZE) + 
					" " +str(MAX_QUEUE_SIZE) */
			log.debug(conn.getTransferID().toString());
			agentInfo = "CLIENT " + conn.getTransferID().toString() + 
					" " + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) +
					" " + portToString(conn.getSrcPort()) +
					" " + IPv4.fromIPv4Address(conn.getDstAgent().getIPAddr()) +
					" " + Integer.toString(conn.getNumParallelSockets()) +
					" " + Integer.toString(conn.getBufferSize()) +
					" " + Integer.toString(conn.getQueueCapacity());
		} else {
			/*payload = "AGENT " + str(transfer_id) + 
				" " + ip_to_str(packet.next.dstip)  + 
				" " + str(packet.next.next.dstport) + 
				"  " + str(NUM_CONNECTIONS) + 
				" " + str(BUFSIZE) + 
				" " + str(MAX_QUEUE_SIZE) */
			agentInfo = "AGENT " + conn.getTransferID().toString() + 
					" " + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
					" " + portToString(conn.getDstPort()) +
					/* I suppose the destination agent will learn the source agent IP
					 * after it receives the first TCP packet (hopefully this is true)
					 */
					" " + Integer.toString(conn.getNumParallelSockets()) +
					" " + Integer.toString(conn.getBufferSize()) +
					" " + Integer.toString(conn.getQueueCapacity());
		}

		Data payloadData = new Data();

		/* Construct the packet layer-by-layer */
		l2.setPayload(l3.setPayload(l4.setPayload(payloadData.setData(agentInfo.getBytes()))));

		/* Tell the switch what to do with the packet. This is specified as an OFAction.
		 * i.e. Which port should it go out?
		 */
		ofPacket.setInPort(OFPort.OFPP_NONE.getValue());
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(OFPort.OFPP_LOCAL.getValue(), (short) 0xffff));
		ofPacket.setActions(actions);
		ofPacket.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

		/* Put the UDP packet in the OF packet (encapsulate it as an OF packet) */
		byte[] udpPacket = l2.serialize();
		ofPacket.setPacketData(udpPacket);
		ofPacket.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH + ofPacket.getActionsLength() + udpPacket.length));

		/* Send the OF packet to the agent switch.
		 * The switch will look at the OpenFlow action and send the encapsulated
		 * UDP packet out the port specified.
		 */
		try {
			if (isSourceAgent) {
				SRC_AGENT_SWITCH.getSwitch().write(ofPacket, cntx);
			} else {
				DST_AGENT_SWITCH.getSwitch().write(ofPacket, cntx);
			}
		} catch (IOException e) {
			System.out.println("Failed to write {} to agent switch {}: {}");
		}
	}
	/**
	 * Send an OF packet with the TCP "spark" packet (the packet that "sparked" the SOS session)
	 * encapsulated inside. This packet is destined for the source agent. 
	 * 
	 * @param l2, the Ethernet packet received by the SOS module.
	 * @param conn, The associated SOSConnection for the UDP info packets.
	 */
	public void sendSrcSparkPacket(FloodlightContext cntx, Ethernet l2, SOSConnection conn) {
		/* OF packet to send to the switches. The switches will be the switch
		 * of the source agent and the switch of the destination agent
		 */
		OFPacketOut ofPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		ofPacket.setBufferId(-1);

		/* L2 of packet
		 * Change the dst MAC to the agent
		 */
		l2.setDestinationMACAddress(conn.getSrcAgent().getMACAddr());

		/* L3 of packet 
		 * Change the dst IP to the agent
		 */
		IPv4 l3 = (IPv4) l2.getPayload();
		l3.setDestinationAddress(conn.getSrcAgent().getIPAddr());

		/* L4 of packet 
		 * Change destination TCP port to the one the agent is listening to
		 */
		TCP l4 = (TCP) l3.getPayload();
		l4.setDestinationPort(AGENT_TCP_IN_PORT);

		/* Reconstruct the packet layer-by-layer 
		 * Only the L2 MAC and L3 IP were changed.
		 * L4 info is preserved in l3 payload.
		 */
		l3.setPayload(l4);
		l2.setPayload(l3);

		/* Tell the switch what to do with the packet. This is specified as an OFAction.
		 * i.e. Which port should it go out?
		 */
		ofPacket.setInPort(OFPort.OFPP_NONE.getValue());
		ofPacket.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		List<OFAction> actions = new ArrayList<OFAction>();
		/* Output to the source agent */
		//actions.add(new OFActionOutput(conn.getSrcAgent().getSwitchPort(), (short) 0xffff));
		actions.add(new OFActionOutput(OFPort.OFPP_LOCAL.getValue(), (short) 0xffff));
		ofPacket.setActions(actions);
		short packetOutLength = (short) (OFPacketOut.MINIMUM_LENGTH + ofPacket.getActionsLength());
		
		/* Put the TCP spark packet in the OF packet (encapsulate it as an OF packet) */
		ofPacket.setPacketData(l2.serialize());
		packetOutLength = (short) (packetOutLength + l2.serialize().length);
		ofPacket.setLength(packetOutLength);

		/* Send the OF packet to the agent switch.
		 * The switch will look at the OpenFlow action and send the encapsulated
		 * UDP packet out the port specified.
		 */
		try {	
			//conn.getSrcSwitch().getSwitch().write(ofPacket, cntx);
			SRC_AGENT_SWITCH.getSwitch().write(ofPacket, cntx);
		} catch (IOException e) {
			System.out.println("Failed to write {} to agent switch {}: {}");
		}
	}
	/**
	 * Send an OF packet with the TCP "spark" packet (the packet that "sparked" the SOS session)
	 * encapsulated inside. This packet is destined for the source agent. 
	 * 
	 * @param l2, the Ethernet packet received by the SOS module.
	 * @param conn, The associated SOSConnection for the UDP info packets.
	 */
	public void sendDstSparkPacket(FloodlightContext cntx, Ethernet l2, SOSConnection conn) {
		/* OF packet to send to the switches. The switches will be the switch
		 * of the source agent and the switch of the destination agent
		 */
		OFPacketOut ofPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		ofPacket.setBufferId(-1);

		/* L2 of packet
		 * Change the dst MAC to the agent
		 */
		l2.setSourceMACAddress(conn.getSrcClient().getMACAddr());

		/* L3 of packet 
		 * Change the dst IP to the agent
		 */
		IPv4 l3 = (IPv4) l2.getPayload();
		l3.setSourceAddress(conn.getSrcClient().getIPAddr());

		/* L4 of packet 
		 * Change destination TCP port to the one the agent is listening to
		 */
		TCP l4 = (TCP) l3.getPayload();
		l4.setSourcePort(conn.getDstAgentL4Port());

		/* Reconstruct the packet layer-by-layer 
		 * Only the L2 MAC and L3 IP were changed.
		 * L4 info is preserved in l3 payload.
		 */
		l3.setPayload(l4);
		l2.setPayload(l3);

		/* Tell the switch what to do with the packet. This is specified as an OFAction.
		 * i.e. Which port should it go out?
		 */
		ofPacket.setInPort(OFPort.OFPP_NONE.getValue());
		ofPacket.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		List<OFAction> actions = new ArrayList<OFAction>();
		/* Output to the source agent */
		//actions.add(new OFActionOutput(conn.getDstClient().getSwitchPort(), (short) 0xffff));
		actions.add(new OFActionOutput(OFPort.OFPP_LOCAL.getValue(), (short) 0xffff));
		ofPacket.setActions(actions);
		short packetOutLength = (short) (OFPacketOut.MINIMUM_LENGTH + ofPacket.getActionsLength());
		
		/* Put the TCP spark packet in the OF packet (encapsulate it as an OF packet) */
		ofPacket.setPacketData(l2.serialize());
		packetOutLength = (short) (packetOutLength + l2.serialize().length);
		ofPacket.setLength(packetOutLength);

		/* Send the OF packet to the agent switch.
		 * The switch will look at the OpenFlow action and send the encapsulated
		 * UDP packet out the port specified.
		 */
		try {	
			//conn.getSrcSwitch().getSwitch().write(ofPacket, cntx);
			DST_AGENT_SWITCH.getSwitch().write(ofPacket, cntx);
		} catch (IOException e) {
			System.out.println("Failed to write {} to agent switch {}: {}");
		}
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		if (!sw.getStringId().equals(SRC_AGENT_SWITCH_DPID) && !sw.getStringId().equals(DST_AGENT_SWITCH_DPID)
				&& !sw.getStringId().equals(SRC_NTWK_SWITCH_DPID) && !sw.getStringId().equals(DST_NTWK_SWITCH_DPID)) {
			return Command.CONTINUE;
		}
		
		OFPacketIn pi = (OFPacketIn) msg;
		
		Ethernet l2 = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (l2.getEtherType() == Ethernet.TYPE_IPv4) {
			//log.debug("Got IPv4 Packet");
			IPv4 l3 = (IPv4) l2.getPayload();

			if (l3.getProtocol() == IPv4.PROTOCOL_TCP) {
				log.debug("Got TCP Packet on port " + pi.getInPort() + " of switch " + sw.getStringId());
				TCP l4 = (TCP) l3.getPayload();
				/* If this source IP and source port (or destination IP and destination port)
				 * have already been assigned a connection then we really shouldn't get to 
				 * this point. Flows matching the source IP and source port should have already \
				 * been inserted switching those packets to the source agent. 
				 */

				/* Lookup the source IP address to see if it belongs to a client with a connection */
				log.debug("(" + Short.toString(l4.getSourcePort()) + ", " + Short.toString(l4.getDestinationPort()) + ")");
				
				SOSConnectionPacketMembership packetStatus = SOS_CONNECTIONS.isPacketMemberOfActiveConnection(
						l3.getSourceAddress(), l3.getDestinationAddress(),
						l4.getSourcePort(), l4.getDestinationPort()); 
				
				if (packetStatus == SOSConnectionPacketMembership.NOT_ASSOCIATED_WITH_ACTIVE_SOS_CONNECTION){
					/* Process new TCP SOS session */
					SOSConnection sc13connection = SOS_CONNECTIONS.addConnection(SRC_CLIENT, SRC_AGENT, l4.getSourcePort(), SRC_NTWK_SWITCH, SRC_AGENT_SWITCH,
							DST_CLIENT, DST_AGENT, l4.getDestinationPort(), DST_NTWK_SWITCH, DST_AGENT_SWITCH, PARALLEL_SOCKETS, QUEUE_CAPACITY, BUFFER_SIZE);
					log.debug("Starting new SOS session: \r\n" + sc13connection.toString());
					
					/* Push flows and add flow names to connection (for removal upon termination) */
					log.debug("Pushing source-side and inter-agent SOS flows");
					pushSOSFlow_1(sc13connection);
					pushSOSFlow_2(sc13connection);
					pushSOSFlow_3(sc13connection);
					pushSOSFlow_4(sc13connection);
					pushSOSFlow_5(sc13connection);
					pushSOSFlow_6(sc13connection);
					pushSOSFlow_7(sc13connection);
					pushSOSFlow_8(sc13connection);
					pushSOSFlow_9(sc13connection);
					pushSOSFlow_10(sc13connection);
					
					/* Send the initial TCP packet that triggered this module to the home agent */
					log.debug("Sending source-side spark packet to source agent");
					sendSrcSparkPacket(cntx, l2, sc13connection);
					
					/* Send UDP messages to the home and foreign agents */
					log.debug("Sending UDP information packets to source and destination agents");
					sendInfoToAgent(cntx, sc13connection, true); // home
					sendInfoToAgent(cntx, sc13connection, false); // foreign
					
				} else if (packetStatus == SOSConnectionPacketMembership.ASSOCIATED_DST_AGENT_TO_DST_CLIENT) {					
					SOSConnection conn = SOS_CONNECTIONS.getConnectionFromIP(l3.getDestinationAddress(), l4.getDestinationPort());
					
					if (conn == null) {
						log.error("Should have found an SOSConnection in need of a dst-agent L4 port!");
					} else {
						conn.setDstAgentL4Port(l4.getSourcePort());
						log.debug("Finalizing SOS session: \r\n" + conn.toString());

						log.debug("Pushing destination-side SOS flows");
						pushSOSFlow_11(conn);
						pushSOSFlow_12(conn);
						pushSOSFlow_13(conn);
						pushSOSFlow_14(conn);
						
						log.debug("Sending destination-side spark packet to destination client");
						sendDstSparkPacket(cntx, l2, conn);
					}
				} else {
					log.error("Received a TCP packet that belongs to an ongoing SOS session. Check accuracy of flows!");
				}

				/* We don't want other modules messing with our SOS TCP session (namely Forwarding) */
				return Command.STOP;
				
			} // END IF TCP packet
		} // END IF IPv4 packet
		return Command.CONTINUE;
	} // END of receive(pkt)

	public void pushSOSFlow_1(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionDataLayerDestination dldestAction = new OFActionDataLayerDestination();

		match.setInputPort(conn.getSrcClient().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getSrcClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportSource(conn.getSrcPort());

		dldestAction.setType(OFActionType.SET_DL_DST);
		dldestAction.setDataLayerAddress(conn.getSrcAgent().getMACAddr());
		dldestAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
		actionList.add(dldestAction);

		outputAction.setType(OFActionType.OUTPUT); // Always add output actions last (executed in order)
		outputAction.setPort(conn.getSrcAgent().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getSrcAgent().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + dldestAction.getLengthU() + outputAction.getLengthU());
		
		String flowName = "sos-1-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcNtwkSwitch().getSwitch().getStringId() + flowName);
	}
	public void pushSOSFlow_2(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionNetworkLayerDestination nwdestAction = new OFActionNetworkLayerDestination();
		OFActionTransportLayerDestination tpdestAction = new OFActionTransportLayerDestination();

		match.setInputPort((short) 1);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getSrcClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportSource(conn.getSrcPort());

		tpdestAction.setType(OFActionType.SET_TP_DST);
		tpdestAction.setTransportPort(AGENT_TCP_IN_PORT);
		tpdestAction.setLength((short) OFActionTransportLayerDestination.MINIMUM_LENGTH);
		actionList.add(tpdestAction);
		
		nwdestAction.setType(OFActionType.SET_NW_DST);
		nwdestAction.setNetworkAddress(conn.getSrcAgent().getIPAddr());
		nwdestAction.setLength((short) OFActionNetworkLayerDestination.MINIMUM_LENGTH);
		actionList.add(nwdestAction);

		outputAction.setType(OFActionType.OUTPUT); 
		outputAction.setPort(OFPort.OFPP_LOCAL.getValue());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + + tpdestAction.getLengthU() + nwdestAction.getLengthU() 
				+ outputAction.getLengthU());
		
		String flowName = "sos-2-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcAgentSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_3(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionNetworkLayerSource nwsrcAction = new OFActionNetworkLayerSource();
		OFActionTransportLayerSource tpsrcAction = new OFActionTransportLayerSource();

		match.setInputPort(OFPort.OFPP_LOCAL.getValue());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getSrcClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportDestination(conn.getSrcPort());

		tpsrcAction.setType(OFActionType.SET_TP_SRC);
		tpsrcAction.setTransportPort(conn.getDstPort());
		tpsrcAction.setLength((short) OFActionTransportLayerSource.MINIMUM_LENGTH);
		actionList.add(tpsrcAction);
		
		nwsrcAction.setType(OFActionType.SET_NW_SRC);
		nwsrcAction.setNetworkAddress(conn.getDstClient().getIPAddr());
		nwsrcAction.setLength((short) OFActionNetworkLayerSource.MINIMUM_LENGTH);
		actionList.add(nwsrcAction);

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort((short) 1);
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort((short) 1);
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + tpsrcAction.getLengthU() + nwsrcAction.getLengthU() 
				+ outputAction.getLengthU());
		
		String flowName = "sos-3-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcAgentSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_4(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionDataLayerSource dlsrcAction = new OFActionDataLayerSource();

		match.setInputPort(conn.getSrcAgent().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getSrcClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportDestination(conn.getSrcPort());

		dlsrcAction.setType(OFActionType.SET_DL_SRC);
		dlsrcAction.setDataLayerAddress(conn.getDstClient().getMACAddr());
		dlsrcAction.setLength((short) OFActionDataLayerSource.MINIMUM_LENGTH);
		actionList.add(dlsrcAction);

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(conn.getSrcClient().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getSrcClient().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + dlsrcAction.getLengthU() + outputAction.getLengthU());
		
		String flowName = "sos-4-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcNtwkSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_5(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort(OFPort.OFPP_LOCAL.getValue());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getDstAgent().getIPAddr()); 
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort((short) 1);
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort((short) 1);
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-5-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcAgentSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_6(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort(conn.getSrcAgent().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getDstAgent().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(conn.getDstAgent().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getDstAgent().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-6-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcNtwkSwitch().getSwitch().getStringId() + flowName);
	}

	public void pushSOSFlow_7(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort((short) 1);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getSrcAgent().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(OFPort.OFPP_LOCAL.getValue());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-7-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstAgentSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_8(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort(OFPort.OFPP_LOCAL.getValue());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getSrcAgent().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort((short) 1);
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort((short) 1);
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-8-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstAgentSwitch().getSwitch().getStringId() + flowName);
	} 
	
	public void pushSOSFlow_9(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort(conn.getDstAgent().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getSrcAgent().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(conn.getSrcAgent().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getSrcAgent().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-d9-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstNtwkSwitch().getSwitch().getStringId() + flowName);
	}
	

	public void pushSOSFlow_10(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();

		match.setInputPort((short) 1);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getDstAgent().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		// Don't care about the TCP port number

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(OFPort.OFPP_LOCAL.getValue());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU());
		
		String flowName = "sos-10-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getSrcAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getSrcAgentSwitch().getSwitch().getStringId() + flowName);
	} 

	public void pushSOSFlow_11(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionNetworkLayerSource nwsrcAction = new OFActionNetworkLayerSource();
		OFActionTransportLayerSource tpsrcAction = new OFActionTransportLayerSource();
		
		match.setInputPort(OFPort.OFPP_LOCAL.getValue());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getDstClient().getIPAddr());
		match.setTransportDestination(conn.getDstPort());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);

		tpsrcAction.setType(OFActionType.SET_TP_SRC);
		tpsrcAction.setTransportPort(conn.getSrcPort());
		tpsrcAction.setLength((short) OFActionTransportLayerSource.MINIMUM_LENGTH);
		actionList.add(tpsrcAction);
		
		nwsrcAction.setType(OFActionType.SET_NW_SRC);
		nwsrcAction.setNetworkAddress(conn.getSrcClient().getIPAddr());
		nwsrcAction.setLength((short) OFActionNetworkLayerSource.MINIMUM_LENGTH);
		actionList.add(nwsrcAction);

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort((short) 1);
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort((short) 1);
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + tpsrcAction.getLengthU() + nwsrcAction.getLengthU() 
				+ outputAction.getLengthU());
		
		String flowName = "sos-11-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstAgentSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_12(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionDataLayerSource dlsrcAction = new OFActionDataLayerSource();
		
		match.setInputPort(conn.getDstAgent().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkDestination(conn.getDstClient().getIPAddr());
		match.setTransportDestination(conn.getDstPort());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);

		dlsrcAction.setType(OFActionType.SET_DL_SRC);
		dlsrcAction.setDataLayerAddress(conn.getSrcClient().getMACAddr());
		dlsrcAction.setLength((short) OFActionDataLayerSource.MINIMUM_LENGTH);
		actionList.add(dlsrcAction);
		
		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(conn.getDstClient().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getDstClient().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + dlsrcAction.getLengthU() + outputAction.getLengthU());
		
		String flowName = "sos-12-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstNtwkSwitch().getSwitch().getStringId() + flowName);
	}

	public void pushSOSFlow_13(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionDataLayerDestination dldestAction = new OFActionDataLayerDestination();
		
		match.setInputPort(conn.getDstClient().getSwitchPort());
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getDstClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportSource(conn.getDstPort());

		dldestAction.setType(OFActionType.SET_DL_DST);
		dldestAction.setDataLayerAddress(conn.getDstAgent().getMACAddr());
		dldestAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
		actionList.add(dldestAction);

		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(conn.getDstAgent().getSwitchPort());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(conn.getDstAgent().getSwitchPort());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + dldestAction.getLengthU() + outputAction.getLengthU());
		
		String flowName = "sos-13-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstNtwkSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstNtwkSwitch().getSwitch().getStringId() + flowName);
	}
	
	public void pushSOSFlow_14(SOSConnection conn) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction = new OFActionOutput();
		OFActionNetworkLayerDestination nwdestAction = new OFActionNetworkLayerDestination();
		OFActionTransportLayerDestination tpdestAction = new OFActionTransportLayerDestination();
		
		match.setInputPort((short) 1);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkSource(conn.getDstClient().getIPAddr());
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setTransportSource(conn.getDstPort());

		tpdestAction.setType(OFActionType.SET_TP_DST);
		tpdestAction.setTransportPort(conn.getDstAgentL4Port());
		tpdestAction.setLength((short) OFActionTransportLayerDestination.MINIMUM_LENGTH);
		actionList.add(tpdestAction);
		
		nwdestAction.setType(OFActionType.SET_NW_DST);
		nwdestAction.setNetworkAddress(conn.getDstAgent().getIPAddr());
		nwdestAction.setLength((short) OFActionNetworkLayerDestination.MINIMUM_LENGTH);
		actionList.add(nwdestAction);
		
		outputAction.setType(OFActionType.OUTPUT);
		outputAction.setPort(OFPort.OFPP_LOCAL.getValue());
		outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(outputAction);

		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
		flow.setActions(actionList);
		flow.setMatch(match);
		flow.setPriority((short) 32768);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + tpdestAction.getLengthU() + nwdestAction.getLengthU() 
				+ outputAction.getLengthU());
		
		String flowName = "sos-14-" + IPv4.fromIPv4Address(conn.getSrcClient().getIPAddr()) + 
				"-" + portToString(conn.getSrcPort()) + 
				"-to-" + IPv4.fromIPv4Address(conn.getDstClient().getIPAddr()) +
				"-" + portToString(conn.getDstPort());
		sfp.addFlow(flowName, flow, conn.getDstAgentSwitch().getSwitch().getStringId());
		conn.addFlow(flowName);
		log.info("added flow on SW " + conn.getDstAgentSwitch().getSwitch().getStringId() + flowName);
	}	
	

	
	@Override
	public void addedSwitch(IOFSwitch sw) {
		// For now, let's assume we have two DPIDs as in our config file.
		if (SRC_NTWK_SWITCH_DPID.equals(sw.getStringId())) {
			SRC_NTWK_SWITCH = new SOSSwitch(sw);
			SRC_NTWK_SWITCH.addClient(SRC_CLIENT);
			SRC_NTWK_SWITCH.setLocalAgent(SRC_AGENT);
			log.debug("Source NTWK switch set and configured!");
		}
		// Not an else-if... we may want two agents on a single switch for whatever reason
		if (DST_NTWK_SWITCH_DPID.equals(sw.getStringId())) {
			DST_NTWK_SWITCH = new SOSSwitch(sw);
			DST_NTWK_SWITCH.addClient(DST_CLIENT);
			DST_NTWK_SWITCH.setLocalAgent(DST_AGENT);
			log.debug("Destination NTWK switch set and configured!");
		}
		// For now, let's assume we have two DPIDs as in our config file.
		if (SRC_AGENT_SWITCH_DPID.equals(sw.getStringId())) {
			SRC_AGENT_SWITCH = new SOSSwitch(sw);
			SRC_AGENT_SWITCH.addClient(SRC_CLIENT);
			SRC_AGENT_SWITCH.setLocalAgent(SRC_AGENT);
			log.debug("Source AGENT switch set and configured!");
		}
		// Not an else-if... we may want two agents on a single switch for whatever reason
		if (DST_AGENT_SWITCH_DPID.equals(sw.getStringId())) {
			DST_AGENT_SWITCH = new SOSSwitch(sw);
			DST_AGENT_SWITCH.addClient(DST_CLIENT);
			DST_AGENT_SWITCH.setLocalAgent(DST_AGENT);
			log.debug("Destination AGENT switch set and configured!");
		}
		
		/* 
		 * In the future, we'll do somthing like this...
		for (SOSSwitch sosSw : SOS_SWITCHES) {
			if (sosSw.getSwitch() == sw) {
				// If we find it, then it's already there, so return w/o adding
				return;
			}
		}
		// We must not have found it, so add it
		SOS_SWITCHES.add(new SOSSwitch(sw));
		*/
	}

	@Override
	public void removedSwitch(IOFSwitch sw) {
		// For now, let's assume we only have two DPIDs as in our config file.
		if (SRC_NTWK_SWITCH_DPID.equals(sw.getStringId())) {
			SRC_NTWK_SWITCH.removeClient(SRC_CLIENT);
			SRC_NTWK_SWITCH = null;
		} else if (DST_NTWK_SWITCH_DPID.equals(sw.getStringId())) {
			DST_NTWK_SWITCH.removeClient(DST_CLIENT);
			DST_NTWK_SWITCH = null;
		} else if (SRC_AGENT_SWITCH_DPID.equals(sw.getStringId())) {
			SRC_AGENT_SWITCH.removeClient(SRC_CLIENT);
			SRC_AGENT_SWITCH = null;
		} else if (DST_AGENT_SWITCH_DPID.equals(sw.getStringId())) {
			DST_AGENT_SWITCH.removeClient(DST_CLIENT);
			DST_AGENT_SWITCH = null;
		}
		
		/*
		 *  In the future, we'll do something like this...
		for (SOSSwitch sosSw : SOS_SWITCHES) {
			if (sosSw.getSwitch() == sw) {
				// Do something to remove all Agent and Client associations to this switch instance
				// ...and unfortunately terminate any SOS connections as well...

				// Now remove the switch from our list
				SOS_SWITCHES.remove(sosSw);
			}
		}
		*/
	}

	@Override
	public void switchPortChanged(Long switchId) {

	}
}