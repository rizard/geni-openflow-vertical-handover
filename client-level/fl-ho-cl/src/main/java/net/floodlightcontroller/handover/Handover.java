package net.floodlightcontroller.handover;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import de.taimos.gpsd4java.*;
import de.taimos.gpsd4java.api.IObjectListener;
import de.taimos.gpsd4java.api.ObjectListener;
import de.taimos.gpsd4java.backend.AbstractResultParser;
import de.taimos.gpsd4java.backend.GPSdEndpoint;
import de.taimos.gpsd4java.backend.ResultParser;
import de.taimos.gpsd4java.types.ATTObject;
import de.taimos.gpsd4java.types.DeviceObject;
import de.taimos.gpsd4java.types.DevicesObject;
import de.taimos.gpsd4java.types.ParseException;
import de.taimos.gpsd4java.types.SKYObject;
import de.taimos.gpsd4java.types.TPVObject;
import de.taimos.gpsd4java.types.subframes.SUBFRAMEObject;

public class Handover implements IFloodlightModule {

	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	private static ScheduledThreadPoolExecutor grcProbeMgr;
	private static Runnable grcProbe;

	private static GPSdEndpoint GPSD_CONN = null;
	private static ResultParser GPSD_RESULT_PARSER = null;
	private static int GPSD_TCP_PORT = 0; // default in GPSD of 2947
	private static double GPSD_LATITUDE = 0;
	private static double GPSD_LONGITUDE = 0;
	private static double GPSD_ALTITUDE = 0;

	private static long GRC_PROBE_INTERVAL_SECONDS;
	private static String GRC_URL = null;

	private static String OVS_TAP_DPID;
	private static String OVS_WIFI0_DPID;
	private static String OVS_WIFI1_DPID;
	private static String OVS_WIMAX0_DPID;
	private static String OVS_WIMAX1_DPID;
	private static String OVS_ETHERNET_DPID;

	private static byte[] TAP_MAC;
	private static byte[] WIFI0_MAC;
	private static byte[] WIFI1_MAC;
	private static byte[] WIMAX0_MAC;
	private static byte[] WIMAX1_MAC;
	private static byte[] ETHERNET_MAC;

	private static String TAP_IFACE_NAME;
	private static String WIFI0_IFACE_NAME;
	private static String WIFI1_IFACE_NAME;
	private static String WIMAX0_IFACE_NAME;
	private static String WIMAX1_IFACE_NAME;
	private static String ETHERNET_IFACE_NAME;

	private static short OVS_TAP_TO_WIFI0_PATCH;
	private static short OVS_TAP_TO_WIFI1_PATCH;
	private static short OVS_TAP_TO_WIMAX0_PATCH;
	private static short OVS_TAP_TO_WIMAX1_PATCH;
	private static short OVS_TAP_TO_ETHERNET_PATCH;

	private static short OVS_WIFI0_TO_TAP_PATCH;
	private static short OVS_WIFI1_TO_TAP_PATCH;
	private static short OVS_WIMAX0_TO_TAP_PATCH;
	private static short OVS_WIMAX1_TO_TAP_PATCH;
	private static short OVS_ETHERNET_TO_TAP_PATCH;

	private static short OVS_TAP_LOCAL_PORT;
	private static short OVS_WIFI0_IFACE_PORT;
	private static short OVS_WIFI1_IFACE_PORT;
	private static short OVS_WIMAX0_IFACE_PORT;
	private static short OVS_WIMAX1_IFACE_PORT;
	private static short OVS_ETHERNET_IFACE_PORT;

