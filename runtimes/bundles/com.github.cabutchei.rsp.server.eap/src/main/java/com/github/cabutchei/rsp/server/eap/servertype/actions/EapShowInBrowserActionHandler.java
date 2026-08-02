/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.eap.servertype.actions;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.WorkflowPromptDetails;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponseItem;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.eap.impl.EapServerDelegate;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

public class EapShowInBrowserActionHandler {
	public static final String ACTION_ID = "ShowInBrowserActionHandler.actionId";
	public static final String ACTION_LABEL = "Show in browser...";
	public static final String ACTION_SELECTED_PROMPT_ID = "ShowInBrowserActionHandler.selection.id";
	public static final String ACTION_SELECTED_PROMPT_LABEL =
			"Which deployment do you want to show in the web browser?";

	private final EapServerDelegate delegate;

	public EapShowInBrowserActionHandler(EapServerDelegate delegate) {
		this.delegate = delegate;
	}

	public ServerActionWorkflow getInitialWorkflow() {
		List<String> choices = getDeploymentChoices();
		if (choices.isEmpty()) {
			return null;
		}

		WorkflowResponse workflow = new WorkflowResponse();
		workflow.setStatus(StatusConverter.convert(
				new Status(IStatus.INFO, "com.github.cabutchei.rsp.server.eap", ACTION_LABEL)));
		ServerActionWorkflow action = new ServerActionWorkflow(ACTION_ID, ACTION_LABEL, workflow);

		WorkflowPromptDetails prompt = new WorkflowPromptDetails();
		prompt.setResponseSecret(false);
		prompt.setResponseType(ServerManagementAPIConstants.ATTR_TYPE_STRING);
		prompt.setValidResponses(choices);

		WorkflowResponseItem item = new WorkflowResponseItem();
		item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_PROMPT_SMALL);
		item.setId(ACTION_SELECTED_PROMPT_ID);
		item.setLabel(ACTION_SELECTED_PROMPT_LABEL);
		item.setPrompt(prompt);

