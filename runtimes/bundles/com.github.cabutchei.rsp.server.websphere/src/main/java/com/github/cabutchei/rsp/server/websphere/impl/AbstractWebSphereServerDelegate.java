package com.github.cabutchei.rsp.server.websphere.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.CommandLineDetails;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ListServerActionResponse;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.StartServerResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.eclipse.debug.core.model.IProcess;
import com.github.cabutchei.rsp.launching.java.ILaunchModes;
import com.github.cabutchei.rsp.launching.utils.LaunchingDebugProperties;
import com.github.cabutchei.rsp.eclipse.wst.model.delegate.AbstractWstServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.CreateServerValidation;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerAttributes;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

import com.github.cabutchei.rsp.server.servertype.impl.IWebSphereServerAttributes;
import com.github.cabutchei.rsp.server.websphere.servertype.actions.WebSphereEditJvmPropertiesActionHandler;
import com.github.cabutchei.rsp.server.websphere.servertype.actions.WebSphereEditServerConfigurationActionHandler;
import com.github.cabutchei.rsp.server.websphere.servertype.actions.WebSphereOpenAdminConsoleActionHandler;

public abstract class AbstractWebSphereServerDelegate extends AbstractWstServerDelegate implements IModuleStateProvider {
	private static final long LAUNCH_WAIT_TIMEOUT_MS = 5000;
	private static final long DEBUG_PORT_WAIT_TIMEOUT_MS = 15000;
	private static final String START_PROGRAM_ARGS = "${server.websphere.profile}";
	private static final String STOP_PROGRAM_ARGS = "${server.websphere.profile} --stop";
	private static final String VM_ARGS =
			"-javaagent:${server.home.dir}/bin/tools/ws-javaagent.jar -Djava.awt.headless=true -Djdk.attach.allowAttachSelf=true";

	private volatile CompletableFuture<Integer> debugPortFuture = new CompletableFuture<>();
	private ILaunch startLaunch;

	protected AbstractWebSphereServerDelegate(IServer server) {
		super(server);
	}

	protected ILaunch getStartLaunch() {
		return startLaunch;
	}

	protected void setStartLaunch(ILaunch launch) {
		this.startLaunch = launch;
	}

	@Override
	public CreateServerValidation validate() {
		IStatus status;
		try {
			status = WebSphereWstServerAccess.validateWebSphereProfileExists(getServer());
			return new CreateServerValidation(status, List.of(IWebSphereServerAttributes.WEBSPHERE_PROFILE));
		} catch (CoreException e) {
			status = new Status(IStatus.ERROR, Activator.BUNDLE_ID, "Error validating WebSphere profile existence: " + e.getMessage(), e);
			return new CreateServerValidation(status, List.of(IWebSphereServerAttributes.WEBSPHERE_PROFILE));
		}
	}

	@Override
	public void setDependentDefaults(IServerWorkingCopy server) {
		setJavaLaunchDependentDefaults(server);
	}

	@Override
	public StartServerResponse start(String mode) {
		IStatus stat = canStart(mode);
		com.github.cabutchei.rsp.api.dao.Status s;
		if (!stat.isOK()) {
			s = StatusConverter.convert(stat);
			return new StartServerResponse(s, null);
		}
		CommandLineDetails details = new CommandLineDetails();
		try {
			if (ILaunchModes.DEBUG.equals(mode)) {
				int debugPort = WebSphereWstServerAccess.getDebugPort(getServer());
				addDebugDetails(debugPort, details);
			}
			prepareLaunchAttacher();
			getWstServerControl().startAsync(mode);
		} catch (CoreException e) {
			resetLaunchAttacher();
			s = StatusConverter.convert(e.getStatus());
			return new StartServerResponse(s, null);
		}
		return new StartServerResponse(StatusConverter.convert(Status.OK_STATUS), details);
	}

	@Override
	public IStatus stop(boolean force) {
		resetLaunchAttacher();
		resetDebugPortFuture();
		return super.stop(force);
	}

