package net.floodlightcontroller.arprelay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPhysicalPort;
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


public class ARPRelay implements IFloodlightModule, IOFSwitchListener {
	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;

	@Override
	public void addedSwitch(IOFSwitch sw) {
		OFFlowMod flow = new OFFlowMod();
		OFMatch match = new OFMatch();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput action = new OFActionOutput();
		int wildcards;

		/*
		 * Loop over all "physical" ports of the switch and add a FLOOD flow on that port
		 * for all ARP packets. "Physical" means any port that can have a device attached,
		 * I think.
		 */
		for (OFPhysicalPort port : sw.getPorts()) {
			flow = new OFFlowMod();
			match = new OFMatch();
			wildcards = OFMatch.OFPFW_ALL;
			match.setInputPort(port.getPortNumber());
			wildcards = wildcards & ~OFMatch.OFPFW_IN_PORT;
			match.setDataLayerType((short) 0x806); // this is ARP. Might need to use the decimal version of 0x806
			wildcards = wildcards & ~OFMatch.OFPFW_DL_TYPE;
			action.setType(OFActionType.OUTPUT);
			action.setPort(OFPort.OFPP_FLOOD.getValue());
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_FLOOD.getValue()); // have to repeat this due to API limitation
			flow.setActions(actionList);
			match.setWildcards(wildcards);
			flow.setMatch(match);
			flow.setPriority((short) 32767); // max priority
			flow.setHardTimeout((short) 0); // should be infinite timeouts (i.e. as long as the switch is connected)
			flow.setIdleTimeout((short) 0);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			try {
				sw.write(flow, null); // don't care about the context
				log.debug("Writing flow on switch {} to FLOOD from port {}.", sw.getStringId(), port.getPortNumber());
				log.debug("Flow: {}", flow.toString());
			} catch (IOException e) {
				log.error(e.getMessage());
			}
			actionList.clear();
			
			/*
			 * Need to have specific flow from port A to LOCAL for all ports
			 * b/c LOCAL is not a physical port and FLOOD only applies to
			 * physical ports.
			 */
			
			flow = new OFFlowMod();
			match = new OFMatch();
			wildcards = OFMatch.OFPFW_ALL;
			match.setInputPort(port.getPortNumber());
			wildcards = wildcards & ~OFMatch.OFPFW_IN_PORT;
			match.setDataLayerType((short) 0x806); // this is ARP. Might need to use the decimal version of 0x806
			wildcards = wildcards & ~OFMatch.OFPFW_DL_TYPE;
			action.setType(OFActionType.OUTPUT);
			action.setPort(OFPort.OFPP_LOCAL.getValue());
			action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			actionList.add(action);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_LOCAL.getValue()); // have to repeat this due to API limitation
			flow.setActions(actionList);
			flow.setMatch(match);
			match.setWildcards(wildcards);
			flow.setPriority((short) 32767); // max priority
			flow.setHardTimeout((short) 0); // should be infinite timeouts (i.e. as long as the switch is connected)
			flow.setIdleTimeout((short) 0);
			flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
			try {
				sw.write(flow, null); // don't care about the context
				log.debug("Writing flow on switch {} to LOCAL from port {}.", sw.getStringId(), port.getPortNumber());
				log.debug("Flow: {}", flow.toString());
			} catch (IOException e) {
				log.error(e.getMessage());
			}
			actionList.clear();
		}
		
		/*
		 *  LOCAL is not a physical port, I don't think. So, handle it separately if need be. 
		 *  (Might be able to remove this if LOCAL is deemed a "physical" port.)
		 */
		flow = new OFFlowMod();
		match = new OFMatch();
		wildcards = OFMatch.OFPFW_ALL;
		match.setInputPort(OFPort.OFPP_LOCAL.getValue());
		wildcards = wildcards & ~OFMatch.OFPFW_IN_PORT;
		match.setDataLayerType((short) 0x806); // this is ARP. Might need to use the decimal version of 0x806
		wildcards = wildcards & ~OFMatch.OFPFW_DL_TYPE;
		action.setType(OFActionType.OUTPUT);
		action.setPort(OFPort.OFPP_FLOOD.getValue());
		action.setLength((short) OFActionOutput.MINIMUM_LENGTH);
		actionList.add(action);
		flow.setCookie(0);
		flow.setBufferId(-1);
		flow.setOutPort(OFPort.OFPP_FLOOD.getValue()); // have to repeat this due to API limitation
		flow.setActions(actionList);
		flow.setMatch(match);
		match.setWildcards(wildcards);
		flow.setPriority((short) 32767); // max priority
		flow.setHardTimeout((short) 0); // should be infinite timeouts (i.e. as long as the switch is connected)
		flow.setIdleTimeout((short) 0);
		flow.setLengthU(OFFlowMod.MINIMUM_LENGTH + action.getLengthU());
		try {
			sw.write(flow, null); // don't care about the context
			log.debug("Writing flow on switch {} to FLOOD from LOCAL port.", sw.getStringId());
			log.debug("Flow: {}", flow.toString());
		} catch (IOException e) {
			log.error(e.getMessage());
		}		
	}

	@Override
	public void removedSwitch(IOFSwitch sw) {
	}

	@Override
	public void switchPortChanged(Long switchId) {
	}

	@Override
	public String getName() {
		return "arp-relay";
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
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		log = LoggerFactory.getLogger(ARPRelay.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFSwitchListener(this);
	}

}