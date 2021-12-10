package io.neo9.scaler.access.utils.network;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.net.InetAddresses;
import io.neo9.scaler.access.utils.network.TcpTableEntry.TcpTableEntryBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class TcpTableParser {

	// Pattern to parse /proc/net/tcp
	final private Pattern fieldsPattern = Pattern.compile("^\\s*(\\d+): ([0-9A-F]+):(....) ([0-9A-F]+):(....) (..) (?:\\S+ ){3}\\s*(\\d+)\\s+\\d+\\s+(\\d+).*$");

	public static List<TcpTableEntry> parseTCPTable(String tcpTableOutput) {
		List<TcpTableEntry> netstatEntries = new ArrayList<>();
		boolean isFirstLine = true;
		for (String line : tcpTableOutput.split("\n")) {
			if (isFirstLine) {
				isFirstLine = false;
				continue;
			}

			TcpTableEntry.TcpTableEntryBuilder builder = new TcpTableEntryBuilder();

			Matcher match = fieldsPattern.matcher(line);
			match.lookingAt();

			builder.slot(match.group(1));
			builder.localAddress(fromLittleEndianHexValue(match.group(2)));
			builder.localPort(hexStringToBase10String(match.group(3)));
			builder.remoteAddress(fromLittleEndianHexValue(match.group(4)));
			builder.remotePort(hexStringToBase10String(match.group(5)));
			builder.state(hexStringToBase10String(match.group(6)));
			builder.uid(match.group(7));
			builder.inode(match.group(8));

			netstatEntries.add(builder.build());
		}
		return netstatEntries;
	}

	public String fromLittleEndianHexValue(String hexValue) {
		try {
			return InetAddresses.fromLittleEndianByteArray(hexStringToByteArray(hexValue)).getHostAddress();
		}
		catch (UnknownHostException e) {
			log.warn("Failed to parse address : {}", hexValue, e);
			return "";
		}
	}

	public String hexStringToBase10String(String hexValue) {
		return String.valueOf(Integer.parseInt(hexValue, 16));
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}

}
