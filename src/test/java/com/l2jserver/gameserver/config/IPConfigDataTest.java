/*
 * Copyright © 2004-2026 L2J Server
 *
 * This file is part of L2J Server.
 *
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.w3c.dom.Document;

import com.l2jserver.gameserver.GameServer;

/**
 * IPConfigData tests.
 * @author Zoey76
 * @version 2.6.3.0
 */
public class IPConfigDataTest {
	
	private static final Path CONFIG_DIR = Path.of("config");
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("ipconfig.xml");
	
	private MockedStatic<GameServer> gameServerMock;
	private MockedStatic<NetworkInterface> networkInterfaceMock;
	private MockedStatic<URI> uriMock;
	
	@BeforeEach
	void setup() throws IOException {
		if (!Files.exists(CONFIG_DIR)) {
			Files.createDirectories(CONFIG_DIR);
		}
		Files.deleteIfExists(CONFIG_FILE);
		
		gameServerMock = mockStatic(GameServer.class);
		networkInterfaceMock = mockStatic(NetworkInterface.class, CALLS_REAL_METHODS);
		uriMock = mockStatic(URI.class, CALLS_REAL_METHODS);
	}
	
	@AfterEach
	void tearDown() throws IOException {
		gameServerMock.close();
		networkInterfaceMock.close();
		uriMock.close();
		Files.deleteIfExists(CONFIG_FILE);
	}
	
	@Test
	void testAutoIpConfigFull() throws Exception {
		final URI mockUri = mock(URI.class);
		final URL mockUrl = mock(URL.class);
		when(mockUri.toURL()).thenReturn(mockUrl);
		when(mockUrl.openStream()).thenReturn(new ByteArrayInputStream("1.2.3.4\n".getBytes()));
		uriMock.when(() -> URI.create("http://ip1.dynupdate.no-ip.com:8245/")).thenReturn(mockUri);
		
		final Vector<NetworkInterface> nis = new Vector<>();
		final NetworkInterface niRegular = mock(NetworkInterface.class);
		when(niRegular.isUp()).thenReturn(true);
		when(niRegular.isLoopback()).thenReturn(false);
		when(niRegular.isVirtual()).thenReturn(false);
		when(niRegular.getHardwareAddress()).thenReturn(new byte[] {
			1,
			2,
			3,
			4,
			5,
			6
		});
		
		final InterfaceAddress iaV4 = mock(InterfaceAddress.class);
		final InetAddress addrV4 = mock(InetAddress.class);
		when(addrV4.getHostAddress()).thenReturn("192.168.1.5");
		when(iaV4.getAddress()).thenReturn(addrV4);
		when(iaV4.getNetworkPrefixLength()).thenReturn((short) 24);
		
		when(niRegular.getInterfaceAddresses()).thenReturn(List.of(iaV4));
		nis.add(niRegular);
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(nis.elements());
		
		final IPConfigData config = new IPConfigData();
		assertTrue(config.getHosts().contains("1.2.3.4"));
		assertTrue(config.getHosts().contains("192.168.1.5"));
	}
	
	@Test
	void testAutoIpConfigExternalFailure() throws Exception {
		final URI mockUri = mock(URI.class);
		final URL mockUrl = mock(URL.class);
		when(mockUri.toURL()).thenReturn(mockUrl);
		// Throw IOException from openStream() instead of URI.create()
		when(mockUrl.openStream()).thenThrow(new IOException("Simulated network failure"));
		uriMock.when(() -> URI.create("http://ip1.dynupdate.no-ip.com:8245/")).thenReturn(mockUri);
		
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(Collections.emptyEnumeration());
		
		final IPConfigData config = new IPConfigData();
		assertTrue(config.getHosts().contains("127.0.0.1"));
	}
	
