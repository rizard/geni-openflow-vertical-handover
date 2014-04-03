package net.floodlightcontroller.sos;

/**
 * This enum defines the types of packets the controller can receive wrt SOS.
 * Under normal circumstances, the controller should only receive packets
 * matching:
 * 		NOT_ASSOCIATED_WITH_ACTIVE_SOS_CONNECTION
 * 		ASSOCIATED_DST_AGENT_TO_DST_CLIENT
 * 
 * The former is for packets detected that should be sent to a nearby SOS agent
 * to facilitate a new SOS connection. The latter is when a destination agent
 * sends its first packet to the intended destination client. There are no flows
 * present for this packet, since we need to learn the L4 port number of the
 * destination agent. Once this port number is learned, flows should be inserted,
 * and all subsequent packets in either direction should match flows.
 * 
 * Any other value within this enum should only be associated with a PACKET_IN packet
 * to the controller if an error has occurred. These values are returned from the
 * method isPacketMemberOfActiveConnection() within class SOSActiveConnections.
 * 
 * @author rizard
 *
 */

public enum SOSConnectionPacketMembership {
		NOT_ASSOCIATED_WITH_ACTIVE_SOS_CONNECTION,
		ASSOCIATED_SRC_CLIENT_TO_SRC_AGENT,
		ASSOCIATED_SRC_AGENT_TO_SRC_CLIENT,
		ASSOCIATED_SRC_AGENT_TO_DST_AGENT,
		ASSOCIATED_DST_AGENT_TO_SRC_AGENT,
		ASSOCIATED_DST_CLIENT_TO_DST_AGENT,
		ASSOCIATED_DST_AGENT_TO_DST_CLIENT
}
