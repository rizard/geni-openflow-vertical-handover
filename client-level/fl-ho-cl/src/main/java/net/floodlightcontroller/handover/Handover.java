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

	private static ScheduledThreadPoolExecutor lrcProbeMgr;
	private static Runnable lrcProbe;
	
	private static GPSdEndpoint GPSD_CONN = null;
	private static ResultParser GPSD_RESULT_PARSER = null;
	private static int GPSD_TCP_PORT = 0; // default in GPSD of 2947
	private static long LRC_PROBE_INTERVAL_SECONDS;
	private final String HTTP_USER_AGENT = "Mozilla/5.0";
	private final String HTTP_ACCEPT_LANGUAGE = "en-US,en;q=0.5";
	private static String LRC_URL = null;


	
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
			LRC_PROBE_INTERVAL_SECONDS = Long.parseLong(configOptions.get("lrc-probe-interval-seconds"));
			GPSD_TCP_PORT = Integer.parseInt(configOptions.get("gpsd-tcp-port"));
			LRC_URL = configOptions.get("lrc-url");

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

		// Periodically ask LRC for a handover decision
		lrcProbeMgr = new ScheduledThreadPoolExecutor(1);
		lrcProbe = new LRCProbe();
		lrcProbeMgr.scheduleAtFixedRate(lrcProbe, 10, LRC_PROBE_INTERVAL_SECONDS, TimeUnit.SECONDS);
		return;
	}
	 
	// HTTP GET request
	/*private void sendGet() throws Exception {
  
		URL obj = new URL(LRC_URL);
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

		log.debug("Got LRC Response: " + response.toString());
		
		return;
	}*/
 
	// HTTP POST request
	private String askLRC() {
		try {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(LRC_URL);
			List <NameValuePair> nvps = new ArrayList <NameValuePair>();
			nvps.add(new BasicNameValuePair("interfaces", "wlan0"));
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			ResponseHandler<String> responseHandler = new ResponseHandler<String>(){

				@Override
				public String handleResponse(HttpResponse arg0)
						throws ClientProtocolException, IOException {
					int status = arg0.getStatusLine().getStatusCode();
					if(status >= 200 && status < 300){
						HttpEntity entity = arg0.getEntity();
						return entity != null ? EntityUtils.toString(entity) : null;
					}
					return null;
				}
				
			};
			String responseBody = httpclient.execute(httpPost, responseHandler);
			log.debug("Response: " + responseBody);
			//CloseableHttpResponse response2 = httpclient.execute(httpPost);

			/*try {
			    //System.out.println(response2.getStatusLine());
			    HttpEntity entity2 = response2.getEntity();
			    // do something useful with the response body
			    // and ensure it is fully consumed
			    EntityUtils.consume(entity2);
			    log.debug("Response: " + response2.);
			} finally {
			    response2.close();
			}*/
			
			
			/*URL obj = new URL(LRC_URL);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();

			// add request header
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", HTTP_USER_AGENT);
			con.setRequestProperty("Accept-Language", HTTP_ACCEPT_LANGUAGE);

			String urlParameters = "?interfaces=wlan0";

			// Send post request
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			int responseCode = con.getResponseCode();
			log.debug("Sending 'POST' request to URL: " + obj.getRef() + " with Params: " + urlParameters);
			log.debug("Got Response Code: " + responseCode);


			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			log.debug("Got LRC Response: " + response.toString());
			return response.toString();*/
		} catch(Exception e) {
			e.printStackTrace();
		} 
		return null;
	}
	
	class LRCProbe implements Runnable {
		@Override
		public void run() {
			log.info("Asking LRC (Cybertiger) for a handover decision...");
			// do it here
			String response = askLRC();
			return;
		}
	} // END LRCProbe Class
} // END Handover Module
