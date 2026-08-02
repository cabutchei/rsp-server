package com.github.cabutchei.rsp.server.eap.servertype.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.github.cabutchei.rsp.server.eap.impl.EapServerDelegate;

public class EapShowInBrowserActionHandlerTest {

	@Test
	public void testConfiguredProtocolPortsFallsBackToNamedHttpsSocketBinding() throws Exception {
		File configFile = writeConfig(
				"<subsystem xmlns=\"urn:jboss:domain:undertow:3.1\">"
						+ "<server name=\"default-server\">"
						+ "<http-listener name=\"default\" socket-binding=\"http\"/>"
						+ "</server>"
						+ "</subsystem>"
						+ "<socket-binding-group name=\"standard-sockets\" default-interface=\"public\">"
						+ "<socket-binding name=\"http\" port=\"${jboss.http.port:8081}\"/>"
						+ "<socket-binding name=\"https\" port=\"${jboss.https.port:8443}\"/>"
						+ "</socket-binding-group>");

		Map<String, List<Integer>> ports = getConfiguredProtocolPorts(configFile);

		assertNotNull(ports);
		assertEquals(List.of(8081), ports.get("http"));
		assertEquals(List.of(8443), ports.get("https"));
	}

	@Test
	public void testConfiguredProtocolPortsUsesListenersAndAppliesPortOffset() throws Exception {
		File configFile = writeConfig(
				"<subsystem xmlns=\"urn:jboss:domain:undertow:3.1\">"
						+ "<server name=\"default-server\">"
						+ "<http-listener name=\"default\" socket-binding=\"http\"/>"
						+ "<https-listener name=\"default-https\" socket-binding=\"https\"/>"
						+ "<https-listener name=\"management-https\" socket-binding=\"https-fixed\"/>"
						+ "</server>"
						+ "</subsystem>"
						+ "<socket-binding-group name=\"standard-sockets\" default-interface=\"public\" "
						+ "port-offset=\"${jboss.socket.binding.port-offset:100}\">"
						+ "<socket-binding name=\"http\" port=\"${jboss.http.port:8080}\"/>"
						+ "<socket-binding name=\"https\" port=\"${jboss.https.port:8443}\"/>"
						+ "<socket-binding name=\"https-fixed\" port=\"9443\" fixed-port=\"true\"/>"
						+ "</socket-binding-group>");

		Map<String, List<Integer>> ports = getConfiguredProtocolPorts(configFile);

		assertNotNull(ports);
		assertEquals(List.of(8180), ports.get("http"));
		assertEquals(List.of(8543, 9443), ports.get("https"));
	}

	@Test
	public void testConfiguredProtocolPortsDetectsLegacyConnectorsAndDeduplicates() throws Exception {
		File configFile = writeConfig(
				"<subsystem xmlns=\"urn:jboss:domain:web:2.2\">"
						+ "<connector name=\"http\" protocol=\"HTTP/1.1\" socket-binding=\"http\"/>"
						+ "<connector name=\"https\" scheme=\"https\" secure=\"true\" socket-binding=\"https\"/>"
						+ "<connector name=\"duplicate-http\" protocol=\"HTTP/1.1\" socket-binding=\"http\"/>"
						+ "<connector name=\"ajp\" protocol=\"AJP/1.3\" socket-binding=\"ajp\"/>"
						+ "</subsystem>"
						+ "<socket-binding-group name=\"standard-sockets\" default-interface=\"public\">"
						+ "<socket-binding name=\"http\" port=\"8080\"/>"
						+ "<socket-binding name=\"https\" port=\"8443\"/>"
						+ "<socket-binding name=\"ajp\" port=\"8009\"/>"
						+ "</socket-binding-group>");

		Map<String, List<Integer>> ports = getConfiguredProtocolPorts(configFile);

		assertNotNull(ports);
		assertEquals(List.of(8080), ports.get("http"));
		assertEquals(List.of(8443), ports.get("https"));
		assertTrue(!ports.containsKey("ajp"));
	}

	@Test
	public void testGetBaseUrlsUsesConfiguredHttpAndHttpsPortsAndNormalizesIpv6Host() throws Exception {
		File configFile = writeConfig(
				"<subsystem xmlns=\"urn:jboss:domain:undertow:3.1\">"
						+ "<server name=\"default-server\">"
						+ "<http-listener name=\"default\" socket-binding=\"http\"/>"
						+ "<https-listener name=\"default-https\" socket-binding=\"https\"/>"
						+ "</server>"
						+ "</subsystem>"
						+ "<socket-binding-group name=\"standard-sockets\" default-interface=\"public\">"
						+ "<socket-binding name=\"http\" port=\"8081\"/>"
						+ "<socket-binding name=\"https\" port=\"8443\"/>"
						+ "</socket-binding-group>");
		EapServerDelegate delegate = mock(EapServerDelegate.class);
		when(delegate.getShowInBrowserHost()).thenReturn("fe80::1");
		when(delegate.getShowInBrowserConfigurationFile()).thenReturn(configFile.getAbsolutePath());

		TestableEapShowInBrowserActionHandler handler = new TestableEapShowInBrowserActionHandler(delegate);

		assertEquals(List.of("http://[fe80::1]:8081", "https://[fe80::1]:8443"), handler.getBaseUrlsInternal());
	}

	@Test
	public void testGetBaseUrlsFallsBackToDelegateHttpPortWhenConfigIsMissing() {
		EapServerDelegate delegate = mock(EapServerDelegate.class);
		when(delegate.getShowInBrowserHost()).thenReturn("localhost");
		when(delegate.getShowInBrowserConfigurationFile()).thenReturn("/tmp/does-not-exist.xml");
		when(delegate.getShowInBrowserHttpPort()).thenReturn(8080);

		TestableEapShowInBrowserActionHandler handler = new TestableEapShowInBrowserActionHandler(delegate);

		assertEquals(List.of("http://localhost:8080"), handler.getBaseUrlsInternal());
	}

	private static File writeConfig(String innerXml) throws Exception {
		File configFile = Files.createTempFile(EapShowInBrowserActionHandlerTest.class.getSimpleName(), ".xml").toFile();
		configFile.deleteOnExit();
		Files.writeString(configFile.toPath(), "<server>" + innerXml + "</server>");
		return configFile;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, List<Integer>> getConfiguredProtocolPorts(File configFile) throws Exception {
		Method method = EapShowInBrowserActionHandler.class.getDeclaredMethod("getConfiguredProtocolPorts", File.class);
		method.setAccessible(true);
		return (Map<String, List<Integer>>) method.invoke(null, configFile);
	}

	private static class TestableEapShowInBrowserActionHandler extends EapShowInBrowserActionHandler {
		TestableEapShowInBrowserActionHandler(EapServerDelegate delegate) {
			super(delegate);
		}

		private List<String> getBaseUrlsInternal() {
			return super.getBaseUrls();
		}
	}
}
