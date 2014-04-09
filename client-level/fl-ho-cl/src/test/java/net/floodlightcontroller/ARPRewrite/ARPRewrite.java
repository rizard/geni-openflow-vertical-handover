package net.floodlightcontroller.ARPRewrite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ARPRewrite extends Forwarding implements
IFloodlightModule  {
	protected static Logger log = LoggerFactory.getLogger(ARPRewrite.class);
	// proxyArp MAC address - can replace with other values in future
	public static String TAP_MAC_ADDRESS = "12:51:16:90:8f:ee"; // Tap
	public static String IFACE1_MAC_ADDRESS = "00:1d:e1:3b:48:1d"; // WiMAX
	public static String IFACE2_MAC_ADDRESS = "00:23:15:81:8b:f8"; // WiFi
	public static String IFACE3_MAC_ADDRESS = "00:23:15:81:8b:f8"; // WiFi #2 (Not Used, GEC18)
	public static String IFACE4_MAC_ADDRESS = "00:1d:e1:3b:48:1d"; // WiMAX #2 (Not Used, GEC18)
	public static String BROADCAST_MAC = "ff:ff:ff:ff:ff:ff";
	
	public static String IFACE1_DPID;
	public static String IFACE2_DPID;
	public static String IFACE3_DPID; 
	public static String IFACE4_DPID;
	public static String TAP_DPID;

	protected MACAddress TAP_MAC;
	protected MACAddress IFACE1_MAC;
	protected MACAddress IFACE2_MAC;
	protected MACAddress IFACE3_MAC;
	protected MACAddress IFACE4_MAC;
	protected MACAddress BCAST_MAC;
	
	protected IStaticFlowEntryPusherService sfep;
	protected IFloodlightProviderService floodlightProvider;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStorageSourceService.class);
        l.add(IStaticFlowEntryPusherService.class);
        return l;
    }
    
    @Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
    	
    	sfep = context.getServiceImpl(IStaticFlowEntryPusherService.class);
    	floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    	
		/* read our config options */
		Map<String, String> configOptions = context.getConfigParams(this);

		TAP_MAC_ADDRESS = configOptions.get("tap-mac-address");
		IFACE1_MAC_ADDRESS = configOptions.get("wimax-mac-address");
		IFACE2_MAC_ADDRESS = configOptions.get("wifi-mac-address");
		IFACE3_MAC_ADDRESS = configOptions.get("iface3-mac-address");
		IFACE4_MAC_ADDRESS = configOptions.get("iface4-mac-address");

		TAP_DPID = configOptions.get("tap-dpid");
		IFACE1_DPID = configOptions.get("wimax-dpid");
		IFACE2_DPID = configOptions.get("wifi-dpid");
		IFACE3_DPID = configOptions.get("iface3-dpid");
		IFACE4_DPID = configOptions.get("iface4-dpid");

		TAP_MAC = MACAddress.valueOf(TAP_MAC_ADDRESS);
		IFACE1_MAC = MACAddress.valueOf(IFACE1_MAC_ADDRESS);
		IFACE2_MAC = MACAddress.valueOf(IFACE2_MAC_ADDRESS);
		IFACE3_MAC = MACAddress.valueOf(IFACE3_MAC_ADDRESS);
		IFACE4_MAC = MACAddress.valueOf(IFACE4_MAC_ADDRESS);
		BCAST_MAC = MACAddress.valueOf(BROADCAST_MAC);
 
		super.init(context);
	}
    
    
    @Override
    public void startUp(FloodlightModuleContext context) {
    	floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

	@Override
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi,
			IRoutingDecision decision, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		if (eth.getEtherType() == Ethernet.TYPE_ARP) {

			// retrieve arp to determine addresses
			ARP arpPayload = (ARP) eth.getPayload();

			// (1) Get flows from switch
			// (2) Find flow with matching ingress port
			// (3) Use matching flow and extract output port
			// (4) Use switch DPID and use correct (hard-coded) MAC of physical interface on that switch
			// This is only for ARP. DHCP is ethertype 0x800, which will work with existing flows.

			// (1) Get flows
			Map<String, OFFlowMod> flows;
			//long switchIDAsLong = sw.getId();
			//String switchIDAsString = Long.toString(switchIDAsLong);
			//flows = sfep.getFlows("00:00:00:00:00:00:00:0".concat(switchIDAsString)); // This might not convert correctly
			flows = sfep.getFlows(sw.getStringId()); // This might not convert correctly
			Collection<OFFlowMod> flowsAsCollection;
			if (flows != null) {
				flowsAsCollection = flows.values();
			} else {
				log.debug("No flows on switch " + sw.getStringId());
				return Command.CONTINUE;
			}
			short inPort = pi.getInPort();
			short outPort = 0;
			log.debug("Packet from switch " + sw.getStringId() + " on port " + inPort);


			// (2) and (3) Get output port from matching ingress port
			if (!flows.isEmpty()) {
				for (OFFlowMod myFlow : flowsAsCollection) {
					if (myFlow.getMatch().getInputPort() == inPort) {
						outPort = myFlow.getOutPort();
						log.debug("Got (inPort, outPort) pair (" + inPort + ", " + outPort + ")");
						break;
					}
				}
			} else {
				log.info("No flows returned for DPID " + sw.getStringId());
				return Command.CONTINUE;
			}
			
			if (outPort == 0) {
				log.debug("No matching flow found");
				return Command.CONTINUE; 
			}

			// (4) Determine MAC address to rewrite
			// ingress on tap

			MACAddress targetMAC;
			MACAddress sourceMAC;
			MACAddress destinationMAC;
			boolean isIngress = false;
			boolean isRequest = false;
			short arpOpCode = arpPayload.getOpCode();
			if (arpOpCode == ARP.OP_REQUEST) {
				log.info("ARP packet is a request");
				destinationMAC = BCAST_MAC;
				isRequest = true;
			} else if (arpOpCode == ARP.OP_REPLY) {
				log.info("ARP packet is a reply");
			} else {
				log.info("ARP packet was neither request nor reply");
				return Command.CONTINUE;
			}
			if (sw.getStringId() == TAP_DPID) {
				if (outPort == 1 || outPort == 65534 || outPort == -1) {
					targetMAC = TAP_MAC;
					sourceMAC = null;
					isIngress = true;
				} else {
					targetMAC = null;
					sourceMAC = TAP_MAC;
				}
			} else if (sw.getStringId() == IFACE1_DPID) {
				if (outPort == 1 || outPort == 65534 || outPort == -1) {
					targetMAC = null;
					sourceMAC = IFACE1_MAC;
				} else {
					targetMAC = TAP_MAC;
					sourceMAC = null;
					isIngress = true;
				}
			} else if (sw.getStringId() == IFACE2_DPID) {
				if (outPort == 1 || outPort == 65534 || outPort == -1) {
					targetMAC = null;
					sourceMAC = IFACE2_MAC;
				} else {
					targetMAC = TAP_MAC;
					sourceMAC = null;
					isIngress = true;
				}
			} else if (sw.getStringId() == IFACE3_DPID) {
				if (outPort == 1 || outPort == 65534 || outPort == -1) {
					targetMAC = null;
					sourceMAC = IFACE3_MAC;
				} else {
					targetMAC = TAP_MAC;
					sourceMAC = null;
					isIngress = true;
				}
			} else if (sw.getStringId() == IFACE4_DPID) {
				if (outPort == 1 || outPort == 65534 || outPort == -1) {
					targetMAC = null;
					sourceMAC = IFACE4_MAC;
				} else {
					targetMAC = TAP_MAC;
					sourceMAC = null;
					isIngress = true;
				}
			} else {
				sourceMAC = null;
				targetMAC = null;
				log.debug("Did find a matching DPID");
			}

			// 4 Cases: Only rewrite on non-TAP switch. The tap switch will have 4 flows for 0x800 and 0x806.
			// All rewrites source and destination will occur on the OVS switch at the physical interface.
			// (1) Ingress Packet, ARP Request -- rewrite nothing (need to find MAC aka request)
			// (2) Ingress Packet, ARP Reply -- rewrite destination MAC to tap
			// (3) Outbound Packet, ARP Request -- rewrite source MAC to physical (need to be able to respond and send on IFACE)
			// (4) Outbound Packet, ARP Reply -- rewrite source MAC to physical (need to be able to send on IFACE)
			if (isRequest && sw.getStringId() == TAP_DPID && isIngress) {
				log.debug("Sending request out with SRC " + sourceMAC.toString() + " DST " + MACAddress.valueOf(arpPayload.getTargetHardwareAddress())
						+ " from port " + inPort + " to port " + outPort);
				destinationMAC = BCAST_MAC;
				sendARP(sw, pi, cntx, inPort, (short)65534, arpOpCode, 
						arpPayload.getSenderHardwareAddress(), arpPayload.getTargetHardwareAddress(), destinationMAC.toBytes());
			} else if (!isRequest && sw.getStringId() == TAP_DPID && isIngress) {
				log.debug("Sending reply out with SRC " + sourceMAC.toString() + " DST " + MACAddress.valueOf(arpPayload.getTargetHardwareAddress())
						+ " from port " + inPort + " to port " + outPort);
				destinationMAC = targetMAC;
				sendARP(sw, pi, cntx, inPort, (short)65534, arpOpCode, 
						arpPayload.getSenderHardwareAddress(), targetMAC.toBytes(), destinationMAC.toBytes());
			} else if (isRequest && sw.getStringId() != TAP_DPID && !isIngress) {
				log.debug("Sending request out with SRC " + sourceMAC.toString() + " DST " + MACAddress.valueOf(arpPayload.getTargetHardwareAddress())
						+ " from port " + inPort + " to port " + outPort);
				destinationMAC = BCAST_MAC;
				// removed (-) from outPort for testing
				sendARP(sw, pi, cntx, inPort, (short)-outPort, arpOpCode, 
						sourceMAC.toBytes(), arpPayload.getTargetHardwareAddress(), destinationMAC.toBytes());
			} else if (!isRequest && sw.getStringId() != TAP_DPID && !isIngress) {
				log.debug("Sending reply out with SRC " + sourceMAC.toString() + " DST " + MACAddress.valueOf(arpPayload.getTargetHardwareAddress())
						+ " from port " + inPort + " to port " + outPort);
				destinationMAC = targetMAC;
				sendARP(sw, pi, cntx, inPort, (short)-outPort, arpOpCode, 
						sourceMAC.toBytes(), arpPayload.getTargetHardwareAddress(), destinationMAC.toBytes());
			} else {
				log.debug("Determining how to rewrite ARP packet failed!");
			}
		} else {
			// Do nothing
		}

		return Command.CONTINUE;
	}

	protected void sendARP(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, short inPort, short outPort, 
			short arpOpCode , byte[] sourceMACAddress, byte[] targetMACAddress, byte[] destinationMACAddress) {

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		// retrieve original ARP
		ARP arpPayload = (ARP) eth.getPayload();
		log.debug("ARP to " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arpPayload.getTargetProtocolAddress())));

		// Modify original ARP packet
		IPacket newARPPacket = new Ethernet()
		.setSourceMACAddress(sourceMACAddress)
		.setDestinationMACAddress(destinationMACAddress)
		.setEtherType(Ethernet.TYPE_ARP)
		.setVlanID(eth.getVlanID())
		.setPriorityCode(eth.getPriorityCode())
		.setPayload(
				new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4)
				.setOpCode(arpOpCode)
				.setSenderHardwareAddress(sourceMACAddress)
				.setSenderProtocolAddress(
						arpPayload.getSenderProtocolAddress())
						.setTargetHardwareAddress(
								targetMACAddress)
								.setTargetProtocolAddress(
										arpPayload.getTargetProtocolAddress()));

		// push ARP out
		pushPacket(newARPPacket, sw, OFPacketOut.BUFFER_ID_NONE, inPort, outPort, cntx, true);
		log.debug("ARP packet pushed out PORT " + outPort + " on SWITCH " + sw.getStringId() + " with SRC " + MACAddress.valueOf(sourceMACAddress)
				+ " and TGT " + MACAddress.valueOf(targetMACAddress) + " and DST " + MACAddress.valueOf(destinationMACAddress));
		return;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

}
