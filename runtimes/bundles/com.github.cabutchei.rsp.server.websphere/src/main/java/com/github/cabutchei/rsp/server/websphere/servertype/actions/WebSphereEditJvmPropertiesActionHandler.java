package com.github.cabutchei.rsp.server.websphere.servertype.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponseItem;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.foundation.core.launchers.StreamGobbler;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

import com.github.cabutchei.rsp.server.servertype.impl.IWebSphereServerAttributes;
import com.github.cabutchei.rsp.server.websphere.impl.AbstractWebSphereServerDelegate;
import com.github.cabutchei.rsp.server.websphere.impl.WebSphereWstServerAccess;

public class WebSphereEditJvmPropertiesActionHandler {
	public static final String ACTION_ID = "WebSphereEditJvmPropertiesActionHandler.actionId";
	public static final String ACTION_LABEL = "Edit JVM Properties...";
	public static final String WORKFLOW_ITEM_TYPE = "workflow.webview.open";
	public static final String DATA_KEY = "websphere.jvm.properties";
	public static final String VIEW_TYPE = "websphere.jvmProperties";
	private static final String SCRIPT_RESOURCE = "/wsadmin/jvm_properties.py";
	private static final String MARKER_START = "RSP_JSON_START";
	private static final String MARKER_END = "RSP_JSON_END";
	private static final long WSADMIN_TIMEOUT_MS = 60000;

	private static final Gson GSON = new Gson();

	private final AbstractWebSphereServerDelegate delegate;

	public WebSphereEditJvmPropertiesActionHandler(AbstractWebSphereServerDelegate delegate) {
		this.delegate = delegate;
	}

