package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;

import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
// Adding a comment to test a new commit
public class DHCPSwitchFlowSetter implements IFloodlightModule, IOFSwitchListener {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	private static String ROOT_NODE_ROOT_OVS_DPID;
	private static String ROOT_NODE_WIFI_OVS_DPID;
	private static String ROOT_NODE_WIMAX_OVS_DPID;
	private static String WIFI_NODE_WIFI_OVS_DPID;
	private static String WIFI_NODE_TUNNEL_OVS_DPID;
	private static short ROOT_NODE_WIFI_OVS_PATCH;
	private static short ROOT_NODE_WIFI_OVS_TUNNEL;
	private static short ROOT_NODE_WIMAX_OVS_PATCH;
	private static short ROOT_NODE_WIMAX_OVS_VLAN;
	private static short ROOT_NODE_ROOT_OVS_WIFI_PATCH;
	private static short ROOT_NODE_ROOT_OVS_WIMAX_PATCH;
	private static short WIFI_NODE_WIFI_OVS_PATCH;
	private static short WIFI_NODE_TUNNEL_OVS_PATCH;
	private static short WIFI_NODE_TUNNEL_OVS_TUNNEL;
	private static int ROOT_NODE_ROOT_OVS_IP;
	
	private static final short PRIORITY_MAX = (short) 32768;
	private static final short PRIORITY_HIGH = (short) 32000;
	private static final short PRIORITY_MEDIUM = (short) 16384;
	private static final short PRIORITY_LOW = (short) 1000;
	private static final short PRIORITY_MIN = (short) 0;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IStaticFlowEntryPusherService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		log = LoggerFactory.getLogger(DHCPServer.class);
		sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFSwitchListener(this);
		Map<String, String> configOptions = context.getConfigParams(this);