	protected void setJavaLaunchDependentDefaults(IServerWorkingCopy server) {
		server.setAttribute(com.github.cabutchei.rsp.server.generic.servertype.GenericServerType.LAUNCH_OVERRIDE_BOOLEAN, false);
		server.setAttribute(com.github.cabutchei.rsp.server.generic.servertype.GenericServerType.LAUNCH_OVERRIDE_PROGRAM_ARGS,
				emptyStringDefault(resolveTemplate(server, START_PROGRAM_ARGS)));
		server.setAttribute(com.github.cabutchei.rsp.server.generic.servertype.GenericServerType.JAVA_LAUNCH_OVERRIDE_VM_ARGS,
				emptyStringDefault(resolveTemplate(server, VM_ARGS)));
		server.setAttribute(com.github.cabutchei.rsp.server.generic.servertype.GenericServerType.LAUNCH_OVERRIDE_SHUTDOWN_PROGRAM_ARGS,
				emptyStringDefault(resolveTemplate(server, STOP_PROGRAM_ARGS)));
		server.setAttribute(com.github.cabutchei.rsp.server.generic.servertype.GenericServerType.JAVA_LAUNCH_OVERRIDE_SHUTDOWN_VM_ARGS,
				emptyStringDefault(resolveTemplate(server, VM_ARGS)));
	}