	public ServerActionWorkflow getInitialWorkflow() {
		WorkflowResponse workflow = new WorkflowResponse();
		ServerActionWorkflow action = new ServerActionWorkflow(ACTION_ID, ACTION_LABEL, workflow);

		try {
			List<Map<String, Object>> properties = loadSystemProperties();
			WorkflowResponseItem item = new WorkflowResponseItem();
			item.setItemType(WORKFLOW_ITEM_TYPE);
			item.setId(DATA_KEY);
			item.setLabel(ACTION_LABEL);
			item.setContent(GSON.toJson(properties));
			Map<String, String> props = new HashMap<>();
			props.put("viewType", VIEW_TYPE);
			props.put("title", ACTION_LABEL);
			props.put("dataKey", DATA_KEY);
			item.setProperties(props);
			workflow.setItems(List.of(item));
			workflow.setStatus(StatusConverter.convert(
					new Status(IStatus.INFO, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID, ACTION_LABEL)));
			return action;
		} catch (Exception e) {
			workflow.setItems(new ArrayList<>());
			workflow.setStatus(StatusConverter.convert(
					new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
							"Failed to load JVM properties", e)));
			return action;
		}
	}

	public WorkflowResponse handle(ServerActionRequest req) {
		if (req == null || req.getData() == null || req.getData().get(DATA_KEY) == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}
		Object payload = req.getData().get(DATA_KEY);
		try {
			List<Map<String, Object>> properties = decodeProperties(payload);
			updateSystemProperties(properties);
			return AbstractServerDelegate.okWorkflowResponse();
		} catch (Exception e) {
			WorkflowResponse resp = new WorkflowResponse();
			resp.setItems(new ArrayList<>());
			resp.setStatus(StatusConverter.convert(
					new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
							"Failed to update JVM properties", e)));
			return resp;
		}
	}

	private List<Map<String, Object>> decodeProperties(Object payload) {
		if (payload == null) {
			return new ArrayList<>();
		}
		String json = payload instanceof String ? (String) payload : GSON.toJson(payload);
		List<Map<String, Object>> list = GSON.fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
		return list == null ? new ArrayList<>() : list;
	}

	private List<Map<String, Object>> loadSystemProperties() throws Exception {
		try {
			Map<String, String> props = WebSphereWstServerAccess.getSystemProperties(delegate.getServer());
			return convertPropsToList(props);
		} catch (CoreException e) {
			String json = runWsadmin("list", null);
			if (json == null || json.isEmpty()) {
				return new ArrayList<>();
			}
			List<Map<String, Object>> list =
					GSON.fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
			return list == null ? new ArrayList<>() : list;
		}
	}

	private void updateSystemProperties(List<Map<String, Object>> properties) throws Exception {
		try {
			Map<String, String> map = convertListToProps(properties);
			WebSphereWstServerAccess.setSystemProperties(delegate.getServer(), map);
			return;
		} catch (CoreException e) {
			// fall back to wsadmin
		}

		Path jsonFile = Files.createTempFile("wsadmin-jvm-props", ".json");
		try {
			String json = GSON.toJson(properties == null ? new ArrayList<>() : properties);
			Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
			runWsadmin("apply", jsonFile);
		} finally {
			try {
				Files.deleteIfExists(jsonFile);
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private String runWsadmin(String action, Path jsonFile) throws Exception {
		Path script = extractScript();
		try {
			WsadminResult result = execWsadmin(script, action, jsonFile);
			if (result.exitCode != 0) {
				throw new CoreException(new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
						"wsadmin failed: " + result.stderr));
			}
			String json = extractJson(result.stdout);
			if (json == null) {
				throw new CoreException(new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
						"wsadmin returned unexpected output: " + result.stdout));
			}
			return json;
		} finally {
			try {
				Files.deleteIfExists(script);
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private List<Map<String, Object>> convertPropsToList(Map<String, String> props) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (props == null) {
			return result;
		}
		for (Map.Entry<String, String> entry : props.entrySet()) {
			Map<String, Object> map = new HashMap<>();
			map.put("name", entry.getKey() == null ? "" : entry.getKey());
			map.put("value", entry.getValue() == null ? "" : entry.getValue());
			map.put("required", Boolean.FALSE);
			result.add(map);
		}
		return result;
	}

	private Map<String, String> convertListToProps(List<Map<String, Object>> properties) {
		Map<String, String> map = new HashMap<>();
		if (properties == null) {
			return map;
		}
		for (Map<String, Object> entry : properties) {
			if (entry == null) {
				continue;
			}
			Object nameObj = entry.get("name");
			String name = nameObj == null ? "" : nameObj.toString();
			if (name.trim().isEmpty()) {
				continue;
			}
			Object valObj = entry.get("value");
			String value = valObj == null ? "" : valObj.toString();
			map.put(name, value);
		}
		return map;
	}

	private Path extractScript() throws IOException {
		Path script = Files.createTempFile("wsadmin-jvm-props", ".py");
		try (InputStream in = WebSphereEditJvmPropertiesActionHandler.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (in == null) {
				throw new IOException("Missing wsadmin script resource: " + SCRIPT_RESOURCE);
			}
			Files.write(script, in.readAllBytes());
		}
		return script;
	}

	private WsadminResult execWsadmin(Path script, String action, Path jsonFile) throws Exception {
		String wsadmin = resolveWsadminExecutable();
		String profile = resolveProfileName();
		String serverName = resolveServerName();

		List<String> cmd = new ArrayList<>();
		if (isWindows()) {
			cmd.add("cmd");
			cmd.add("/c");
		}
		cmd.add(wsadmin);
		cmd.add("-conntype");
		cmd.add("NONE");
		cmd.add("-lang");
		cmd.add("jython");
		cmd.add("-quiet");
		if (profile != null && !profile.isEmpty()) {
			cmd.add("-profileName");
			cmd.add(profile);
		}
		cmd.add("-f");
		cmd.add(script.toString());
		cmd.add(action);
		cmd.add(serverName == null ? "" : serverName);
		cmd.add("");
		if (jsonFile != null) {
			cmd.add(jsonFile.toString());
		}

		ProcessBuilder pb = new ProcessBuilder(cmd);
		File binDir = new File(wsadmin).getParentFile();
		if (binDir != null) {
			pb.directory(binDir);
		}
		Map<String, String> env = pb.environment();
		String home = resolveServerHome();
		if (home != null) {
			env.put("WAS_HOME", home);
		}

		Process process = pb.start();
		StreamGobbler out = new StreamGobbler(process.getInputStream());
		StreamGobbler err = new StreamGobbler(process.getErrorStream());
		out.start();
		err.start();
		boolean finished = process.waitFor(WSADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly();
			return new WsadminResult(1, "", "wsadmin timed out after " + WSADMIN_TIMEOUT_MS + "ms");
		}
		String stdout = String.join("\n", out.getOutput());
		String stderr = String.join("\n", err.getOutput());
		return new WsadminResult(process.exitValue(), stdout, stderr);
	}

	private String resolveWsadminExecutable() throws CoreException {
		String home = resolveServerHome();
		if (home == null || home.isEmpty()) {
			throw new CoreException(new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
					"WebSphere home is missing; cannot locate wsadmin"));
		}
		String exe = isWindows() ? "wsadmin.bat" : "wsadmin.sh";
		File file = new File(new File(home, "bin"), exe);
		if (!file.isFile()) {
			throw new CoreException(new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
					"wsadmin not found at " + file.getAbsolutePath()));
		}
		return file.getAbsolutePath();
	}

	private String resolveServerHome() {
		String home = delegate.getServerHome();
		if (home != null && !home.isEmpty()) {
			return home;
		}
		try {
			return WebSphereWstServerAccess.getWebSphereInstallPath(delegate.getServer());
		} catch (CoreException e) {
			return null;
		}
	}

	private String resolveProfileName() {
		String profile = delegate.getServer().getAttribute(IWebSphereServerAttributes.WEBSPHERE_PROFILE, "");
		if (profile != null && !profile.isEmpty()) {
			return profile;
		}
		try {
			return WebSphereWstServerAccess.getProfileName(delegate.getServer());
		} catch (CoreException e) {
			return null;
		}
	}

	private String resolveServerName() {
		try {
			return WebSphereWstServerAccess.getBaseServerName(delegate.getServer());
		} catch (CoreException e) {
			return delegate.getServer().getName();
		}
	}

	private String extractJson(String output) {
		if (output == null) {
			return null;
		}
		int start = output.indexOf(MARKER_START);
		if (start < 0) {
			return null;
		}
		int end = output.indexOf(MARKER_END, start);
		if (end < 0) {
			return null;
		}
		String chunk = output.substring(start + MARKER_START.length(), end);
		return chunk.trim();
	}

	private boolean isWindows() {
		String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().contains("win");
	}

	private static class WsadminResult {
		final int exitCode;
		final String stdout;
		final String stderr;

		WsadminResult(int exitCode, String stdout, String stderr) {
			this.exitCode = exitCode;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}
}