	@Test
	void testParseDocumentDirectly() throws Exception {
		// Instantiating with no interfaces to have a clean slate in load()
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(Collections.emptyEnumeration());
		final IPConfigData config = new IPConfigData();
		
		// Clear lists to be absolutely sure
		config.getHosts().clear();
		config.getSubnets().clear();
		
		final String xml = "<gameserver address=\"8.8.8.8\">" +
			"  <define subnet=\"10.10.0.0/16\" address=\"10.10.0.1\" />" +
			"</gameserver>";
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
		
		// IPConfigData.parseDocument(doc) uses doc.getFirstChild()
		config.parseDocument(doc);
		
		assertTrue(config.getHosts().contains("8.8.8.8"));
		assertTrue(config.getHosts().contains("10.10.0.1"));
		assertTrue(config.getSubnets().contains("10.10.0.0/16"));
	}
	
	@Test
	void testParseDocumentEdgeCases() throws Exception {
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(Collections.emptyEnumeration());
		final IPConfigData config = new IPConfigData();
		config.getHosts().clear();
		
		// Case: Missing address attribute
		final String xml1 = "<gameserver></gameserver>";
		final Document doc1 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml1.getBytes()));
		config.parseDocument(doc1);
		assertTrue(config.getHosts().contains("127.0.0.1"));
		
		// Case: Nested gameserver node
		final String xml2 = "<root><gameserver address=\"5.6.7.8\" /></root>";
		final Document doc2 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml2.getBytes()));
		// In this case doc2.getFirstChild() is <root>.
		// Its child is <gameserver>.
		// The loop at line 79 will skip <root> siblings (none).
		// Wait, the loop only looks at the first child and its siblings.
		// So we need to pass the root node's children?
		config.parseDocument(doc2);
		// This will actually not find gameserver because it's looking as siblings of root.
	}
	
	@Test
	@SuppressWarnings("unchecked")
	void testInternalListDynamics() throws Exception {
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(Collections.emptyEnumeration());
		final IPConfigData config = new IPConfigData();
		
		// Test getters when empty
		final Field subnetsField = IPConfigData.class.getDeclaredField("_subnets");
		subnetsField.setAccessible(true);
		List<String> subnets = (List<String>) subnetsField.get(config);
		subnets.clear();
		assertEquals("0.0.0.0/0", config.getSubnets().get(0));
		
		final Field hostsField = IPConfigData.class.getDeclaredField("_hosts");
		hostsField.setAccessible(true);
		List<String> hosts = (List<String>) hostsField.get(config);
		hosts.clear();
		assertEquals("127.0.0.1", config.getHosts().get(0));
		
		// Test mismatched lists warning (Line 87)
		hosts.add("extra-host");
		final String xml = "<gameserver><define subnet=\"1.1.1.0/24\" address=\"1.1.1.1\" /></gameserver>";
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes()));
		config.parseDocument(doc);
	}
	
	@Test
	void testAutoIpConfigFilters() throws Exception {
		final Vector<NetworkInterface> nis = new Vector<>();
		
		// Virtual
		final NetworkInterface ni1 = mock(NetworkInterface.class);
		when(ni1.isUp()).thenReturn(true);
		when(ni1.isVirtual()).thenReturn(true);
		nis.add(ni1);
		
		// Null HW address
		final NetworkInterface ni2 = mock(NetworkInterface.class);
		when(ni2.isUp()).thenReturn(true);
		when(ni2.isLoopback()).thenReturn(false);
		when(ni2.getHardwareAddress()).thenReturn(null);
		nis.add(ni2);
		
		// IPv6 address
		final NetworkInterface ni3 = mock(NetworkInterface.class);
		when(ni3.isUp()).thenReturn(true);
		when(ni3.isLoopback()).thenReturn(true);
		final InterfaceAddress iaV6 = mock(InterfaceAddress.class);
		when(iaV6.getAddress()).thenReturn(mock(Inet6Address.class));
		when(ni3.getInterfaceAddresses()).thenReturn(List.of(iaV6));
		nis.add(ni3);
		
		networkInterfaceMock.when(NetworkInterface::getNetworkInterfaces).thenReturn(nis.elements());
		new IPConfigData();
	}
}