		workflow.setItems(List.of(item));
		return action;
	}

	protected List<String> getDeploymentChoices() {
		LinkedHashSet<String> urls = new LinkedHashSet<>();
		List<String> baseUrls = getBaseUrls();
		urls.addAll(baseUrls);
		for (DeployableState ds : getDeployableStates()) {
			for (String baseUrl : baseUrls) {
				String[] deploymentUrls = getDeploymentUrls(ds, baseUrl);
				if (deploymentUrls != null) {
					urls.addAll(Arrays.asList(deploymentUrls));
				}
			}
		}
		return new ArrayList<>(urls);
	}

	protected List<String> getBaseUrls() {
		String host = delegate.getShowInBrowserHost();
		if (host == null || host.isBlank()) {
			return List.of();
		}

		LinkedHashSet<String> urls = new LinkedHashSet<>();
		Map<String, List<Integer>> configuredPorts = getConfiguredProtocolPorts();
		if (!configuredPorts.isEmpty()) {
			addUrls(urls, "http", host, configuredPorts.get("http"));
			addUrls(urls, "https", host, configuredPorts.get("https"));
			return new ArrayList<>(urls);
		}

		int httpPort = delegate.getShowInBrowserHttpPort();
		if (httpPort > 0) {
			urls.add(createUrl("http", host, httpPort));
		}
		return new ArrayList<>(urls);
	}

	private void addUrls(LinkedHashSet<String> urls, String protocol, String host, List<Integer> ports) {
		if (ports == null) {
			return;
		}
		for (Integer port : ports) {
			if (port != null && port.intValue() > 0) {
				urls.add(createUrl(protocol, host, port.intValue()));
			}
		}
	}

	private String createUrl(String protocol, String host, int port) {
		String normalizedHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
		return protocol + "://" + normalizedHost + ":" + port;
	}

	protected List<DeployableState> getDeployableStates() {
		return delegate.getServerPublishModel().getDeployableStatesWithOptions();
	}

	protected String[] getDeploymentUrls(DeployableState ds, String baseUrl) {
		return delegate.getDeploymentUrls(delegate.getDeploymentStrategy(), baseUrl, getOutputName(ds.getReference()), ds);
	}

	private String getOutputName(DeployableReference ref) {
		Map<String, Object> options = ref.getOptions();
		String def = null;
		if (ref.getPath() != null) {
			def = new File(ref.getPath()).getName();
		}
		String key = ServerManagementAPIConstants.DEPLOYMENT_OPTION_OUTPUT_NAME;
		if (options != null && options.get(key) != null) {
			return (String) options.get(key);
		}
		return def;
	}

	public WorkflowResponse handle(ServerActionRequest req) {
		if (req == null || req.getData() == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}
		String choice = (String) req.getData().get(ACTION_SELECTED_PROMPT_ID);
		if (choice == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}
		String url = findUrlFromChoice(choice);
		if (url == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}

		WorkflowResponseItem item = new WorkflowResponseItem();
		item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_BROWSER);
		item.setLabel("Open the following url: " + url);
		item.setContent(url);

		WorkflowResponse resp = new WorkflowResponse();
		resp.setItems(List.of(item));
		resp.setStatus(StatusConverter.convert(Status.OK_STATUS));
		return resp;
	}

	private String findUrlFromChoice(String choice) {
		String lower = choice.toLowerCase();
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return choice;
		}
		return null;
	}

	private Map<String, List<Integer>> getConfiguredProtocolPorts() {
		File configFile = getConfigurationFile();
		if (configFile == null || !configFile.isFile()) {
			return Map.of();
		}
		return getConfiguredProtocolPorts(configFile);
	}

	static Map<String, List<Integer>> getConfiguredProtocolPorts(File configFile) {
		try (InputStream is = new FileInputStream(configFile)) {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			Document document = factory.newDocumentBuilder().parse(is);
			document.getDocumentElement().normalize();
			Map<String, Integer> socketBindings = getSocketBindings(document);
			List<Integer> httpPorts = new ArrayList<>();
			List<Integer> httpsPorts = new ArrayList<>();
			collectListenerPorts(document, socketBindings, httpPorts, httpsPorts);
			addNamedSocketBindingFallback(httpPorts, socketBindings, "http");
			addNamedSocketBindingFallback(httpsPorts, socketBindings, "https");
			LinkedHashMap<String, List<Integer>> ret = new LinkedHashMap<>();
			if (!httpPorts.isEmpty()) {
				ret.put("http", dedupePorts(httpPorts));
			}
			if (!httpsPorts.isEmpty()) {
				ret.put("https", dedupePorts(httpsPorts));
			}
			return ret;
		} catch (Exception e) {
			return Map.of();
		}
	}

	private static List<Integer> dedupePorts(List<Integer> ports) {
		return new ArrayList<>(new LinkedHashSet<>(ports));
	}

	private File getConfigurationFile() {
		String configurationFile = delegate.getShowInBrowserConfigurationFile();
		if (configurationFile == null || configurationFile.isBlank()) {
			return null;
		}
		return new File(configurationFile);
	}

	private static Map<String, Integer> getSocketBindings(Document document) {
		LinkedHashMap<String, Integer> bindings = new LinkedHashMap<>();
		Integer portOffset = getPortOffset(document);
		for (Element element : getElements(document)) {
			if (!"socket-binding".equals(localName(element))) {
				continue;
			}
			String name = element.getAttribute("name");
			Integer port = parsePort(element.getAttribute("port"));
			if (name == null || name.isBlank() || port == null || port.intValue() <= 0) {
				continue;
			}
			boolean fixedPort = Boolean.parseBoolean(element.getAttribute("fixed-port"));
			if (!fixedPort && portOffset != null) {
				port = Integer.valueOf(port.intValue() + portOffset.intValue());
			}
			bindings.put(name, port);
		}
		return bindings;
	}

	private static Integer getPortOffset(Document document) {
		for (Element element : getElements(document)) {
			if ("socket-binding-group".equals(localName(element))) {
				Integer portOffset = parsePort(element.getAttribute("port-offset"));
				return portOffset == null ? Integer.valueOf(0) : portOffset;
			}
		}
		return Integer.valueOf(0);
	}

	private static void collectListenerPorts(Document document, Map<String, Integer> socketBindings, List<Integer> httpPorts,
			List<Integer> httpsPorts) {
		for (Element element : getElements(document)) {
			String localName = localName(element);
			if ("http-listener".equals(localName)) {
				addPort(httpPorts, element, socketBindings);
			} else if ("https-listener".equals(localName)) {
				addPort(httpsPorts, element, socketBindings);
			} else if ("connector".equals(localName)) {
				addConnectorPort(element, socketBindings, httpPorts, httpsPorts);
			}
		}
	}

	private static void addNamedSocketBindingFallback(List<Integer> target, Map<String, Integer> socketBindings,
			String bindingName) {
		if (!target.isEmpty()) {
			return;
		}
		Integer port = socketBindings.get(bindingName);
		if (port != null && port.intValue() > 0) {
			target.add(port);
		}
	}

	private static void addConnectorPort(Element element, Map<String, Integer> socketBindings, List<Integer> httpPorts,
			List<Integer> httpsPorts) {
		String scheme = element.getAttribute("scheme");
		String name = element.getAttribute("name");
		String protocol = element.getAttribute("protocol");
		boolean secure = Boolean.parseBoolean(element.getAttribute("secure"));
		boolean https = secure || "https".equalsIgnoreCase(scheme)
				|| (name != null && name.toLowerCase().contains("https"));
		boolean http = "http".equalsIgnoreCase(scheme)
				|| (protocol != null && protocol.toUpperCase().contains("HTTP"));
		if (https) {
			addPort(httpsPorts, element, socketBindings);
		} else if (http) {
			addPort(httpPorts, element, socketBindings);
		}
	}

	private static void addPort(List<Integer> target, Element element, Map<String, Integer> socketBindings) {
		Integer directPort = parsePort(element.getAttribute("port"));
		if (directPort != null && directPort.intValue() > 0) {
			target.add(directPort);
			return;
		}
		String socketBinding = element.getAttribute("socket-binding");
		Integer resolved = socketBindings.get(socketBinding);
		if (resolved != null && resolved.intValue() > 0) {
			target.add(resolved);
		}
	}

	private static Integer parsePort(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
			String inner = trimmed.substring(2, trimmed.length() - 1);
			int colon = inner.indexOf(':');
			if (colon != -1 && colon + 1 < inner.length()) {
				return parsePort(inner.substring(colon + 1));
			}
			return null;
		}
		try {
			return Integer.valueOf(Integer.parseInt(trimmed));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static List<Element> getElements(Document document) {
		NodeList nodes = document.getElementsByTagName("*");
		List<Element> elements = new ArrayList<>(nodes.getLength());
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node instanceof Element) {
				elements.add((Element) node);
			}
		}
		return elements;
	}

	private static String localName(Element element) {
		return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
	}
}
