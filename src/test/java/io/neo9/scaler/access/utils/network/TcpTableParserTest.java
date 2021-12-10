package io.neo9.scaler.access.utils.network;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TcpTableParserTest {

	@Test
	public void tcpTableShouldBeParsed() {
		// given
		String commandOutput = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode                                \n"
				+ "   0: 00000000:3A99 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1337        0 50347 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   1: 00000000:3A9E 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1337        0 50372 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   2: 00000000:3AAD 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1337        0 49274 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   3: 00000000:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 49192 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   4: 00000000:3AF2 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1337        0 49269 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   5: 0100007F:3A98 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1337        0 49257 1 0000000000000000 100 0 0 10 0                     \n"
				+ "   6: 0100007F:3A98 0100007F:C066 01 00000000:00000000 00:00000000 00000000  1337        0 51136 1 0000000000000000 20 4 23 10 -1                    \n"
				+ "   7: 0100007F:E20E 0100007F:3AAC 01 00000000:00000000 00:00000000 00000000  1337        0 50442 1 0000000000000000 20 4 30 10 -1                    \n"
				+ "   8: 0644720A:EBB6 5D3F720A:3AA4 01 00000000:00000000 02:000002B4 00000000  1337        0 661501 2 0000000000000000 38 4 24 10 -1                   \n"
				+ "   9: 0100007F:3A99 0644720A:81D0 01 00000000:00000000 00:00000000 00000000  1337        0 689617 1 0000000000000000 20 0 0 10 -1                    \n"
				+ "  10: 0644720A:81D0 4A34720A:2382 01 00000000:00000000 00:00000000 00000000     0        0 690468 1 0000000000000000 20 0 0 10 -1                    \n"
				+ "  11: 0100007F:E22C 0100007F:3AAC 01 00000000:00000000 00:00000000 00000000  1337        0 50563 1 0000000000000000 20 4 30 10 -1                    \n"
				+ "  12: 0100007F:D346 0100007F:3AF2 01 00000000:00000000 02:00000B82 00000000  1337        0 51558 2 0000000000000000 20 4 24 10 -1                    \n"
				+ "  13: 0100007F:3AF2 0100007F:D346 01 00000000:00000000 00:00000000 00000000  1337        0 51559 1 0000000000000000 20 4 25 10 -1                    \n"
				+ "  14: 0644720A:A4CC 1346720A:2382 01 00000000:00000000 00:00000000 00000000  1337        0 689618 1 0000000000000000 20 0 0 10 -1                    \n"
				+ "  15: 0100007F:C066 0100007F:3A98 01 00000000:00000000 00:00000000 00000000  1337        0 51561 1 0000000000000000 20 4 22 10 -1                    \n"
				+ "  16: 0644720A:88EC 5D3F720A:3AA4 01 00000000:00000000 02:00000353 00000000  1337        0 680706 2 0000000000000000 20 4 29 10 -1";

		// when
		List<TcpTableEntry> tcpTableEntries = TcpTableParser.parseTCPTable(commandOutput);

		// then
		assertThat(tcpTableEntries).hasSize(17);
	}

	@Test
	public void lineShouldBeWellDecoded() {
		// given
		String simpleLine = "  header line \n" +
				"   8: 0644720A:EBB6 5D3F720A:3AA4 01 00000000:00000000 02:000002B4 00000000  1337        0 661501 2 0000000000000000 38 4 24 10 -1                   \n";

		// when
		List<TcpTableEntry> tcpTableEntries = TcpTableParser.parseTCPTable(simpleLine);

		// then
		assertThat(tcpTableEntries).hasSize(1);
		TcpTableEntry tcpTableEntry = tcpTableEntries.get(0);
		assertThat(tcpTableEntry.getLocalAddress()).isEqualTo("10.114.68.6");
		assertThat(tcpTableEntry.getLocalPort()).isEqualTo("60342");
		assertThat(tcpTableEntry.getRemoteAddress()).isEqualTo("10.114.63.93");
		assertThat(tcpTableEntry.getRemotePort()).isEqualTo("15012");
	}
}
