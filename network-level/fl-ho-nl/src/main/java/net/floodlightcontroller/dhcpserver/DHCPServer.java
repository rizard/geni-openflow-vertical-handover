package net.floodlightcontroller.dhcpserver;

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
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;

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
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.DHCP.DHCPOptionCode;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.DHCP;
import net.floodlightcontroller.packet.DHCPOption;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.util.MACAddress;

/**
 * SDN DHCP Server
 * @author Ryan Izard, rizard@g.clemson.edu
 * 
 * 
 * The Floodlight Module implementing a DHCP DHCPServer.
 * This module uses {@code DHCPPool} to manage DHCP leases.
 * It intercepts any DHCP/BOOTP requests from connected hosts and
 * handles the replies. The configuration file:
 * 
 * 		floodlight/src/main/resources/floodlightdefault.properties
 * 
 * contains the DHCP options and parameters that can be set. To allow
 * all DHCP request messages to be sent to the controller (Floodlight),
 * the DHCPSwitchFlowSetter module (in this same package) and the
 * Forwarding module (loaded by default) should also be loaded in
 * Floodlight. When the first DHCP request is received on a particular
 * port of an OpenFlow switch, the request will by default be sent to
 * the control plane to the controller for processing. The DHCPServer
 * module will intercept the message before it makes it to the Forwarding
 * module and process the packet. Now, because we don't want to hog all
 * the DHCP messages (in case there is another module that is using them)
 * we forward the packets down to other modules using Command.CONTINUE.
 * As a side effect, the forwarding module will insert flows in the OF
 * switch for our DHCP traffic even though we've already processed it.
 * In order to allow all future DHCP messages from that same port to be
 * sent to the controller (and not follow the Forwarding module's flows),
 * we need to proactively insert flows for all DHCP client traffic on
 * UDP port 67 to the controller. These flows will allow all DHCP traffic
 * to be intercepted on that same port and sent to the DHCP server running
 * on the Floodlight controller.
 * 
 * Currently, this DHCP server only supports a single subnet; however,
 * work is ongoing to use connected OF switches and ports to allow
 * the user to configure multiple subnets. On a traditional DHCP server,
 * the machine is configured with different NICs, each with their own
 * statically-assigned IP address/subnet/mask. The DHCP server matches
 * the network information of each NIC with the DHCP server's configured
 * subnets and answers the requests accordingly. To mirror this behavior
 * on a OpenFlow network, we can differentiate between subnets based on a
 * device's attachment point. We can assign subnets for a device per
 * OpenFlow switch or per port per switch. This is the next step for
 * this implementations of a SDN DHCP server.
 *
 * I welcome any feedback or suggestions for improvement!
 * 
 * 
 */
public class DHCPServer implements IOFMessageListener, IFloodlightModule  {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	// The garbage collector service for the DHCP server
	// Handle expired leases by adding the IP back to the address pool
	private static ScheduledThreadPoolExecutor leasePoliceDispatcher;
	//private static ScheduledFuture<?> leasePoliceOfficer;
	private static Runnable leasePolicePatrol;

	// Contains the pool of IP addresses their bindings to MAC addresses
	// Tracks the lease status and duration of DHCP bindings
	private static volatile DHCPPool theDHCPPool;

	/** START CONFIG FILE VARIABLES **/

	// These variables are set using the floodlightdefault.properties file
	// Refer to startup() for a list of the expected names in the config file

	// The IP and MAC addresses of the controller/DHCP server
	private static byte[] CONTROLLER_MAC;
	private static int CONTROLLER_IP;

	private static byte[] DHCP_SERVER_DHCP_SERVER_IP; // Same as CONTROLLER_IP but in byte[] form
	private static byte[] DHCP_SERVER_SUBNET_MASK;
	private static byte[] DHCP_SERVER_BROADCAST_IP;
	private static byte[] DHCP_SERVER_IP_START;
	private static byte[] DHCP_SERVER_IP_STOP;
	private static int DHCP_SERVER_ADDRESS_SPACE_SIZE; // Computed in startUp()
	private static byte[] DHCP_SERVER_ROUTER_IP = null;
	private static byte[] DHCP_SERVER_ROUTER_MAC = null;
	private static byte[] DHCP_SERVER_NTP_IP_LIST = null;
	private static byte[] DHCP_SERVER_DNS_IP_LIST = null;
	private static byte[] DHCP_SERVER_DN = null;
	private static byte[] DHCP_SERVER_IP_FORWARDING = null;
	private static int DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS;
	private static int DHCP_SERVER_HOLD_LEASE_TIME_SECONDS;
	private static int DHCP_SERVER_REBIND_TIME_SECONDS; // Computed in startUp()
	private static int DHCP_SERVER_RENEWAL_TIME_SECONDS; // Computed in startUp()
	private static long DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS;

	private static int ROOT_NODE_ROOT_OVS_IP;
	private static String ROOT_NODE_WIMAX_OVS_DPID;
	private static int WIFI_NODE_IP;
	private static String WIFI_NODE_WIFI_OVS_DPID;
	private static String ROOT_NODE_ROOT_OVS_DPID;
	private static short ROOT_NODE_ROOT_OVS_WIFI_PATCH;
	private static short ROOT_NODE_ROOT_OVS_WIMAX_PATCH;


	/** END CONFIG FILE VARIABLES **/

