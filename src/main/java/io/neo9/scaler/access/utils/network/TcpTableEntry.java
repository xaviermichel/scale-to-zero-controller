package io.neo9.scaler.access.utils.network;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TcpTableEntry {
	private String slot;
	private String localAddress;
	private String localPort;
	private String remoteAddress;
	private String remotePort;
	private String state;
	private String uid;
	private String inode;
}