	protected String resolveTemplate(IServerWorkingCopy server, String template) {
		if (template == null) {
			return null;
		}
		String result = template;
		String home = getRuntimeLocation(server);
		if (home == null) {
			home = server.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null);
		}
		if (home == null) {
			home = server.getAttribute(DefaultServerAttributes.SERVER_HOME_FILE, (String) null);
		}
		String profile = server.getAttribute(IWebSphereServerAttributes.WEBSPHERE_PROFILE, "AppSrv01");
		result = result.replace("${server.home.dir}", home == null ? "" : home);
		result = result.replace("${server.websphere.profile}", profile == null ? "" : profile);
		return result;
	}

	@Override
	public ListServerActionResponse listServerActions() {
		ListServerActionResponse ret = new ListServerActionResponse();
		ret.setStatus(StatusConverter.convert(Status.OK_STATUS));
		List<ServerActionWorkflow> workflows = new ArrayList<>();
		ServerActionWorkflow edit = new WebSphereEditServerConfigurationActionHandler(this).getInitialWorkflow();
		if (edit != null) {
			workflows.add(edit);
		}
		ServerActionWorkflow jvmProps = new WebSphereEditJvmPropertiesActionHandler(this).getInitialWorkflow();
		if (jvmProps != null) {
			workflows.add(jvmProps);
		}
		if (getServerRunState() == ServerManagementAPIConstants.STATE_STARTED) {
			ServerActionWorkflow adminConsole = new WebSphereOpenAdminConsoleActionHandler(this).getInitialWorkflow();
			if (adminConsole != null) {
				workflows.add(adminConsole);
			}
		}
		ret.setWorkflows(workflows);
		return ret;
	}

	@Override
	public WorkflowResponse executeServerAction(ServerActionRequest req) {
		if (req == null) {
			return cancelWorkflowResponse();
		}
		if (WebSphereEditServerConfigurationActionHandler.ACTION_ID.equals(req.getActionId())) {
			return new WebSphereEditServerConfigurationActionHandler(this).handle(req);
		}
		if (WebSphereEditJvmPropertiesActionHandler.ACTION_ID.equals(req.getActionId())) {
			return new WebSphereEditJvmPropertiesActionHandler(this).handle(req);
		}
		if (WebSphereOpenAdminConsoleActionHandler.ACTION_ID.equals(req.getActionId())) {
			return new WebSphereOpenAdminConsoleActionHandler(this).handle(req);
		}
		return cancelWorkflowResponse();
	}

	public String[] getConfigurationFilePaths() {
		String home = getServerHome(getServer());
		if (home == null) {
			return new String[0];
		}
		String profile = getServer().getAttribute(IWebSphereServerAttributes.WEBSPHERE_PROFILE, (String) null);
		if (profile == null || profile.isEmpty()) {
			return new String[0];
		}
		File file = new File(home, "profiles/" + profile + "/config/server.xml");
		if (file.isFile()) {
			return new String[] { file.getAbsolutePath() };
		}
		return new String[0];
	}

	protected String getServerHome(IServer server) {
		String runtimeHome = getRuntimeLocation(server);
		if (runtimeHome != null) {
			return runtimeHome;
		}
		String home = server.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null);
		if (home != null) {
			return home;
		}
		String homeFile = server.getAttribute(DefaultServerAttributes.SERVER_HOME_FILE, (String) null);
		if (homeFile != null) {
			File file = new File(homeFile);
			return file.getParent();
		}
		return null;
	}

	public String getServerHome() {
		return getServerHome(getServer());
	}

	public String getDeploymentStrategy() {
		return "appendDeploymentNameRemoveSuffix";
	}

	public abstract String[] getDeploymentUrls(String strat, String baseUrl, String deployableOutputName,
			DeployableState ds);

	@Override
	protected void setServerState(int state) {
		// no-op: WST handles server state notifications
	}

	@Override
	protected void setServerState(int state, boolean fire) {
		// no-op: WST handles server state notifications
	}

	@Override
	protected void handleLaunchReady(ILaunch launch) {
		setStartLaunch(launch);
		addLaunchStreamListeners(launch, true, this::handleDebugPort);
	}

	private CommandLineDetails awaitLaunchDetails(String mode) {
		ILaunch launch = awaitLaunchWithProcess(LAUNCH_WAIT_TIMEOUT_MS);
		if (launch == null) {
			return null;
		}
		IProcess[] processes = launch.getProcesses();
		if (processes == null || processes.length == 0) {
			return null;
		}
		String cmdline = null;
		for (int i = 0; i < processes.length; i++) {
			String candidate = processes[i].getAttribute(IProcess.ATTR_CMDLINE);
			if (candidate != null && !candidate.isEmpty()) {
				cmdline = candidate;
				break;
			}
		}
		if (cmdline == null) {
			return null;
		}
		CommandLineDetails details = new CommandLineDetails();
		details.setCmdLine(com.github.cabutchei.rsp.eclipse.debug.core.ArgumentUtils.parseArguments(cmdline));
		addDebugDetails(mode, details);
		return details;
	}

	private void addDebugDetails(int port, CommandLineDetails details) {
		if (port == 0) {
			return;
		}
		Map<String, String> props = details.getProperties();
		if (props == null) {
			props = new HashMap<>();
			details.setProperties(props);
		}
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_TYPE, LaunchingDebugProperties.DEBUG_DETAILS_TYPE_JAVA);
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_HOST, "localhost");
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_PORT, Integer.toString(port));
	}

	private void addDebugDetails(String mode, CommandLineDetails details) {
		if (!ILaunchModes.DEBUG.equals(mode)) {
			return;
		}
		Integer port = awaitDebugPort();
		if (port == null) {
			return;
		}
		Map<String, String> props = details.getProperties();
		if (props == null) {
			props = new HashMap<>();
			details.setProperties(props);
		}
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_TYPE, LaunchingDebugProperties.DEBUG_DETAILS_TYPE_JAVA);
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_HOST, "localhost");
		props.put(LaunchingDebugProperties.DEBUG_DETAILS_PORT, Integer.toString(port));
	}

	private Integer awaitDebugPort() {
		CompletableFuture<Integer> future = debugPortFuture;
		if (future == null) {
			return null;
		}
		try {
			return future.get(DEBUG_PORT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private void handleDebugPort(int port) {
		if (port <= 0) {
			return;
		}
		CompletableFuture<Integer> future = debugPortFuture;
		if (future != null && !future.isDone()) {
			future.complete(port);
		}
	}

	private void resetDebugPortFuture() {
		debugPortFuture = new CompletableFuture<>();
	}

	private String emptyStringDefault(String s) {
		return s == null || s.isEmpty() ? "" : s;
	}

	private String getRuntimeLocation(IServerAttributes server) {
		if (server == null) {
			return null;
		}
		IRuntime runtime = server.getRuntime();
		if (runtime == null || runtime.getLocation() == null) {
			return null;
		}
		return runtime.getLocation().toOSString();
	}
}