		try {
			ROOT_NODE_ROOT_OVS_DPID = configOptions.get("root-node-root-ovs-dpid");
			ROOT_NODE_WIFI_OVS_DPID = configOptions.get("root-node-wifi-ovs-dpid");
			
			ROOT_NODE_WIMAX_OVS_DPID = configOptions.get("root-node-wimax-ovs-dpid");
			WIFI_NODE_WIFI_OVS_DPID = configOptions.get("wifi-node-wifi-ovs-dpid");
			WIFI_NODE_TUNNEL_OVS_DPID = configOptions.get("wifi-node-tunnel-ovs-dpid");
			ROOT_NODE_ROOT_OVS_IP = IPv4.toIPv4Address(configOptions.get("root-node-root-ovs-ip"));
			ROOT_NODE_WIFI_OVS_PATCH = Short.parseShort(configOptions.get("root-node-wifi-ovs-patch-port"));
			ROOT_NODE_WIFI_OVS_TUNNEL = Short.parseShort(configOptions.get("root-node-wifi-ovs-tunnel-port"));
			ROOT_NODE_WIMAX_OVS_PATCH = Short.parseShort(configOptions.get("root-node-wimax-ovs-patch-port"));
			ROOT_NODE_WIMAX_OVS_VLAN = Short.parseShort(configOptions.get("root-node-wimax-ovs-vlan-port"));
			ROOT_NODE_ROOT_OVS_WIFI_PATCH = Short.parseShort(configOptions.get("root-node-root-ovs-wifi-patch-port"));
			ROOT_NODE_ROOT_OVS_WIMAX_PATCH = Short.parseShort(configOptions.get("root-node-root-ovs-wimax-patch-port"));
			WIFI_NODE_WIFI_OVS_PATCH = Short.parseShort(configOptions.get("wifi-node-wifi-ovs-patch-port"));
			WIFI_NODE_TUNNEL_OVS_TUNNEL = Short.parseShort(configOptions.get("wifi-node-tunnel-ovs-tunnel-port"));
			WIFI_NODE_TUNNEL_OVS_PATCH = Short.parseShort(configOptions.get("wifi-node-tunnel-ovs-patch-port"));
		} catch(IllegalArgumentException ex) {
			log.error("Incorrect DHCP Switch Flow Setter configuration options (illegal arg)", ex);
			throw ex;
		} catch(NullPointerException ex) {
			log.error("Incorrect DHCP Switch Flow Setter configuration options (null ptr)", ex);
			throw ex;
		}

	}

	@Override
	public void addedSwitch(IOFSwitch sw) {
		/** Insert static flows on all ports of the switch to redirect
		 * DHCP client --> DHCP DHCPServer traffic to the controller.
		 * DHCP client's operate on UDP port 67
		 */
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput action = new OFActionOutput();
		String flowName;
		
		if (sw.getStringId().equals(ROOT_NODE_WIFI_OVS_DPID)) {
			// root node, WiFi bridge, patch to tunnel port
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_WIFI_OVS_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort(ROOT_NODE_WIFI_OVS_TUNNEL);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ROOT_NODE_WIFI_OVS_TUNNEL);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-wifi-br-patch-tun";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, WiFi bridge, physical to patch
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_WIFI_OVS_TUNNEL);
			action.setType(OFActionType.OUTPUT);
			action.setPort(ROOT_NODE_WIFI_OVS_PATCH);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ROOT_NODE_WIFI_OVS_PATCH);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-wifi-br-tun-patch";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
		} else if (sw.getStringId().equals(ROOT_NODE_WIMAX_OVS_DPID)) {
			// root node, WiMAX bridge, patch to physical
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_WIMAX_OVS_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort(ROOT_NODE_WIMAX_OVS_VLAN);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ROOT_NODE_WIMAX_OVS_VLAN);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-wimax-br-patch-phys";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, WiMAX bridge, interface to patch
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_WIMAX_OVS_VLAN);
			action.setType(OFActionType.OUTPUT);
			action.setPort(ROOT_NODE_WIMAX_OVS_PATCH);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ROOT_NODE_WIMAX_OVS_PATCH);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-wimax-br-phys-patch";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, WiMAX bridge, DHCP on physical
			match.setInputPort(ROOT_NODE_WIMAX_OVS_VLAN);
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_UDP);
			match.setTransportSource(UDP.DHCP_CLIENT_PORT);
			action.setType(OFActionType.OUTPUT);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			action.setPort(OFPort.OFPP_CONTROLLER.getValue());
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_CONTROLLER.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_MAX);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-wimax-br-DHCP-phys";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
		} else if (sw.getStringId().equals(ROOT_NODE_ROOT_OVS_DPID)) {	
			// root node, root bridge, patch AP3 (WiFi) to Linux
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_ROOT_OVS_WIFI_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort(OFPort.OFPP_LOCAL.getValue());
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-root-br-patchWiFi-linux";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, root bridge, patch WiMAX to Linux
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(ROOT_NODE_ROOT_OVS_WIMAX_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort(OFPort.OFPP_LOCAL.getValue());
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-node-root-br-patchWiMAX-linux";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, root bridge, physical to Linux
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort((short) 1);
			action.setType(OFActionType.OUTPUT);
			action.setPort(OFPort.OFPP_LOCAL.getValue());
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_LOCAL.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-ovs-root-br-phys-linux";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, root bridge, Linux to physical (match extra: src-ip of root node...i.e. it's outbound)
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(OFPort.OFPP_LOCAL.getValue());
			match.setDataLayerType(Ethernet.TYPE_IPv4); // ... IP packets ... required for a match on an IP address
			match.setNetworkSource(ROOT_NODE_ROOT_OVS_IP);
			action.setType(OFActionType.OUTPUT);
			action.setPort((short) 1);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort((short) 1);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "root-ovs-root-br-linux-phys-egress";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
		} else if (sw.getStringId().equals(WIFI_NODE_WIFI_OVS_DPID)) {
			// WiFi node, WiFi bridge, physical to patch
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort((short) 1);
			action.setType(OFActionType.OUTPUT);
			action.setPort(WIFI_NODE_WIFI_OVS_PATCH);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(WIFI_NODE_WIFI_OVS_PATCH);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "wifi-node-wifi-br-phys-patch";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// WiFi node, WiFi bridge, patch to physical
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(WIFI_NODE_WIFI_OVS_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort((short) 1);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort((short) 1);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "wifi-node-wifi-br-patch-phys";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// root node, WiMAX bridge, DHCP on physical
			match.setInputPort((short) 1);
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkProtocol(IPv4.PROTOCOL_UDP);
			match.setTransportSource(UDP.DHCP_CLIENT_PORT);
			action.setType(OFActionType.OUTPUT);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			action.setPort(OFPort.OFPP_CONTROLLER.getValue());
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_CONTROLLER.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_MAX);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "wifi-node-wifi-br-DHCP-phys";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
		} else if (sw.getStringId().equals(WIFI_NODE_TUNNEL_OVS_DPID)) {
			// WiFi node, tunnel bridge, tunnel to patch
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(WIFI_NODE_TUNNEL_OVS_TUNNEL);
			action.setType(OFActionType.OUTPUT);
			action.setPort(WIFI_NODE_TUNNEL_OVS_PATCH);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(WIFI_NODE_TUNNEL_OVS_PATCH);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "wifi-node-tunnel-br-tun-patch";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
			// WiFi node, tunnel bridge, patch to tunnel
			flow = new OFFlowMod();
			match = new OFMatch();
			match.setInputPort(WIFI_NODE_TUNNEL_OVS_PATCH);
			action.setType(OFActionType.OUTPUT);
			action.setPort(WIFI_NODE_TUNNEL_OVS_TUNNEL);
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(WIFI_NODE_TUNNEL_OVS_TUNNEL);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority(PRIORITY_HIGH);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			flowName = "wifi-node-tunnel-br-patch-tun";
			sfp.addFlow(flowName, flow, sw.getStringId());
			log.info("added flow on SW " + sw.getStringId() + flowName);
			actionList.clear();
		}
	}

	@Override
	public void removedSwitch(IOFSwitch sw) {
		// We can't exactly remove flows from a switch if/when it disconnects, so do nothing?
	}

	@Override
	public void switchPortChanged(Long switchId) {
	}

	@Override
	public String getName() {
		return DHCPSwitchFlowSetter.class.getSimpleName();
	}
}