	/**
	 * DHCP messages are either:
	 *		REQUEST (client --0x01--> server)
	 *		or REPLY (server --0x02--> client)
	 */
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
	 **/
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
	 * 
	 * NetBios options are not currently implemented in this server but can be added
	 * via the configuration file.
	 **/
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

	// Used for composing DHCP REPLY messages
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
		log = LoggerFactory.getLogger(DHCPServer.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

		// Read our config options for the DHCP DHCPServer
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			DHCP_SERVER_SUBNET_MASK = IPv4.toIPv4AddressBytes(configOptions.get("subnet-mask"));
			DHCP_SERVER_IP_START = IPv4.toIPv4AddressBytes(configOptions.get("lower-ip-range"));
			DHCP_SERVER_IP_STOP = IPv4.toIPv4AddressBytes(configOptions.get("upper-ip-range"));
			DHCP_SERVER_ADDRESS_SPACE_SIZE = IPv4.toIPv4Address(DHCP_SERVER_IP_STOP) - IPv4.toIPv4Address(DHCP_SERVER_IP_START) + 1;
			DHCP_SERVER_BROADCAST_IP = IPv4.toIPv4AddressBytes(configOptions.get("broadcast-address"));
			DHCP_SERVER_ROUTER_IP = IPv4.toIPv4AddressBytes(configOptions.get("router"));
			DHCP_SERVER_ROUTER_MAC = Ethernet.toMACAddress(configOptions.get("router-mac"));
			DHCP_SERVER_DN = configOptions.get("domain-name").getBytes();
			DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS = Integer.parseInt(configOptions.get("default-lease-time"));
			DHCP_SERVER_HOLD_LEASE_TIME_SECONDS = Integer.parseInt(configOptions.get("hold-lease-time"));
			DHCP_SERVER_RENEWAL_TIME_SECONDS = (int) (DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS / 2.0);
			DHCP_SERVER_REBIND_TIME_SECONDS = (int) (DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS * 0.875);
			DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS = Long.parseLong(configOptions.get("lease-gc-period"));
			DHCP_SERVER_IP_FORWARDING = intToBytesSizeOne(Integer.parseInt(configOptions.get("ip-forwarding")));

			CONTROLLER_MAC = Ethernet.toMACAddress(configOptions.get("controller-mac"));
			CONTROLLER_IP = IPv4.toIPv4Address(configOptions.get("controller-ip"));
			DHCP_SERVER_DHCP_SERVER_IP = IPv4.toIPv4AddressBytes(CONTROLLER_IP);

			ROOT_NODE_ROOT_OVS_DPID = configOptions.get("root-node-root-ovs-dpid");
			ROOT_NODE_WIMAX_OVS_DPID = configOptions.get("root-node-wimax-ovs-dpid");
			WIFI_NODE_WIFI_OVS_DPID = configOptions.get("wifi-node-wifi-ovs-dpid");

			ROOT_NODE_ROOT_OVS_WIFI_PATCH = Short.parseShort(configOptions.get("root-node-root-ovs-wifi-patch-port"));
			ROOT_NODE_ROOT_OVS_WIMAX_PATCH = Short.parseShort(configOptions.get("root-node-root-ovs-wimax-patch-port"));

			ROOT_NODE_ROOT_OVS_IP = IPv4.toIPv4Address(configOptions.get("root-node-root-ovs-ip"));
			WIFI_NODE_IP = IPv4.toIPv4Address(configOptions.get("wifi-node-ip"));

			// NetBios and other options can be added to this function here as needed in the future
		} catch(IllegalArgumentException ex) {
			log.error("Incorrect DHCP Server configuration options", ex);
			throw ex;
		} catch(NullPointerException ex) {
			log.error("Incorrect DHCP Server configuration options", ex);
			throw ex;
		}
		// Create our new DHCPPool object with the specific address size
		theDHCPPool = new DHCPPool(DHCP_SERVER_IP_START, DHCP_SERVER_ADDRESS_SPACE_SIZE, log);

		// Any addresses that need to be set as static/fixed can be permanently added to the pool with a set MAC
		String staticAddresses = configOptions.get("reserved-static-addresses");
		if (staticAddresses != null) {
			String[] macIpBindings = staticAddresses.split("\\s*;\\s*");
			String[] macIpSplit;
			int ipPos = -1;
			ArrayList<byte[]> macs = new ArrayList<byte[]>();
			for (int i = 0; i < macIpBindings.length; i++) {
				macIpSplit = macIpBindings[i].split("\\s*,\\s*");
				// Determine which elements are the MACs and which is the IP
				// i.e. which order have they been typed in in the config file?
				//log.info(macIpSplit[0] + " " + macIpSplit[1]);
				for (int j = 0; j < macIpSplit.length; j++) {
					if (macIpSplit[j].length() <= DHCPBinding.IP_ADDRESS_STRING_LENGTH_MAX && macIpSplit[j].length() >= DHCPBinding.IP_ADDRESS_STRING_LENGTH_MIN) {
						ipPos = j;
					} else {
						macs.add(Ethernet.toMACAddress(macIpSplit[j]));
					}
				}				
				if (ipPos != -1 && theDHCPPool.configureFixedIPLease(IPv4.toIPv4AddressBytes(macIpSplit[ipPos]), macs)) {
					String ip = theDHCPPool.getDHCPbindingFromIPv4(IPv4.toIPv4AddressBytes(macIpSplit[ipPos])).getIPv4AddresString();
					String mac = theDHCPPool.getDHCPbindingFromIPv4(IPv4.toIPv4AddressBytes(macIpSplit[ipPos])).getMACAddressesString();
					log.info("Configured fixed address of " + ip + " for device " + mac);
				} else {
					log.error("Could not configure fixed address " + macIpSplit[ipPos] + " for device!");
				}
				macs.clear();
			}
		}

