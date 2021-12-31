package io.neo9.scaler.access.utils.network;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
// https://www.kernel.org/doc/Documentation/networking/proc_net_tcp.txt
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