	private static ArrayList<String> ACTIVE_FLOWS;
	private static final String OFFLINE = "offline";
	private static String ACTIVE_NETWORK_TYPE = OFFLINE;

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
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		sfp = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		log = LoggerFactory.getLogger(Handover.class);
		return;
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		Map<String, String> configOptions = context.getConfigParams(this);
		try {
			GRC_PROBE_INTERVAL_SECONDS = Long.parseLong(configOptions.get("grc-probe-interval-seconds"));
			GPSD_TCP_PORT = Integer.parseInt(configOptions.get("gpsd-tcp-port"));
			GRC_URL = configOptions.get("grc-url");

			TAP_IFACE_NAME = configOptions.get("tap-iface-name");
			WIFI0_IFACE_NAME = configOptions.get("wifi0-iface-name");
			WIFI1_IFACE_NAME = configOptions.get("wifi1-iface-name");
			WIMAX0_IFACE_NAME = configOptions.get("wimax0-iface-name");
			WIMAX1_IFACE_NAME = configOptions.get("wimax1-iface-name");
			ETHERNET_IFACE_NAME = configOptions.get("ethernet-iface-name");

			TAP_MAC = Ethernet.toMACAddress(configOptions.get("tap-mac"));
			WIFI0_MAC = Ethernet.toMACAddress(configOptions.get("wifi0-mac"));
			WIFI1_MAC = Ethernet.toMACAddress(configOptions.get("wifi1-mac"));
			WIMAX0_MAC = Ethernet.toMACAddress(configOptions.get("wimax0-mac"));
			WIMAX1_MAC = Ethernet.toMACAddress(configOptions.get("wimax1-mac"));
			ETHERNET_MAC = Ethernet.toMACAddress(configOptions.get("ethernet-mac"));


			OVS_TAP_DPID = configOptions.get("ovs-tap-dpid");
			OVS_WIFI0_DPID = configOptions.get("ovs-wifi0-dpid");
			OVS_WIFI1_DPID = configOptions.get("ovs-wifi1-dpid");
			OVS_WIMAX0_DPID = configOptions.get("ovs-wimax0-dpid");
			OVS_WIMAX1_DPID = configOptions.get("ovs-wimax1-dpid");
			OVS_ETHERNET_DPID = configOptions.get("ovs-ethernet-dpid");

			OVS_TAP_TO_WIFI0_PATCH = Short.parseShort(configOptions.get("ovs-tap-to-wifi0-patch"));
			OVS_TAP_TO_WIFI1_PATCH = Short.parseShort(configOptions.get("ovs-tap-to-wifi1-patch"));
			OVS_TAP_TO_WIMAX0_PATCH = Short.parseShort(configOptions.get("ovs-tap-to-wimax0-patch"));
			OVS_TAP_TO_WIMAX1_PATCH = Short.parseShort(configOptions.get("ovs-tap-to-wimax1-patch"));
			OVS_TAP_TO_ETHERNET_PATCH = Short.parseShort(configOptions.get("ovs-tap-to-ethernet-patch"));

			OVS_WIFI0_TO_TAP_PATCH = Short.parseShort(configOptions.get("ovs-wifi0-to-tap-patch"));
			OVS_WIFI1_TO_TAP_PATCH = Short.parseShort(configOptions.get("ovs-wifi1-to-tap-patch"));
			OVS_WIMAX0_TO_TAP_PATCH = Short.parseShort(configOptions.get("ovs-wimax0-to-tap-patch"));
			OVS_WIMAX1_TO_TAP_PATCH = Short.parseShort(configOptions.get("ovs-wimax1-to-tap-patch"));
			OVS_ETHERNET_TO_TAP_PATCH = Short.parseShort(configOptions.get("ovs-ethernet-to-tap-patch"));

			OVS_TAP_LOCAL_PORT = Short.parseShort(configOptions.get("ovs-tap-local-port"));
			OVS_WIFI0_IFACE_PORT = Short.parseShort(configOptions.get("ovs-wifi0-iface-port"));
			OVS_WIFI1_IFACE_PORT = Short.parseShort(configOptions.get("ovs-wifi1-iface-port"));
			OVS_WIMAX0_IFACE_PORT = Short.parseShort(configOptions.get("ovs-wimax0-iface-port"));
			OVS_WIMAX1_IFACE_PORT = Short.parseShort(configOptions.get("ovs-wimax1-iface-port"));
			OVS_ETHERNET_IFACE_PORT = Short.parseShort(configOptions.get("ovs-ethernet-iface-port"));

		} catch(Exception e) {
			log.error("Incorrect Handover configuration options", e);
			//throw e;
		}