		// The order of the DNS and NTP servers should be most reliable to least
		String dnses = configOptions.get("domain-name-servers");
		String ntps = configOptions.get("ntp-servers");

		// Separate the servers in the comma-delimited list
		// TODO If the list is null then we need to not include this information with the options request,
		// otherwise the client will get incorrect option information
		if (dnses != null) {
			DHCP_SERVER_DNS_IP_LIST = IPv4.toIPv4AddressBytes(dnses.split("\\s*,\\s*")[0].toString());
		}
		if (ntps != null) {
			DHCP_SERVER_NTP_IP_LIST = IPv4.toIPv4AddressBytes(ntps.split("\\s*,\\s*")[0].toString());
		}

		// Monitor bindings for expired leases and clean them up
		leasePoliceDispatcher = new ScheduledThreadPoolExecutor(1);
		leasePolicePatrol = new DHCPLeasePolice();
		/*leasePoliceOfficer = */
		leasePoliceDispatcher.scheduleAtFixedRate(leasePolicePatrol, 10, 
				DHCP_SERVER_LEASE_POLICE_PATROL_PERIOD_SECONDS, TimeUnit.SECONDS);
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
		return DHCPServer.class.getSimpleName();
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
		 **/
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
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_REBIND_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpOfferOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_RENEWAL_TIME_SECONDS));
				newOption.setLength((byte) 4);
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
		// Doing this as a test for the Teltonika
		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
		newOption.setData(DHCP_SERVER_DHCP_SERVER_IP);
		newOption.setLength((byte) 4);
		dhcpOfferOptions.add(newOption);

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
		 **/
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
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_LEASE_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_NTP_IP) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_NTP_IP);
				newOption.setData(DHCP_SERVER_NTP_IP_LIST);
				newOption.setLength((byte) DHCP_SERVER_NTP_IP_LIST.length);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_REBIND_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_REBIND_TIME_SECONDS));
				newOption.setLength((byte) 4);
				dhcpAckOptions.add(newOption);
			} else if (specificRequest.byteValue() == DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME) {
				newOption = new DHCPOption();
				newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_RENEWAL_TIME);
				newOption.setData(intToBytes(DHCP_SERVER_RENEWAL_TIME_SECONDS));
				newOption.setLength((byte) 4);
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

	public void sendDHCPNack(IOFSwitch sw, short inPort, byte[] chaddr, int giaddr, int xid) {
		OFPacketOut DHCPOfferPacket = (OFPacketOut)
				floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		DHCPOfferPacket.setBufferId(-1);

		Ethernet ethDHCPOffer = new Ethernet();
		ethDHCPOffer.setSourceMACAddress(CONTROLLER_MAC);
		ethDHCPOffer.setDestinationMACAddress(chaddr);
		ethDHCPOffer.setEtherType(Ethernet.TYPE_IPv4);

		IPv4 ipv4DHCPOffer = new IPv4();
		ipv4DHCPOffer.setDestinationAddress(BROADCAST_IP);
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
		dhcpDHCPOffer.setYourIPAddress(UNASSIGNED_IP);
		dhcpDHCPOffer.setServerIPAddress(CONTROLLER_IP);
		dhcpDHCPOffer.setGatewayIPAddress(giaddr);
		dhcpDHCPOffer.setClientHardwareAddress(chaddr);

		List<DHCPOption> dhcpOfferOptions = new ArrayList<DHCPOption>();
		DHCPOption newOption;

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_MSG_TYPE);
		newOption.setData(DHCP_MSG_TYPE_NACK);
		newOption.setLength((byte) 1);
		dhcpOfferOptions.add(newOption);

		newOption = new DHCPOption();
		newOption.setCode(DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER);
		newOption.setData(DHCP_SERVER_DHCP_SERVER_IP);
		newOption.setLength((byte) 4);
		dhcpOfferOptions.add(newOption);

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

		log.info("Sending DHCP NACK");
		try {
			sw.write(DHCPOfferPacket, null);
		} catch (IOException e) {
			System.out.println("Failed to write {} to switch {}: {}");
		}
	}

	/**
	 * Creates an ARP request frame, puts it into a packet out message and
	 * sends the packet out message to all switch ports (attachment point ports)
	 * that are not connected to other OpenFlow switches.
	 *
	 */
	/*protected void sendARPReqest(ARPRequest arpRequest) {
		// Create an ARP request frame
		IPacket arpReply = new Ethernet()
		.setSourceMACAddress(Ethernet.toByteArray(arpRequest.getSourceMACAddress()))
		.setDestinationMACAddress(BROADCAST_MAC)
		.setEtherType(Ethernet.TYPE_ARP)
		.setPayload(new ARP()
		.setHardwareType(ARP.HW_TYPE_ETHERNET)
		.setProtocolType(ARP.PROTO_TYPE_IP)
		.setOpCode(ARP.OP_REQUEST)
		.setHardwareAddressLength((byte)6)
		.setProtocolAddressLength((byte)4)
		.setSenderHardwareAddress(Ethernet.toByteArray(arpRequest.getSourceMACAddress()))
		.setSenderProtocolAddress(IPv4.toIPv4AddressBytes((int)arpRequest.getSourceIPAddress()))
		.setTargetHardwareAddress(Ethernet.toByteArray(arpRequest.getTargetMACAddress()))
		.setTargetProtocolAddress(IPv4.toIPv4AddressBytes((int)arpRequest.getTargetIPAddress()))
		.setPayload(new Data(new byte[] {0x01})));

		// Send ARP request to all external ports (i.e. attachment point ports).
		for (Map<long, IOFSwitch> switchId : floodlightProvider.getSwitches()) {
			IOFSwitch sw = floodlightProvider.getSwitch(switchId);
			for (OFPhysicalPort port : sw.getPorts()) {
				short portId = port.getPortNumber();
				if (switchId == arpRequest.getSwitchId() && portId == arpRequest.getInPort()) {
					continue;
				}
				if (topologyManager.isAttachmentPointPort(switchId, portId))
					this.sendPOMessage(arpReply, sw, portId);
				if (log.isDebugEnabled()) {
					log.debug("Send ARP request to " + HexString.toHexString(switchId) + " at port " + portId);
				}
			}
		}
	}*/
	/**
	 * Creates an ARP reply frame, puts it into a packet out message and
	 * sends the packet out message to the switch that received the ARP
	 * request message.
	 *
	 */
	protected void sendARPReply(byte[] srcMac, byte[] dstMac, byte[] srcIP, byte[] dstIP, short inPort, IOFSwitch sw) {
		// Create an ARP reply frame (from target (source) to source (destination)).
		IPacket arpReply = new Ethernet()
		.setSourceMACAddress(srcMac)
		.setDestinationMACAddress(dstMac)
		.setEtherType(Ethernet.TYPE_ARP)
		.setPayload(new ARP()
		.setHardwareType(ARP.HW_TYPE_ETHERNET)
		.setProtocolType(ARP.PROTO_TYPE_IP)
		.setOpCode(ARP.OP_REPLY)
		.setHardwareAddressLength((byte) 6)
		.setProtocolAddressLength((byte) 4)
		.setSenderHardwareAddress(srcMac)
		.setSenderProtocolAddress(srcIP)
		.setTargetHardwareAddress(dstMac)
		.setTargetProtocolAddress(dstIP)
		.setPayload(new Data(new byte[] {0x01})));
		// Send ARP reply.
		sendPOMessage(arpReply, sw, inPort);
		if (log.isDebugEnabled()) {
			log.debug("Send ARP reply to " + HexString.toHexString(sw.getId()) + " at port " + inPort);
		}
	}
	
	/**
	* Creates and sends an OpenFlow PacketOut message containing the packet
	* information to the switch. The packet included on the PacketOut message
	* is sent out at the given port.
	*
	* @param packet The packet that is sent out.
	* @param sw The switch the packet is sent out.
	* @param port The port the packet is sent out.
	*/
	protected void sendPOMessage(IPacket packet, IOFSwitch sw, short port) {	
		// Serialize and wrap in a packet out
		byte[] data = packet.serialize();
		OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
		po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		po.setInPort(OFPort.OFPP_NONE);

		// Set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(port, (short) 0));
		po.setActions(actions);
		po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

		// Set data
		po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + data.length);
		po.setPacketData(data);

		// Send message
		try {
			sw.write(po, null);
		} catch (IOException e) {
			log.error("Failure sending ARP out port {} on switch {}", new Object[] { port, sw.getStringId() }, e);
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

		if (!theDHCPPool.hasAvailableAddresses()) {
			log.info("DHCP Pool is full! Consider increasing the pool size.");
			return Command.CONTINUE;
		}

		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
				IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

		// Do Proxy ARP for all connected clients
		if (eth.getEtherType() == Ethernet.TYPE_ARP) {
			ARP ARPPayload = (ARP) eth.getPayload();
			byte[] foundMAC;
			if (ARPPayload.getOpCode() == ARP.OP_REQUEST) {
				// First, check to see if the MAC being requested is the GW/router
				if (Arrays.equals(DHCP_SERVER_ROUTER_IP, ARPPayload.getTargetProtocolAddress())) {
					log.debug("Got ARP REQUEST for GW/Router. Sending Reply...");
					sendARPReply(DHCP_SERVER_ROUTER_MAC, ARPPayload.getSenderHardwareAddress(), 
							ARPPayload.getTargetProtocolAddress(), ARPPayload.getSenderHardwareAddress(), 
							pi.getInPort(), sw);
				// Then, check to see if it's a known and active client of the DHCP server
				} else if ((foundMAC = theDHCPPool.lookupBoundMAC(ARPPayload.getSenderProtocolAddress())) != null) {
					log.debug("Got ARP REQUEST for local IP. Sending Reply...");
					sendARPReply(foundMAC, ARPPayload.getSenderHardwareAddress(), 
							ARPPayload.getTargetProtocolAddress(), ARPPayload.getSenderHardwareAddress(), 
							pi.getInPort(), sw);
				// Otherwise, we need to let another machine handle the reply, so rebroadcast the request
				} else {
					// in the future, rebroadcast the ARP request
				}
			} else if (ARPPayload.getOpCode() == ARP.OP_REPLY) {
				// do nothing for right now. After initial testing with the needed REQUEST handling, rebroadcast any REPLY
			}
		} else if (eth.getEtherType() == Ethernet.TYPE_IPv4) {
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

					/* DHCP/IPv4 Header Information */
					int xid = 0;
					int yiaddr = 0;
					int giaddr = 0;
					byte[] chaddr = null;
					byte[] desiredIPAddr = null;
					ArrayList<Byte> requestOrder = new ArrayList<Byte>();
					if (DHCPPayload.getOpCode() == DHCP_OPCODE_REQUEST) {
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
						 **/
						if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_DISCOVER)) {
							log.debug("DHCP DISCOVER Received");
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

							// Process DISCOVER message and prepare an OFFER with minimum-hold lease
							// A HOLD lease should be a small amount of time sufficient for the client to respond
							// with a REQUEST, at which point the ACK will set the least time to the DEFAULT
							synchronized (theDHCPPool) {
								if (!theDHCPPool.hasAvailableAddresses()) {
									log.info("DHCP Pool is full! Consider increasing the pool size.");
									log.info("Device with MAC " + MACAddress.valueOf(chaddr).toString() + " was not granted an IP lease");
									return Command.CONTINUE;
								}

								DHCPBinding lease = theDHCPPool.getStaticLease(chaddr);

								if (lease == null) {
									lease = theDHCPPool.getSpecificAvailableLease(desiredIPAddr, chaddr);
								}

								if (lease != null) {
									log.debug("Checking new lease with specific IP");
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_HOLD_LEASE_TIME_SECONDS);
									yiaddr = IPv4.toIPv4Address(lease.getIPv4AddressBytes());
									log.debug("Got new lease for " + IPv4.fromIPv4Address(yiaddr) + " " + MACAddress.valueOf(chaddr).toString());
								} else {
									log.debug("Checking new lease for any IP");
									lease = theDHCPPool.getAnyAvailableLease(chaddr);
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_HOLD_LEASE_TIME_SECONDS);
									yiaddr = IPv4.toIPv4Address(lease.getIPv4AddressBytes());
									log.debug("Got new lease for " + IPv4.fromIPv4Address(yiaddr) + " " + MACAddress.valueOf(chaddr).toString());
								}
							}

							sendDHCPOffer(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);
						} // END IF DISCOVER

						/** (3) DHCP Request
						 * -- UDP src port = 68
						 * -- UDP dst port = 67
						 * -- IP src addr = 0.0.0.0
						 * -- IP dst addr = 255.255.255.255
						 * -- Opcode = 0x01
						 * -- XID = transactionX
						 * -- ciaddr = blank
						 * -- yiaddr = blank
						 * -- siaddr = DHCP DHCPServer IP
						 * -- giaddr = GW IP
						 * -- chaddr = Client's MAC
						 * -- Options:
						 * --	Option 53 = DHCP Request
						 * --	Option 50 = IP requested (from offer)
						 * --	Option 54 = DHCP DHCPServer IP
						 **/
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_REQUEST)) {
							log.debug("DHCP REQUEST received");
							IPv4SrcAddr = IPv4Payload.getSourceAddress();
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = Arrays.copyOf(DHCPPayload.getClientHardwareAddress(), DHCPPayload.getClientHardwareAddress().length);

							List<DHCPOption> options = DHCPPayload.getOptions();
							for (DHCPOption option : options) {
								if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_IP) {
									desiredIPAddr = Arrays.copyOf(option.getData(), option.getData().length);
									// TODO: Double-check to make sure checking if this is NULL breaks anything else somewhere (logic-wise)
									if (theDHCPPool.getDHCPbindingFromMAC(chaddr) != null && !Arrays.equals(option.getData(), theDHCPPool.getDHCPbindingFromMAC(chaddr).getIPv4AddressBytes())) {
										// This client wants a different IP than what we have on file, so cancel its HOLD lease now (if we have one)
										theDHCPPool.cancelLeaseOfMAC(chaddr);
										return Command.CONTINUE;
									}
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_DHCP_SERVER) {
									if (!Arrays.equals(option.getData(), DHCP_SERVER_DHCP_SERVER_IP)) {
										// We're not the DHCPServer the client wants to use, so cancel its HOLD lease now and ignore the client
										theDHCPPool.cancelLeaseOfMAC(chaddr);
										return Command.CONTINUE;
									}
								} else if (option.getCode() == DHCP_REQ_PARAM_OPTION_CODE_REQUESTED_PARAMTERS) {
									requestOrder = getRequestedParameters(DHCPPayload, false);
								}
							}
							// Process REQUEST message and prepare an ACK with default lease time
							// This extends the hold lease time to that of a normal lease
							boolean sendACK = true;
							synchronized (theDHCPPool) {
								if (!theDHCPPool.hasAvailableAddresses()) {
									log.info("DHCP Pool is full! Consider increasing the pool size.");
									log.info("Device with MAC " + MACAddress.valueOf(chaddr).toString() + " was not granted an IP lease");
									return Command.CONTINUE;
								}
								DHCPBinding lease = theDHCPPool.getStaticLease(chaddr);

								// Get any binding, in use now or not
								if (desiredIPAddr != null && lease == null) {
									lease = theDHCPPool.getDHCPbindingFromIPv4(desiredIPAddr);
								} else if (lease == null) {
									lease = theDHCPPool.getAnyAvailableLease(chaddr);
								}
								// This IP is not in our allocation range
								if (lease == null) {
									log.info("The IP " + IPv4.fromIPv4Address(IPv4.toIPv4Address(desiredIPAddr)) + " is not in the range " 
											+ IPv4.fromIPv4Address(IPv4.toIPv4Address(DHCP_SERVER_IP_START)) + " to " + IPv4.fromIPv4Address(IPv4.toIPv4Address(DHCP_SERVER_IP_STOP)));
									log.info("Device with MAC " + MACAddress.valueOf(chaddr).toString() + " was not granted an IP lease");
									sendACK = false;
									// Determine if the IP in the binding we just retrieved is okay to allocate to the MAC requesting it
								} else if (!lease.isMACMemberOf(chaddr) && lease.isActiveLease()) {
									log.debug("Tried to REQUEST an IP that is currently assigned to another MAC");
									log.debug("Device with MAC " + MACAddress.valueOf(chaddr).toString() + " was not granted an IP lease");
									sendACK = false;
									// Check if we want to renew the MAC's current lease
								} else if (lease.isMACMemberOf(chaddr) && lease.isActiveLease()) {
									log.debug("Renewing lease for MAC " + MACAddress.valueOf(chaddr).toString());
									theDHCPPool.renewLease(lease.getIPv4AddressBytes(), DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS);
									yiaddr = IPv4.toIPv4Address(lease.getIPv4AddressBytes());
									log.debug("Finalized renewed lease for " + IPv4.fromIPv4Address(yiaddr) + " " + MACAddress.valueOf(chaddr).toString());
									// Check if we want to create a new lease for the MAC
								} else if (!lease.isActiveLease()){
									log.debug("Assigning new lease for MAC " + MACAddress.valueOf(chaddr).toString());
									theDHCPPool.setDHCPbinding(lease, chaddr, DHCP_SERVER_DEFAULT_LEASE_TIME_SECONDS);
									yiaddr = IPv4.toIPv4Address(lease.getIPv4AddressBytes());
									log.debug("Finalized renewed lease for " + IPv4.fromIPv4Address(yiaddr) + " " + MACAddress.valueOf(chaddr).toString());
								} else {
									log.debug("Don't know how we got here");
									return Command.CONTINUE;
								}
								if (sendACK) {
									updateClientLocation(lease, sw, inPort);
								}
							}
							if (sendACK) {
								sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);							
							} else {
								sendDHCPNack(sw, inPort, chaddr, giaddr, xid);
							}
						} // END IF REQUEST
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_RELEASE)) {
							if (DHCPPayload.getServerIPAddress() != CONTROLLER_IP) {
								log.info("DHCP RELEASE message not for our DHCP server");
								// Send the packet out the port it would normally go out via the Forwarding module
								// Execution jumps to return Command.CONTINUE at end of receive()
							} else {
								log.debug("Got DHCP RELEASE. Cancelling remaining time on DHCP lease");
								synchronized(theDHCPPool) {
									if (theDHCPPool.cancelLeaseOfMAC(DHCPPayload.getClientHardwareAddress())) {
										log.info("Cancelled DHCP lease of " + MACAddress.valueOf(DHCPPayload.getClientHardwareAddress()).toString());
										log.info("IP " + theDHCPPool.getDHCPbindingFromMAC(DHCPPayload.getClientHardwareAddress()).getIPv4AddresString()
												+ " is now available in the DHCP address pool");
									} else {
										log.debug("Lease of " + MACAddress.valueOf(DHCPPayload.getClientHardwareAddress()).toString()
												+ " was already inactive");
									}
								}
							}
						} // END IF RELEASE
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_DECLINE)) {
							log.debug("Got DHCP DECLINE. Cancelling HOLD time on DHCP lease");
							synchronized(theDHCPPool) {
								if (theDHCPPool.cancelLeaseOfMAC(DHCPPayload.getClientHardwareAddress())) {
									log.info("Cancelled DHCP lease of " + MACAddress.valueOf(DHCPPayload.getClientHardwareAddress()).toString());
								} else {
									log.info("HOLD Lease of " + MACAddress.valueOf(DHCPPayload.getClientHardwareAddress()).toString()
											+ " has already expired");
								}
							}
						} // END IF DECLINE
						else if (Arrays.equals(DHCPPayload.getOption(DHCP.DHCPOptionCode.OptionCode_MessageType).getData(), DHCP_MSG_TYPE_INFORM)) {
							log.debug("Got DHCP INFORM. Retreiving requested parameters from message");
							IPv4SrcAddr = IPv4Payload.getSourceAddress();
							xid = DHCPPayload.getTransactionId();
							yiaddr = DHCPPayload.getYourIPAddress();
							giaddr = DHCPPayload.getGatewayIPAddress();
							chaddr = Arrays.copyOf(DHCPPayload.getClientHardwareAddress(), DHCPPayload.getClientHardwareAddress().length);

							// Get the requests from the INFORM message. True for inform -- we don't want to include lease information
							requestOrder = getRequestedParameters(DHCPPayload, true);

							// Process INFORM message and send an ACK with requested information
							sendDHCPAck(sw, inPort, chaddr, IPv4SrcAddr, yiaddr, giaddr, xid, requestOrder);							
						} // END IF INFORM
					} // END IF DHCP OPCODE REQUEST 
					else if (DHCPPayload.getOpCode() == DHCP_OPCODE_REPLY) {
						// Do nothing right now. The DHCP DHCPServer isn't supposed to receive replies but ISSUE them instead
						log.debug("Got an OFFER/ACK (REPLY) on switch " + sw.getStringId());
						// can return Command.STOP here to prevent DHCP packets leaking out of the handoff network, but this would prevent replies
						// from being passed through participating (but middle-man) OVS or physical OF switches.
					} else {
						log.debug("Got DHCP packet, but not a known DHCP packet opcode");
					}
				} // END IF DHCP packet
			} // END IF UDP packet
		} // END IF IPv4 packet
		return Command.CONTINUE;
	} // END of receive(pkt)

	public void updateClientLocation(DHCPBinding clientBinding, IOFSwitch sw, short inPort) {
		// If the client is new, then give it a home switch...
		if (clientBinding.getHomeSwitch().equals("")) {
			clientBinding.setHomeSwitch(sw.getStringId());
			log.debug("Setting home switch for client " + clientBinding.getIPv4AddresString() + " " + clientBinding.getMACAddressesString() 
					+ " " + sw.getStringId());
			// Set flows for the home (current) switch
			if (sw.getStringId().equals(WIFI_NODE_WIFI_OVS_DPID)) {
				log.info("Adding INITIAL WIFI FLOW for client " + clientBinding.getIPv4AddresString() + " at " + clientBinding.getHomeSwitch() );
				OFFlowMod flow = new OFFlowMod();
				OFMatch match = new OFMatch();
				OFActionDataLayerDestination dldstAction = new OFActionDataLayerDestination();
				ArrayList<OFAction> actionList = new ArrayList<OFAction>();
				OFActionOutput outputAction = new OFActionOutput();
				match.setInputPort(OFPort.OFPP_LOCAL.getValue());
				match.setDataLayerType(Ethernet.TYPE_IPv4);
				match.setNetworkDestination(IPv4.toIPv4Address(clientBinding.getIPv4AddressBytes())); // match only our client's packets
				dldstAction.setType(OFActionType.SET_DL_DST);
				dldstAction.setDataLayerAddress(clientBinding.getCurrentMACAddressBytes());
				dldstAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
				actionList.add(dldstAction);
				outputAction.setType(OFActionType.OUTPUT);
				outputAction.setPort(ROOT_NODE_ROOT_OVS_WIFI_PATCH);
				outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
				actionList.add(outputAction);
				flow.setCookie(0);
				flow.setBufferId(-1);
				flow.setOutPort(ROOT_NODE_ROOT_OVS_WIFI_PATCH);
				flow.setActions(actionList);
				flow.setMatch(match);
				flow.setPriority((short) 32768);
				flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU() + dldstAction.getLengthU());
				String flowName = "WiFi-client-" + clientBinding.getIPv4AddresString();
				sfp.addFlow(flowName, flow, ROOT_NODE_ROOT_OVS_DPID);
				clientBinding.setFlowName(flowName);
				log.info("added flow on SW " + ROOT_NODE_ROOT_OVS_DPID + flowName);
				actionList.clear();
			} else if (sw.getStringId().equals(ROOT_NODE_WIMAX_OVS_DPID)) {
				log.info("Adding INITIAL WIMAX FLOW for client " + clientBinding.getIPv4AddresString() + " at " + clientBinding.getHomeSwitch() );
				OFFlowMod flow = new OFFlowMod();
				OFMatch match = new OFMatch();
				OFActionDataLayerDestination dldstAction = new OFActionDataLayerDestination();
				ArrayList<OFAction> actionList = new ArrayList<OFAction>();
				OFActionOutput outputAction = new OFActionOutput();
				match.setInputPort(OFPort.OFPP_LOCAL.getValue());
				match.setDataLayerType(Ethernet.TYPE_IPv4);
				match.setNetworkDestination(IPv4.toIPv4Address(clientBinding.getIPv4AddressBytes())); // match only our client's packets
				dldstAction.setType(OFActionType.SET_DL_DST);
				dldstAction.setDataLayerAddress(clientBinding.getCurrentMACAddressBytes());
				dldstAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
				actionList.add(dldstAction);
				outputAction.setType(OFActionType.OUTPUT);
				outputAction.setPort(ROOT_NODE_ROOT_OVS_WIMAX_PATCH);
				outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
				actionList.add(outputAction);
				flow.setCookie(0);
				flow.setBufferId(-1);
				flow.setOutPort(ROOT_NODE_ROOT_OVS_WIMAX_PATCH);
				flow.setActions(actionList);
				flow.setMatch(match);
				flow.setPriority((short) 32768);
				flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU() + dldstAction.getLengthU());
				String flowName = "WiMAX-client-" + clientBinding.getIPv4AddresString();
				sfp.addFlow(flowName, flow, ROOT_NODE_ROOT_OVS_DPID);
				clientBinding.setFlowName(flowName);
				log.info("added flow on SW " + ROOT_NODE_ROOT_OVS_DPID + flowName);
				actionList.clear();
			}
			// If the client has already associated with a network (and thus has a flow name)
		} else if (sw.getStringId().equals(ROOT_NODE_WIMAX_OVS_DPID)) {
			if (clientBinding.getFlowName().startsWith("WiMAX")) {
				log.debug("Client is still @ WiMAX!");
			} else if (clientBinding.getFlowName().startsWith("WiFi")) {
				// Client is new to the WiMAX network
				sfp.deleteFlow(clientBinding.getFlowName());
				clientBinding.setFlowName("");
				log.debug("Client has arrived @ WiMAX!");
				log.info("Adding WIMAX FLOW for client " + clientBinding.getIPv4AddresString() + " at " + clientBinding.getHomeSwitch() );
				OFFlowMod flow = new OFFlowMod();
				OFMatch match = new OFMatch();
				OFActionDataLayerDestination dldstAction = new OFActionDataLayerDestination();
				ArrayList<OFAction> actionList = new ArrayList<OFAction>();
				OFActionOutput outputAction = new OFActionOutput();
				match.setInputPort(OFPort.OFPP_LOCAL.getValue());
				match.setDataLayerType(Ethernet.TYPE_IPv4);
				match.setNetworkDestination(IPv4.toIPv4Address(clientBinding.getIPv4AddressBytes())); // match only our client's packets
				dldstAction.setType(OFActionType.SET_DL_DST);
				dldstAction.setDataLayerAddress(clientBinding.getCurrentMACAddressBytes());
				dldstAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
				actionList.add(dldstAction);
				outputAction.setType(OFActionType.OUTPUT);
				outputAction.setPort(ROOT_NODE_ROOT_OVS_WIMAX_PATCH);
				outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
				actionList.add(outputAction);
				flow.setCookie(0);
				flow.setBufferId(-1);
				flow.setOutPort(ROOT_NODE_ROOT_OVS_WIMAX_PATCH);
				flow.setActions(actionList);
				flow.setMatch(match);
				flow.setPriority((short) 32768);
				flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU() + dldstAction.getLengthU());
				String flowName = "WiMAX-client-" + clientBinding.getIPv4AddresString();
				sfp.addFlow(flowName, flow, ROOT_NODE_ROOT_OVS_DPID);
				clientBinding.setFlowName(flowName);
				log.info("added flow on SW " + ROOT_NODE_ROOT_OVS_DPID + flowName);
				actionList.clear();
			}
		} else if (sw.getStringId().equals(WIFI_NODE_WIFI_OVS_DPID)) {
			if (clientBinding.getFlowName().startsWith("WiFi")) {
				log.debug("Client is still @ WiFi!");
			} else if (clientBinding.getFlowName().startsWith("WiMAX")) {
				// Client is new to the WiFi network
				sfp.deleteFlow(clientBinding.getFlowName());
				clientBinding.setFlowName("");
				log.debug("Client has arrived @ WiFi!");
				log.info("Adding WIFI FLOW for client " + clientBinding.getIPv4AddresString() + " at " + clientBinding.getHomeSwitch() );
				OFFlowMod flow = new OFFlowMod();
				OFMatch match = new OFMatch();
				OFActionDataLayerDestination dldstAction = new OFActionDataLayerDestination();
				ArrayList<OFAction> actionList = new ArrayList<OFAction>();
				OFActionOutput outputAction = new OFActionOutput();
				match.setInputPort(OFPort.OFPP_LOCAL.getValue());
				match.setDataLayerType(Ethernet.TYPE_IPv4);
				match.setNetworkDestination(IPv4.toIPv4Address(clientBinding.getIPv4AddressBytes())); // match only our client's packets
				dldstAction.setType(OFActionType.SET_DL_DST);
				dldstAction.setDataLayerAddress(clientBinding.getCurrentMACAddressBytes());
				dldstAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
				actionList.add(dldstAction);
				outputAction.setType(OFActionType.OUTPUT);
				outputAction.setPort(ROOT_NODE_ROOT_OVS_WIFI_PATCH);
				outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
				actionList.add(outputAction);
				flow.setCookie(0);
				flow.setBufferId(-1);
				flow.setOutPort(ROOT_NODE_ROOT_OVS_WIFI_PATCH);
				flow.setActions(actionList);
				flow.setMatch(match);
				flow.setPriority((short) 32768);
				flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + outputAction.getLengthU() + dldstAction.getLengthU());
				String flowName = "WiFi-client-" + clientBinding.getIPv4AddresString();
				sfp.addFlow(flowName, flow, ROOT_NODE_ROOT_OVS_DPID);
				clientBinding.setFlowName(flowName);
				log.info("added flow on SW " + ROOT_NODE_ROOT_OVS_DPID + flowName);
				actionList.clear();
			}
		}
		return;
	}

	/**
	 * DHCPLeasePolice is a simple class that is instantiated and invoked
	 * as a runnable thread. The objective is to clean up the expired DHCP
	 * leases on a set time interval. Most DHCP leases are hours in length,
	 * so the granularity of our check can be on the order of minutes (IMHO).
	 * The period of the check for expired leases, in seconds, is specified
	 * in the configuration file:
	 * 
	 * 		floodlight/src/main/resources/floodlightdefault.properties
	 * 
	 * as option:
	 * 
	 * 		net.floodlightcontroller.dhcpserver.DHCPServer.lease-gc-period = <seconds>
	 * 
	 * where gc stands for "garbage collection".
	 * 
	 * @author Ryan Izard, rizard@g.clemson.edu
	 *
	 */
	class DHCPLeasePolice implements Runnable {
		@Override
		public void run() {
			log.info("Cleaning any expired DHCP leases...");
			ArrayList<DHCPBinding> newAvailableBindings;
			synchronized(theDHCPPool) {
				// Loop through lease pool and check all leases to see if they are expired
				// If a lease is expired, then clean it up and make the binding available
				newAvailableBindings = theDHCPPool.cleanExpiredLeases();
			}
			for (DHCPBinding binding : newAvailableBindings) {
				log.info("MAC " + binding.getMACAddressesString() + " has expired");
				log.info("Lease now available for IP " + binding.getIPv4AddresString());
			}
		}
	} // END DHCPLeasePolice Class
} // END DHCPServer Class