package net.floodlightcontroller.dhcpserver;

import java.util.ArrayList;
import java.util.Arrays;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.MACAddress;

import java.lang.String;

/**
 * The class representing a DHCP Binding -- MAC and IP.
 * It also contains important information regarding the lease status
 * --active
 * --inactive
 * the lease type of the binding
 * --dynamic
 * --fixed/static
 * and the lease times
 * --start time in seconds
 * --duration in seconds
 * 
 * @author Ryan Izard (rizard@g.clemson.edu)
 */
public class DHCPBinding {
	
	public static final int IP_ADDRESS_LENGTH = 4;
	public static final int IP_ADDRESS_STRING_LENGTH_MAX = 15;
	public static final int IP_ADDRESS_STRING_LENGTH_MIN = 7;
	public static final int MAC_ADDRESS_LENGTH = (int) Ethernet.DATALAYER_ADDRESS_LENGTH;
	public static final int MAC_ADDRESS_STRING_LENGTH = 17;
	
	private ArrayList<byte[]> MACS;
	private byte[] CURRENT_MAC;
	private byte[] IP = new byte[IP_ADDRESS_LENGTH];
	private boolean LEASE_STATUS;
	private boolean PERMANENT_LEASE;
	
	private ArrayList<String> FLOWS;
	private String HOME_SWITCH;
	
	private long LEASE_START_TIME_SECONDS;
	private long LEASE_DURATION_SECONDS;
	
	protected DHCPBinding(byte[] ip, ArrayList<byte[]> macs) {
		this.MACS = new ArrayList<byte[]>();
		for (int i = 0; i < macs.size(); i++) {
			this.MACS.add(Arrays.copyOf(macs.get(i), MAC_ADDRESS_LENGTH));
		}
		this.CURRENT_MAC = null;
		this.setIPv4Addresss(ip);
		this.setLeaseStatus(false);
		FLOWS = new ArrayList<String>();
		this.FLOWS.add("");
		this.HOME_SWITCH = "";
	}
	protected DHCPBinding(byte[] ip, byte[] mac) {
		this.MACS = new ArrayList<byte[]>();
		this.MACS.add(Arrays.copyOf(mac, MAC_ADDRESS_LENGTH));
		this.CURRENT_MAC = Arrays.copyOf(mac, MAC_ADDRESS_LENGTH);
		this.setIPv4Addresss(ip);
		this.setLeaseStatus(false);
		FLOWS = new ArrayList<String>();
		this.FLOWS.add("");
		this.HOME_SWITCH = "";
	}
	
	public String getHomeSwitch() {
		return HOME_SWITCH;
	}
	
	public void setHomeSwitch(String dpid) {
		HOME_SWITCH = dpid;
	}
	
	public String getFlowName() {
		if (FLOWS.size() == 0) {
			return null;
		} else {
			return FLOWS.get(0);
		}
	}
	
	public void setFlowName(String flowName) {
		FLOWS.clear();
		FLOWS.add(flowName);
	}
	
	public byte[] getIPv4AddressBytes() {
		return IP;
	}
	
	public String getIPv4AddresString() {
		return IPv4.fromIPv4Address(IPv4.toIPv4Address(IP));
	}
	
	public byte[] getMACAddressBytes(int index) {
		return MACS.get(index);
	}
	
	public byte[] getCurrentMACAddressBytes() {
		return CURRENT_MAC;
	}
	
	public void setCurrentMACAddressBytes(byte[] mac) {
		CURRENT_MAC = Arrays.copyOf(mac, MAC_ADDRESS_LENGTH);
	}
	
	public String getCurrentMACAddressString() {
		return MACAddress.valueOf(CURRENT_MAC).toString();
	}
	
	public String getMACAddressString(int index) {
		return MACAddress.valueOf(MACS.get(index)).toString();
	}
	public ArrayList<byte[]> getMACAddresses() {
		return MACS;
	}
	public String getMACAddressesString() {
		String macString = "";
		for (byte[] mac : MACS) {
			if (macString.isEmpty()) {
				macString = MACAddress.valueOf(mac).toString();
			} else {
				macString = macString + ", " + MACAddress.valueOf(mac).toString();
			}
		}
		return macString;
	}
	
	public boolean isMACMemberOf(byte[] mac) {
		for (byte[] test : MACS) {
			if (Arrays.equals(test, mac)) {
				return true;
			}
		}
		return false;
	}
	public int getNumberOfMACAddresses() {
		return MACS.size();
	}
	
	private void setIPv4Addresss(byte[] ip) {
		IP = Arrays.copyOf(ip, IP_ADDRESS_LENGTH); 
	}
	
	public void addMACAddress(byte[] mac) {
		if (!MACS.contains(mac)) {
			MACS.add(Arrays.copyOf(mac, MAC_ADDRESS_LENGTH));
		}
		CURRENT_MAC = Arrays.copyOf(mac, MAC_ADDRESS_LENGTH);
	}
	public void addMACAddress(String mac) {
		if (!MACS.contains(Ethernet.toMACAddress(mac))) {
			MACS.add(Ethernet.toMACAddress(mac));
		}
		CURRENT_MAC = Ethernet.toMACAddress(mac);
	}
	public void addMACAddresses(ArrayList<byte[]> macs) {
		for (byte[] mac : macs) {
			MACS.add(Arrays.copyOf(mac, MAC_ADDRESS_LENGTH));
		}
	}
	public void setMACAddresses(ArrayList<byte[]> macs) {
		clearMACAddresses();
		addMACAddresses(macs);
	}
	public void clearMACAddresses() {
		MACS.clear();
		CURRENT_MAC = null;
	}
	
	public boolean isActiveLease() {
		return LEASE_STATUS;
	}
	
	public void setStaticIPLease(boolean staticIP) {
		PERMANENT_LEASE = staticIP;
	}
	
	public boolean isStaticIPLease() {
		return PERMANENT_LEASE;
	}
	
	public void setLeaseStatus(boolean status) {
		LEASE_STATUS = status;
	}
	
	public boolean isLeaseExpired() {
		long currentTime = System.currentTimeMillis();
		if ((currentTime / 1000) >= (LEASE_START_TIME_SECONDS + LEASE_DURATION_SECONDS)) {
			return true;
		} else {
			return false;
		}
	}
	
	protected void setLeaseStartTimeSeconds() {
		LEASE_START_TIME_SECONDS = System.currentTimeMillis() / 1000;
	}
	
	protected void setLeaseDurationSeconds(long time) {
		LEASE_DURATION_SECONDS = time;
	}
	
	protected void clearLeaseTimes() {
		LEASE_START_TIME_SECONDS = 0;
		LEASE_DURATION_SECONDS = 0;
	}
	
	protected boolean cancelLease() {
		this.clearLeaseTimes();
		this.setLeaseStatus(false);
		return true;
	}
}