		ACTIVE_FLOWS = new ArrayList<String>();

		try {
			GPSD_CONN = new GPSdEndpoint("127.0.0.1", GPSD_TCP_PORT, new ResultParser());
			GPSD_CONN.addListener(new ObjectListener(){
				@Override
				public void handleTPV(TPVObject tpv) {
					log.debug("GPSD: Updated TPV");
					GPSD_LATITUDE = tpv.getLatitude();
					GPSD_LONGITUDE = tpv.getLongitude();
					GPSD_ALTITUDE = tpv.getAltitude();
				}

				@Override
				public void handleSKY(SKYObject sky) {
					log.debug("GPSD: Updated SKY");
				}

				@Override
				public void handleATT(ATTObject att) {	
					log.debug("GPSD: Updated ATT");
				}

				@Override
				public void handleSUBFRAME(SUBFRAMEObject subframe) {		
					log.debug("GPSD: Updated SUBFRAME");
				}

				@Override
				public void handleDevices(DevicesObject devices) {
					log.debug("GPSD: Updated DEVICES");
					for (DeviceObject d : devices.getDevices()){
						log.debug("Device: " + d.toString());
					}
					try {
						GPSD_CONN.poll();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				@Override
				public void handleDevice(DeviceObject device) {
					log.debug("GPSD: Updated DEVICE");
				}
			});

			GPSD_CONN.start();
			
			log.debug("Version: " + GPSD_CONN.version().toString());
			GPSD_CONN.watch(true, true);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			throw e;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
			throw e;
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Periodically ask GRC for a handover decision
		grcProbeMgr = new ScheduledThreadPoolExecutor(1);
		grcProbe = new GRCProbe();
		grcProbeMgr.scheduleAtFixedRate(grcProbe, 10, GRC_PROBE_INTERVAL_SECONDS, TimeUnit.SECONDS);
		return;
	}

	private static String cmdExec(String cmdLine) {
		String line;
		String output = "";
		try {
			Process p = Runtime.getRuntime().exec(cmdLine);
			BufferedReader input = new BufferedReader
					(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				output += (line + '\n');
			}
			input.close();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		return output;
	}

	private void switchInterface(String networkType) {
		if(ACTIVE_NETWORK_TYPE.equals(networkType) || networkType.equals(OFFLINE))
			return;
		
		OFFlowMod flow;
		OFMatch match;
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput outputAction;
		OFActionDataLayerDestination dlDstAction;
		OFActionDataLayerSource dlSrcAction;
		String flowName;
		int flowLength;

		String ifaceOVSdpid;
		String tapOVSdpid = OVS_TAP_DPID;

		short tapOVSpatchPort;
		short tapOVShostPort = OVS_TAP_LOCAL_PORT;
		short ifaceOVSifacePort;
		short ifaceOVSpatchPort;

		byte[] ifaceMACaddr;
		byte[] tapMACaddr = TAP_MAC;		

		// based on the iface chosen, set appropriate parameters
		if (networkType.equals("Wifi")) {
			ifaceOVSdpid = OVS_WIFI0_DPID;
			tapOVSpatchPort = OVS_TAP_TO_WIFI0_PATCH;
			ifaceOVSifacePort = OVS_WIFI0_IFACE_PORT;
			ifaceOVSpatchPort = OVS_WIFI0_TO_TAP_PATCH;
			ifaceMACaddr = WIFI0_MAC;
		} else if (networkType.equals("WiMAX")) {
			ifaceOVSdpid = OVS_WIMAX0_DPID;
			tapOVSpatchPort = OVS_TAP_TO_WIMAX0_PATCH;
			ifaceOVSifacePort = OVS_WIMAX0_IFACE_PORT;
			ifaceOVSpatchPort = OVS_WIMAX0_TO_TAP_PATCH;
			ifaceMACaddr = WIMAX0_MAC;
		} else {
			//TODO: remove this... not needed
			//fail over to ethernet
			ifaceOVSdpid = OVS_ETHERNET_DPID;
			tapOVSpatchPort = OVS_TAP_TO_ETHERNET_PATCH;
			ifaceOVSifacePort = OVS_ETHERNET_IFACE_PORT;
			ifaceOVSpatchPort = OVS_ETHERNET_TO_TAP_PATCH;
			ifaceMACaddr = ETHERNET_MAC;
		}

		// Remove old flows and switch only if we are switching to a different interface.
		if (!networkType.equals(ACTIVE_NETWORK_TYPE)) {
			while (!ACTIVE_FLOWS.isEmpty()) {
				String tmp = ACTIVE_FLOWS.remove(0);
				log.debug("In prep. for switch, removing flow " + tmp);
				sfp.deleteFlow(tmp);
			}

			// update to the new interface we have been told to use
			ACTIVE_NETWORK_TYPE = networkType;

			// first, insert flows at the tap OVS. Do the MAC rewrites here (just to keep them all on one OVS).
			// this means there should be no ARP flows at the tap OVS (all ARP packets will be sent to the controller auto-magically).
			// tap --> patch (IP)
			flow = new OFFlowMod();
			match = new OFMatch();
			outputAction = new OFActionOutput();
			dlSrcAction = new OFActionDataLayerSource();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(tapOVShostPort);
			match.setDataLayerType((short) 0x0800);
			dlSrcAction.setType(OFActionType.SET_DL_SRC);
			dlSrcAction.setDataLayerAddress(ifaceMACaddr);
			dlSrcAction.setLength((short) OFActionDataLayerSource.MINIMUM_LENGTH);
			flowLength = flowLength + dlSrcAction.getLengthU();
			actionList.add(dlSrcAction);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(tapOVSpatchPort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(tapOVSpatchPort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "tap-to-patch-ip";
			sfp.addFlow(flowName, flow, tapOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + tapOVSdpid + flowName);
			actionList.clear();
			// tap --> patch (CONTROLLER) (ARP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(tapOVShostPort);
			match.setDataLayerType((short) 0x0806);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(OFPort.OFPP_CONTROLLER.getValue());
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_CONTROLLER.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "tap-to-patch(controller)-arp";
			sfp.addFlow(flowName, flow, tapOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + tapOVSdpid + flowName);
			actionList.clear();
			// tap <-- patch (IP)
			flow = new OFFlowMod();
			match = new OFMatch();
			dlDstAction = new OFActionDataLayerDestination();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(tapOVSpatchPort);
			match.setDataLayerType((short) 0x0800);
			dlDstAction.setType(OFActionType.SET_DL_DST);
			dlDstAction.setDataLayerAddress(tapMACaddr);
			dlDstAction.setLength((short) OFActionDataLayerDestination.MINIMUM_LENGTH);
			flowLength = flowLength + dlDstAction.getLengthU();
			actionList.add(dlDstAction);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(tapOVShostPort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(tapOVShostPort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "tap-from-patch-ip";
			sfp.addFlow(flowName, flow, tapOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + tapOVSdpid + flowName);
			actionList.clear();
			// tap (CONTROLLER) <-- patch (ARP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(tapOVSpatchPort);
			match.setDataLayerType((short) 0x0806);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(OFPort.OFPP_CONTROLLER.getValue());
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(OFPort.OFPP_CONTROLLER.getValue());
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "tap(controller)-from-patch-arp";
			sfp.addFlow(flowName, flow, tapOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + tapOVSdpid + flowName);
			actionList.clear();

			// now, insert the flows on the OVS corresponding to the iface of choice.
			// no rewrites should occur here. Forward all packets of any ethertype.
			// iface <-- patch (IP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(ifaceOVSpatchPort);
			match.setDataLayerType((short) 0x0800);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(ifaceOVSifacePort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ifaceOVSifacePort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "iface-from-patch-ip";
			sfp.addFlow(flowName, flow, ifaceOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + ifaceOVSdpid + flowName);
			actionList.clear();
			// iface <-- patch (ARP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(ifaceOVSpatchPort);
			match.setDataLayerType((short) 0x0806);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(ifaceOVSifacePort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ifaceOVSifacePort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "iface-from-patch-arp";
			sfp.addFlow(flowName, flow, ifaceOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + ifaceOVSdpid + flowName);
			actionList.clear();
			// iface --> patch (IP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(ifaceOVSifacePort);
			match.setDataLayerType((short) 0x0800);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(ifaceOVSpatchPort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ifaceOVSpatchPort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "iface-to-patch-ip";
			sfp.addFlow(flowName, flow, ifaceOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + ifaceOVSdpid + flowName);
			actionList.clear();
			// iface --> patch (ARP)
			flow = new OFFlowMod();
			match = new OFMatch();
			flowLength = OFFlowMod.MINIMUM_LENGTH;
			match.setInputPort(ifaceOVSifacePort);
			match.setDataLayerType((short) 0x0806);
			outputAction.setType(OFActionType.OUTPUT);
			outputAction.setPort(ifaceOVSpatchPort);
			outputAction.setLength((short) OFActionOutput.MINIMUM_LENGTH);
			flowLength = flowLength + outputAction.getLengthU();
			actionList.add(outputAction);
			flow.setCookie(0);
			flow.setBufferId(-1);
			flow.setOutPort(ifaceOVSpatchPort);
			flow.setActions(actionList);
			flow.setMatch(match);
			flow.setPriority((short) 32768);
			flow.setLengthU(flowLength);
			flowName = "iface-to-patch-arp";
			sfp.addFlow(flowName, flow, ifaceOVSdpid);
			ACTIVE_FLOWS.add(flowName);
			log.info("added flow on SW " + ifaceOVSdpid + flowName);
			actionList.clear();
		}
		
		return;
	}

	// HTTP POST request
	private String askGRC() {
		try {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(GRC_URL);
			List <NameValuePair> nvps = new ArrayList <NameValuePair>();
			
			//we need a list of all available network types, this shouldn't be hard coded
			ArrayList<String> networkTypes = new ArrayList<String>();
			networkTypes.add("WiMAX");
			networkTypes.add("Wifi");
			
			//create a JSON object for every interface name, containing collected details
			ArrayList<JSONObject> netTypeObjs = new ArrayList<JSONObject>();
			for(String netType : networkTypes){
				JSONObject obj = new JSONObject();
				obj.put("name", netType);
				obj.put("operator", netType.equals("Wifi")? "" :"Clemson");
				netTypeObjs.add(obj);
			}

			//create a JSON array containing all of our JSON objects, and include it in our request
			log.debug("Latitude: " + Double.toString(GPSD_LATITUDE) + ", Longitude: " + Double.toString(GPSD_LONGITUDE));
			JSONArray jNetTypesArray = new JSONArray(netTypeObjs);
			nvps.add(new BasicNameValuePair("latitude", Double.toString(GPSD_LATITUDE)));
			nvps.add(new BasicNameValuePair("longitude", Double.toString(GPSD_LONGITUDE)));
			nvps.add(new BasicNameValuePair("network_types", jNetTypesArray.toString()));
			
			//set POST parameters and execute request
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			HttpResponse response = httpclient.execute(httpPost);
			int status = response.getStatusLine().getStatusCode();

			//retreive JSON response
			String body;
			if(status >= 200 && status < 300){
				HttpEntity entity = response.getEntity();
				body = EntityUtils.toString(entity);
			} else {
				body = "ERROR";
			}

			//parse JSON response
			JSONObject responseObj = new JSONObject(body);
			String responseNet = responseObj.getString("network");
			if(responseNet.isEmpty()){
			    log.debug("ERROR: Empty response from server, check args.");
				return OFFLINE;
			}
			
			log.debug("Switching to: " + responseNet);
			return responseObj.getString("network");
		} catch(Exception e) {
			e.printStackTrace();
		} 
		return OFFLINE;
	}

	class GRCProbe implements Runnable {
		@Override
		public void run() {
			log.info("Asking GRC (Cybertiger) for a handover decision...");
			// do it here
			switchInterface(askGRC());
			return;
		}
	} // END GRCProbe Class
} // END Handover Module
