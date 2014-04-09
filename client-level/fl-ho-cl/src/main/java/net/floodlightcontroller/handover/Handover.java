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
import org.json.JSONObject;
import org.json.JSONStringer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;

import de.taimos.gpsd4java.*;
import de.taimos.gpsd4java.backend.AbstractResultParser;
import de.taimos.gpsd4java.backend.GPSdEndpoint;
import de.taimos.gpsd4java.backend.ResultParser;

public class Handover implements IFloodlightModule {

	protected static Logger log;
	protected IFloodlightProviderService floodlightProvider;
	protected IStaticFlowEntryPusherService sfp;

	private static ScheduledThreadPoolExecutor grcProbeMgr;
	private static Runnable grcProbe;
	
	private static GPSdEndpoint GPSD_CONN = null;
	private static ResultParser GPSD_RESULT_PARSER = null;
	private static int GPSD_TCP_PORT = 0; // default in GPSD of 2947
	private static long GRC_PROBE_INTERVAL_SECONDS;
	private final String HTTP_USER_AGENT = "Mozilla/5.0";
	private final String HTTP_ACCEPT_LANGUAGE = "en-US,en;q=0.5";
	private static String GRC_URL = null;


	
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

		} catch(Exception e) {
			log.error("Incorrect Handover configuration options", e);
			//throw e;
		}
		
		/*try {
			GPSD_RESULT_PARSER = new ResultParser();
			GPSD_CONN = new GPSdEndpoint("127.0.0.1", GPSD_TCP_PORT, GPSD_RESULT_PARSER);
			GPSD_CONN.start();
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
		}*/

		// Periodically ask GRC for a handover decision
		grcProbeMgr = new ScheduledThreadPoolExecutor(1);
		grcProbe = new GRCProbe();
		grcProbeMgr.scheduleAtFixedRate(grcProbe, 10, GRC_PROBE_INTERVAL_SECONDS, TimeUnit.SECONDS);
		return;
	}
	 
	// HTTP GET request
	/*private void sendGet() throws Exception {
  
		URL obj = new URL(GRC_URL);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		// optional default is GET
		con.setRequestMethod("GET");
 
		// add request header
		con.setRequestProperty("User-Agent", HTTP_USER_AGENT);
 
		int responseCode = con.getResponseCode();
		log.debug("Sending 'GET' request to URL: " + obj.getRef());
		log.debug("Got Response Code: " + responseCode);
 
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		log.debug("Got GRC Response: " + response.toString());
		
		return;
	}*/
 
	// HTTP POST request
	private String askGRC() {
		try {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(GRC_URL);
			List <NameValuePair> nvps = new ArrayList <NameValuePair>();
			
			//we need a list of all interface names, this shouldn't be hard coded
			ArrayList<String> interface_names = new ArrayList<String>();
			interface_names.add("wmx0");
			interface_names.add("eth0");
			interface_names.add("wlan0");
			
			//create a JSON object for every interface name, containing collected details
			ArrayList<JSONObject> ifaceObjs = new ArrayList<JSONObject>();
			for(String iface : interface_names){
				JSONObject obj = new JSONObject();
				obj.put("name", iface);
				obj.put("signalDbm", -60);
				ifaceObjs.add(obj);
			}
			
			//create a JSON array containing all of our JSON objects, and include it in our request
			JSONArray jInterfacesArray = new JSONArray(ifaceObjs);
			nvps.add(new BasicNameValuePair("interfaces", jInterfacesArray.toString()));
			
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
			log.debug("Switching to: " + responseObj.getString("interface"));
		} catch(Exception e) {
			e.printStackTrace();
		} 
		return null;
	}
	
	class GRCProbe implements Runnable {
		@Override
		public void run() {
			log.info("Asking GRC (Cybertiger) for a handover decision...");
			// do it here
			String response = askGRC();
			return;
		}
	} // END GRCProbe Class
} // END Handover Module