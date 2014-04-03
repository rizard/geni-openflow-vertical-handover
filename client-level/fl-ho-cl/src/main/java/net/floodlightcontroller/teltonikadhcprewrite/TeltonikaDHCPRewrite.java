/*package net.floodlightcontroller.teltonikadhcprewrite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.util.MACAddress;

public class TeltonikaDHCPRewrite implements IOFMessageListener, IFloodlightModule  {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	/** START CONFIG FILE VARIABLES *

	// These variables are set using the floodlightdefault.properties file
	// Refer to startup() for a list of the expected names in the config file

	private static byte[] MY_DHCP_SERVER_IP; // Same as CONTROLLER_IP but in byte[] form
	private static byte[] MY_SUBNET_MASK;
	private static byte[] MY_BROADCAST_IP;
	private static byte[] MY_IP_ADDRESS;
	private static byte[] MY_ROUTER_IP = null;
	
	/** END CONFIG FILE VARIABLES *

	/**
	 * DHCP messages are either:
	 *		REQUEST (client --0x01--> server)
	 *		or REPLY (server --0x02--> client)
	 
	public static byte DHCP_OPCODE_REQUEST = intToBytes(1)[0];
	public static byte DHCP_OPCODE_REPLY = intToBytes(2)[0];

	/**
	 * DHCP REQUEST messages are either of type:
	 *		DISCOVER (0x01)
	 *		REQUEST (0x03)
	 * 		DECLINE (0x04)
	 *		RELEASE (0x07)
	 *		or INFORM (0x08)
	 * DHCP REPLY messages are either of type:
	 *		OFFER (0x02)
	 *		ACK (0x05)
	 *		or NACK (0x06)
	 **
	public static byte[] DHCP_MSG_TYPE_DISCOVER = intToBytesSizeOne(1);
	public static byte[] DHCP_MSG_TYPE_OFFER = intToBytesSizeOne(2);
	public static byte[] DHCP_MSG_TYPE_REQUEST = intToBytesSizeOne(3);
	public static byte[] DHCP_MSG_TYPE_DECLINE = intToBytesSizeOne(4);
	public static byte[] DHCP_MSG_TYPE_ACK = intToBytesSizeOne(5);
	public static byte[] DHCP_MSG_TYPE_NACK = intToBytesSizeOne(6);
	public static byte[] DHCP_MSG_TYPE_RELEASE = intToBytesSizeOne(7);
	public static byte[] DHCP_MSG_TYPE_INFORM = intToBytesSizeOne(8);

	/**
	 * DHCP messages contain options requested by the client and
	 * provided by the server. The options requested by the client are
	 * provided in a list (option 0x37 below) and the server elects to
	 * answer some or all of these options and may provide additional
	 * options as necessary for the DHCP client to obtain a lease.
	 *		OPTION NAME			HEX		DEC 		
	 * 		Subnet Mask			0x01	1
	 * 		Router IP			0x03	3
	 * 		DNS Server IP		0x06	6
	 * 		Domain Name			0x0F	15
	 * 		IP Forwarding		0x13	19
	 * 		Broadcast IP		0x1C	28
	 * 		NTP Server IP		0x2A	42
	 * 		NetBios Name IP		0x2C	44
	 * 		NetBios DDS IP		0x2D	45
	 * 		NetBios Node Type	0x2E	46
	 * 		NetBios Scope ID	0x2F	47
	 * 		Requested IP		0x32	50
	 * 		Lease Time (s)		0x33	51
	 * 		Msg Type (above)	0x35	53
	 * 		DHCP Server IP		0x36	54
	 * 		Option List (this)	0x37	55
	 * 		Renewal Time (s)	0x3A	58
	 * 		Rebind Time (s)		0x3B	59
	 * 		End Option List		0xFF	255
	 **
	public static byte DHCP_REQ_PARAM_OPTION_CODE_SN = intToBytes(1)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_ROUTER = intToBytes(3)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DNS = intToBytes(6)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DN = intToBytes(15)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING = intToBytes(19)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP = intToBytes(28)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NTP_IP = intToBytes(42)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_NAME_IP = intToBytes(44)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_DDS_IP = intToBytes(45)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_NODE_TYPE = intToBytes(46)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_NET_BIOS_SCOPE_ID = intToBytes(47)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP = intToBytes(50)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME = intToBytes(51)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE = intToBytes(53)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER = intToBytes(54)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS = intToBytes(55)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME = intToBytes(58)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME = intToBytes(59)[0];
	public static byte DHCP_REQ_PARAM_OPTION_CODE_END = intToBytes(255)[0];

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
		log = LoggerFactory.getLogger(TeltonikaDHCPRewrite.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		// Read our config options for the DHCP DHCPServer
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			MY_SUBNET_MASK = IPv4.toIPv4AddressBytes(configOptions.get("my-subnet-mask"));
			MY_IP_ADDRESS = IPv4.toIPv4AddressBytes(configOptions.get("my-ip-address"));
			MY_BROADCAST_IP = IPv4.toIPv4AddressBytes(configOptions.get("my-broadcast-address"));
			MY_ROUTER_IP = IPv4.toIPv4AddressBytes(configOptions.get("my-router"));

		} catch(IllegalArgumentException ex) {
			log.error("Incorrect DHCP configuration options", ex);
			throw ex;
		} catch(NullPointerException ex) {
			log.error("Incorrect DHCP configuration options", ex);
			throw ex;
		}
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
		return TeltonikaDHCPRewrite.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// We will rely on forwarding to forward out any DHCP packets that are not
		// destined for our DHCP server. This is to allow an environment where
		// multiple DHCP servers operate cooperatively
		if (type == OFType.PACKET_IN && name.equals(Forwarding.class.getSimpleName())) {
			return true;
		} else {
			return false;
		}
	}

	public static byte[] intToBytes(int integer) {
		byte[] bytes = new byte[4];
		bytes[3] = (byte) (integer >> 24);
		bytes[2] = (byte) (integer >> 16);
		bytes[1] = (byte) (integer >> 8);
		bytes[0] = (byte) (integer);
		return bytes;
	}

	public static byte[] intToBytesSizeOne(int integer) {
		byte[] bytes = new byte[1];
		bytes[0] = (byte) (integer);
		return bytes;
	}

	public void sendDHCPOffer(IOFSwitch sw, short inPort, byte[] chaddr, int dstIPAddr, 
			int yiaddr, int giaddr, int xid, ArrayList<Byte> requestOrder) {
		// Compose DHCP OFFER
		/** (2) DHCP Offer
		 * -- UDP src port = 67
		 * -- UDP dst port = 68
		 * -- IP src addr = DHCP DHCPServer's IP
		 * -- IP dst addr = 255.255.255.255
		 * -- Opcode = 0x02
		 * -- XID = transactionX
		 * -- ciaddr = blank
		 * -- yiaddr = offer IP
		 * -- siaddr = DHCP DHCPServer IP
		 * -- giaddr = blank
		 * -- chaddr = Client's MAC
		 * -- Options:
		 * --	Option 53 = DHCP Offer
		 * --	Option 1 = SN Mask IP
		 * --	Option 3 = Router IP
		 * --	Option 51 = Lease time (s)
		 * --	Option 54 = DHCP DHCPServer IP
		 * --	Option 6 = DNS servers
		 **
		OFPacketOut DHCPOfferPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		DHCPOfferPacket.setBufferId(-1);

		Ethernet ethDHCPOffer = new Ethernet();
		ethDHCPOffer.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPOffer.setDestinationMACAddress(chaddr);
		ethDHCPOffer.setEtherType(Ethernet.TYPE_IPv4);

		IPv4 ipv4DHCPOffer = new IPv4();
		if (dstIPAddr == 0) {
			ipv4DHCPOffer.setDestinationAddress(BROADCAST_IP);
		} else { // Client has IP and dhcpc must have crashed
			ipv4DHCPOffer.setDestinationAddress(dstIPAddr);
		}
		ipv4DHCPOffer.setSourceAddress(CONTROLLER_IP);
		ipv4DHCPOffer.setProtocol(IPv4.PROTOCOL_UDP);
		ipv4DHCPOffer.setTtl((byte) 64);

		UDP udpDHCPOffer = new UDP();
		udpDHCPOffer.setDestinationPort(UDP.DHCP_CLIENT_PORT);
		udpDHCPOffer.setSourcePort(UDP.DHCP_SERVER_PORT);

		DHCP dhcpDHCPOffer = new DHCP();
		dhcpDHCPOffer.setOpCode(DHCP_OPCODE_REPLY);
		dhcpDHCPOffer.setHardwareType((byte) 1);
		dhcpDHCPOffer.setHardwareAddressLength((byte) 6);
		dhcpDHCPOffer.setHops((byte) 0);
		dhcpDHCPOffer.setTransactionId(xid);
		dhcpDHCPOffer.setSeconds((short) 0);
		dhcpDHCPOffer.setFlags((short) 0);
		dhcpDHCPOffer.setClientIPAddress(UNASSIGNED_IP);
		dhcpDHCPOffer.setYourIPAddress(yiaddr);
		dhcpDHCPOffer.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPOffer.setGatewayIPAddress(giaddr);
		dhcpDHCPOffer.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpOfferOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_OFFER);
		newOption.setLength((byte) 1);
		dhcpOfferOptions.add(newOption);

		for (Byte specificRequest : requestOrder) {
			if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_SN);
				newOption.setData(DHCP_SERVER_SUBNET_MASK);
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
				newOption.setData(DHCP_SERVER_ROUTER_IP);
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DN);
				newOption.setData(DHCP_SERVER_DN);
				newOption.setLength((byte) DHCP_SERVER_DN.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DNS);
				newOption.setData(DHCP_SERVER_DNS_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_DNS_IP_LIST.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
				newOption.setData(DHCP_SERVER_BROADCAST_IP);
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
				newOption.setData(DHCP_SERVER_DHCP_SERVER_IP);
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				newOption.setData(DHCP_SERVER_IP_FORWARDING);
				newOption.setLength((byte) 1);
				dhcpOfferOptions.add(newOption);
			} else {
				//log.debug("Setting specific request for OFFER failed");
			}
		}

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_END);
		newOption.setLength((byte) 0);
		dhcpOfferOptions.add(newOption);

		dhcpDHCPOffer.setOptions(dhcpOfferOptions);

		ethDHCPOffer.setPayload(ipv4DHCPOffer.setPayload(udpDHCPOffer.setPayload(dhcpDHCPOffer)));

		short packetOutLength = (short) OFPacketOut.MINIMUM_LENGTH;

		DHCPOfferPacket.setInPort(OFPort.OFPP_NONE.getValue());

		DHCPOfferPacket.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		packetOutLength = (short) (packetOutLength + OFActionOutput.MINIMUM_LENGTH);

		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(new OFActionOutput(inPort, (short) 0));
		DHCPOfferPacket.setActions(actions);

		DHCPOfferPacket.setPacketData(ethDHCPOffer.serialize());
		packetOutLength = (short) (packetOutLength + ethDHCPOffer.serialize().length);

		DHCPOfferPacket.setLength(packetOutLength);

		log.debug("Sending DHCP OFFER");
		try {
			sw.write(DHCPOfferPacket, null);
		} catch (IOException e) {
			System.out.println("Failed to write {} to switch {}: {}");
		}
	}
	
	public void sendDHCPAck(IOFSwitch sw, short inPort, byte[] chaddr, int dstIPAddr, 
			int yiaddr, int giaddr, int xid, ArrayList<Byte> requestOrder) {
		/** (4) DHCP ACK
		 * -- UDP src port = 67
		 * -- UDP dst port = 68
		 * -- IP src addr = DHCP DHCPServer's IP
		 * -- IP dst addr = 255.255.255.255
		 * -- Opcode = 0x02
		 * -- XID = transactionX
		 * -- ciaddr = blank
		 * -- yiaddr = offer IP
		 * -- siaddr = DHCP DHCPServer IP
		 * -- giaddr = blank
		 * -- chaddr = Client's MAC
		 * -- Options:
		 * --	Option 53 = DHCP ACK
		 * --	Option 1 = SN Mask IP
		 * --	Option 3 = Router IP
		 * --	Option 51 = Lease time (s)
		 * --	Option 54 = DHCP DHCPServer IP
		 * --	Option 6 = DNS servers
		 **
		OFPacketOut DHCPACKPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		DHCPACKPacket.setBufferId(-1);

		Ethernet ethDHCPAck = new Ethernet();
		ethDHCPAck.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPAck.setDestinationMACAddress(chaddr);
		ethDHCPAck.setEtherType(Ethernet.TYPE_IPv4);

		IPv4 ipv4DHCPAck = new IPv4();
		if (dstIPAddr == 0) {
			ipv4DHCPAck.setDestinationAddress(BROADCAST_IP);
		} else { // Client has IP and dhclient must have crashed
			ipv4DHCPAck.setDestinationAddress(dstIPAddr);
		}
		ipv4DHCPAck.setSourceAddress(CONTROLLER_IP);
		ipv4DHCPAck.setProtocol(IPv4.PROTOCOL_UDP);
		ipv4DHCPAck.setTtl((byte) 64);

		UDP udpDHCPAck = new UDP();
		udpDHCPAck.setDestinationPort(UDP.DHCP_CLIENT_PORT);
		udpDHCPAck.setSourcePort(UDP.DHCP_SERVER_PORT);

		DHCP dhcpDHCPAck = new DHCP();
		dhcpDHCPAck.setOpCode(DHCP_OPCODE_REPLY);
		dhcpDHCPAck.setHardwareType((byte) 1);
		dhcpDHCPAck.setHardwareAddressLength((byte) 6);
		dhcpDHCPAck.setHops((byte) 0);
		dhcpDHCPAck.setTransactionId(xid);
		dhcpDHCPAck.setSeconds((short) 0);
		dhcpDHCPAck.setFlags((short) 0);
		dhcpDHCPAck.setClientIPAddress(UNASSIGNED_IP);
		dhcpDHCPAck.setYourIPAddress(yiaddr);
		dhcpDHCPAck.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPAck.setGatewayIPAddress(giaddr);
		dhcpDHCPAck.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpAckOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_ACK);
		newOption.setLength((byte) 1);
		dhcpAckOptions.add(newOption);

		for (Byte specificRequest : requestOrder) {
			if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_SN);
				newOption.setData(DHCP_SERVER_SUBNET_MASK);
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
				newOption.setData(DHCP_SERVER_ROUTER_IP);
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DN);
				newOption.setData(DHCP_SERVER_DN);
				newOption.setLength((byte) DHCP_SERVER_DN.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DNS);
				newOption.setData(DHCP_SERVER_DNS_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_DNS_IP_LIST.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
				newOption.setData(DHCP_SERVER_BROADCAST_IP);
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
				newOption.setData(DHCP_SERVER_DHCP_SERVER_IP);
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				newOption.setData(DHCP_SERVER_IP_FORWARDING);
				newOption.setLength((byte) 1);
				dhcpAckOptions.add(newOption);
			}else {
				log.debug("Setting specific request for ACK failed");
			}
		}

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_END);
		newOption.setLength((byte) 0);
		dhcpAckOptions.add(newOption);

		dhcpDHCPAck.setOptions(dhcpAckOptions);

		ethDHCPAck.setPayload(ipv4DHCPAck.setPayload(udpDHCPAck.setPayload(dhcpDHCPAck)));

		short packetOutLength = (short) OFPacketOut.MINIMUM_LENGTH;

		DHCPACKPacket.setInPort(OFPort.OFPP_NONE.getValue());

		DHCPACKPacket.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		packetOutLength = (short) (packetOutLength + OFActionOutput.MINIMUM_LENGTH);

		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(new OFActionOutput(inPort, (short) 0));
		DHCPACKPacket.setActions(actions);

		DHCPACKPacket.setPacketData(ethDHCPAck.serialize());
		packetOutLength = (short) (packetOutLength + ethDHCPAck.serialize().length);

		DHCPACKPacket.setLength(packetOutLength);
		log.debug("Sending DHCP ACK");
		try {
			sw.write(DHCPACKPacket, null);
		} catch (IOException e) {
			System.out.println("Failed to write {} to switch {}: {}");
		}
	}

	public ArrayList<Byte> getRequestedParameters(DHCP DHCPPayload, boolean isInform) {
		ArrayList<Byte> requestOrder = new ArrayList<Byte>();
		byte[] requests = DHCPPayload.getOption(DHCPOptionCode.OptionCode_RequestedParameters).getData();
		boolean requestedLeaseTime = false;
		boolean requestedRebindTime = false;
		boolean requestedRenewTime = false;
		for (byte specificRequest : requests) {
			if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_SN) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_SN);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_ROUTER) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_ROUTER);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DN) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DN);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DNS) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DNS);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				requestedLeaseTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_BROADCAST_IP);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				requestedRebindTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				requestedRenewTime = true;
			} else if (specificRequest == DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_IP_FORWARDING);
				log.debug("requested IP FORWARDING");
			} else {
				//log.debug("Requested option 0x" + Byte.toString(specificRequest) + " not available");
			}
		}

		// We need to add these in regardless if the request list includes them
		if (!isInform) {
			if (!requestedLeaseTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				//log.debug("added option LEASE TIME");
			}
			if (!requestedRenewTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				//log.debug("added option RENEWAL TIME");
			}
			if (!requestedRebindTime) {
				requestOrder.add(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				//log.debug("added option REBIND TIME");
			}
		}
		return requestOrder;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		OFPacketIn pi = (OFPacketIn) msg;

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
			//log.debug("Got IPv4 Packet");
			IPv4 IPv4Payload = (IPv4) eth.getPayload();
			int IPv4SrcAddr = IPv4Payload.getSourceAddress();

			if (IPv4Payload.getProtocol() == IPv4.PROTOCOL_UDP) {
				//log.debug("Got UDP Packet");
				UDP UDPPayload = (UDP) IPv4Payload.getPayload();

				if ((UDPPayload.getDestinationPort() == UDP.DHCP_SERVER_PORT 
						|| UDPPayload.getDestinationPort() == UDP.DHCP_CLIENT_PORT)
						&& (UDPPayload.getSourcePort() == UDP.DHCP_SERVER_PORT
						|| UDPPayload.getSourcePort() == UDP.DHCP_CLIENT_PORT))
				{
					//log.debug("Got DHCP Packet");
					// This is a DHCP packet that we need to process
					DHCP DHCPPayload = (DHCP) UDPPayload.getPayload();
					short inPort = pi.getInPort();

					/* DHCP/IPv4 Header Information *
					int xid = 0;
					int yiaddr = 0;
					int giaddr = 0;
					byte[] chaddr = null;
					byte[] desiredIPAddr = null;
					ArrayList<Byte> requestOrder = new ArrayList<Byte>();
					if (DHCPPayload.getOpCode() == DHCP_OPCODE_REQUEST) {
						// Do nothing... we want to modify the replies only
					} // END IF DHCP OPCODE REQUEST 
					else if (DHCPPayload.getOpCode() == DHCP_OPCODE_REPLY) {
						/**  * (1) DHCP Discover
						 * -- UDP src port = 68
						 * -- UDP dst port = 67
						 * -- IP src addr = 0.0.0.0
						 * -- IP dst addr = 255.255.255.255
						 * -- Opcode = 0x01
						 * -- XID = transactionX
						 * -- All addresses blank:
						 * --	ciaddr (client IP)
						 * --	yiaddr (your IP)
						 * --	siaddr (DHCPServer IP)
						 * --	giaddr (GW IP)
						 * -- chaddr = Client's MAC
						 * -- Options:
						 * --	Option 53 = DHCP Discover
						 * --	Option 50 = possible IP request
						 * --	Option 55 = parameter request list
						 * --		(1) SN Mask
						 * --		(3) Router
						 * --		(15) Domain Name
						 * --		(6) DNS
						 **
						if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_OFFER)) {
							log.debug("DHCP OFFER Received");
							yiaddr = DHCPPayload.getYourIPAddress();
							// Will have GW IP if a relay agent was used
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = Arrays.copyOf(DHCPPayload.getClientHardwareAddress(), DHCPPayload.getClientHardwareAddress().length);
							List<DHCPOption> options = DHCPPayload.getOptions();
							for (DHCPOption option : options) {
								if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP) {
									option.setData(data) = Arrays.copyOf(option.getData(), option.getData().length);
									log.debug("Got requested IP");
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS) {
									log.debug("Got requested param list");
									requestOrder = getRequestedParameters(DHCPPayload, false); 		
								}
							}
							sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);
						} else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_ACK)) {
							log.debug("DHCP ACK Received");
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							// Will have GW IP if a relay agent was used
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = Arrays.copyOf(DHCPPayload.getClientHardwareAddress(), DHCPPayload.getClientHardwareAddress().length);
							List<DHCPOption> options = DHCPPayload.getOptions();
							for (DHCPOption option : options) {
								if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP) {
									desiredIPAddr = Arrays.copyOf(option.getData(), option.getData().length);
									log.debug("Got requested IP");
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS) {
									log.debug("Got requested param list");
									requestOrder = getRequestedParameters(DHCPPayload, false); 		
								}
							}
							sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);
						}
					} else {
						log.debug("Got DHCP packet, but not a known DHCP packet opcode");
					}
				} // END IF DHCP packet
			} // END IF UDP packet
		} // END IF IPv4 packet
		return Command.CONTINUE;
	} // END of receive(pkt)
}*/