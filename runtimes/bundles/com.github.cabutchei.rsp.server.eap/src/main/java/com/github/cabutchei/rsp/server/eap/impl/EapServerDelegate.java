package com.github.cabutchei.rsp.server.eap.impl;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.dao.CommandLineDetails;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ListServerActionResponse;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.StartServerResponse;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.eclipse.jdt.JDTPlugin;
import com.github.cabutchei.rsp.eclipse.wst.model.delegate.AbstractWstServerDelegate;
import com.github.cabutchei.rsp.launching.java.ILaunchModes;
import com.github.cabutchei.rsp.launching.utils.LaunchingDebugProperties;
import com.github.cabutchei.rsp.server.eap.servertype.EapContextRootSupport;
import com.github.cabutchei.rsp.server.eap.servertype.publishing.EapPublishController;
import com.github.cabutchei.rsp.server.eap.servertype.IEapServerAttributes;
import com.github.cabutchei.rsp.server.eap.servertype.actions.EapShowInBrowserActionHandler;
import com.github.cabutchei.rsp.server.eap.adapter.IJBossRuntimeAdapter;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import org.jboss.ide.eclipse.as.core.server.internal.JBossServer;
import org.jboss.ide.eclipse.as.core.util.ServerUtil;

public class EapServerDelegate extends AbstractWstServerDelegate implements IServerDelegate, IModuleStateProvider {
	private static final String DEBUG_PORT_KEY = "com.github.cabutchei.rsp.server.eap.debugPort";

	private EapPublishController publishController;

	public EapServerDelegate(IServer server) {
		super(server);
	}

	@Override
	public void setDependentDefaults(IServerWorkingCopy server) {
		// no-op
	}

	@Override
	public IStatus publish(int publishRequestType) {
		return publish(publishRequestType, new NullProgressMonitor());
	}

	@Override
	public IStatus publish(int publishRequestType, IProgressMonitor monitor) {
		IStatus status = super.publish(publishRequestType, monitor);
		if (status != null && status.isOK()) {
			// getPublishController().publishFinished(publishRequestType,
			// 		getServerPublishModel().getDeployableStatesWithOptions(),
			// 		getServerRunState());
		}
		return status;
	}

	@Override
	public ServerState getServerState() {
		return super.getServerState();
	}

	@Override
	public ListServerActionResponse listServerActions() {
		ListServerActionResponse ret = new ListServerActionResponse();
		ret.setStatus(StatusConverter.convert(Status.OK_STATUS));
		List<ServerActionWorkflow> workflows = new ArrayList<>();
		if (getServerRunState() == ServerManagementAPIConstants.STATE_STARTED) {
			ServerActionWorkflow showInBrowser = new EapShowInBrowserActionHandler(this).getInitialWorkflow();
			if (showInBrowser != null) {
				workflows.add(showInBrowser);
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
		if (EapShowInBrowserActionHandler.ACTION_ID.equals(req.getActionId())) {
			return new EapShowInBrowserActionHandler(this).handle(req);
		}
		return super.executeServerAction(req);
	}

	@Override
	public void updateServer(IServer dummyServer, UpdateServerResponse resp) {
		// noop
	}

	@Override
	public void updateServer(IServerWorkingCopy workingCopy, UpdateServerResponse resp) {
		if (workingCopy == null) {
			return;
		}
		String pattern = workingCopy.getAttribute(IEapServerAttributes.RESTART_FILE_PATTERN, (String) null);
		boolean useDefault = shouldUseDefaultRestartPattern(pattern);
		workingCopy.setAttribute(IEapServerAttributes.USE_DEFAULT_RESTART_FILE_PATTERN, useDefault);
		if (useDefault) {
			workingCopy.setAttribute(IEapServerAttributes.RESTART_FILE_PATTERN,
					IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT);
		}
		String vmInstallLocation = workingCopy.getAttribute(IEapServerAttributes.VM_INSTALL_PATH,
				IEapServerAttributes.VM_INSTALL_PATH_DEFAULT);
		try {
			IRuntime runtime = workingCopy.getRuntime();
			if (runtime == null) {
				throw new CoreException(new Status(IStatus.ERROR, "com.github.cabutchei.rsp.server.eap",
						"Server runtime is null"));
			}
			IRuntimeWorkingCopy runtimeWc = runtime.isWorkingCopy() ? (IRuntimeWorkingCopy) runtime : runtime.createWorkingCopy();
			if (runtimeWc == null) {
				throw new CoreException(new Status(IStatus.ERROR, "com.github.cabutchei.rsp.server.eap",
						"Server runtime is null"));
			}
			String runtimeLocation = workingCopy.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null);
			if (runtimeLocation != null && !runtimeLocation.isBlank()) {
				runtimeWc.setLocation(new Path(runtimeLocation));
			}
			IJBossRuntimeAdapter jbossRuntime = (IJBossRuntimeAdapter) runtimeWc.loadAdapter(IJBossRuntimeAdapter.class);
			if (jbossRuntime == null) {
				throw new CoreException(new Status(IStatus.ERROR, "com.github.cabutchei.rsp.server.eap",
						"Unable to adapt WST runtime to IJBossRuntimeAdapter"));
			}
			String configFile = workingCopy.getAttribute(IEapServerAttributes.CONFIG_FILE, IEapServerAttributes.CONFIG_FILE_DEFAULT);
			jbossRuntime.setConfigurationFile(configFile);
			jbossRuntime.setVM(JDTPlugin.getVMService().findOrCreateVMInstall(vmInstallLocation));
			workingCopy.setRuntime(runtimeWc);
		} catch (CoreException e) {
			if (resp != null && resp.getValidation() != null) {
				resp.getValidation().setStatus(StatusConverter.convert(e.getStatus()));
			}
		}
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
				Integer debugPort = findFreePort();
				addDebugDetails(debugPort, details);
				EapServerAccess.getControllableServerBehavior(getServer()).putSharedData(DEBUG_PORT_KEY, debugPort);
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
	protected void handleLaunchReady(ILaunch launch) {
		addLaunchStreamListeners(launch, false, null);
	}

	@Override
	public IStatus stopModule(DeployableReference ref) {
		getWstServerControl().stopModule(ref);
		return Status.OK_STATUS;
	}

	private EapPublishController getPublishController() {
		if (publishController == null) {
			publishController = new EapPublishController(getServer());
		}
		return publishController;
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

	private Integer findFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			return -1;
		}
	}

	private boolean shouldUseDefaultRestartPattern(String pattern) {
		if (pattern == null) {
			return true;
		}
		return IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT.equals(pattern);
	}

	public String getShowInBrowserHost() {
		org.eclipse.wst.server.core.IServer wtpServer = getServer().getAdapter(org.eclipse.wst.server.core.IServer.class);
		if (wtpServer != null && wtpServer.getHost() != null && !wtpServer.getHost().isBlank()) {
			return wtpServer.getHost();
		}
		return getServer().getAttribute(IEapServerAttributes.HOSTNAME, IEapServerAttributes.HOSTNAME_DEFAULT);
	}

	public int getShowInBrowserHttpPort() {
		JBossServer jbossServer = getJBossServerAdapter();
		if (jbossServer != null) {
			return jbossServer.getJBossWebPort();
		}
		String configured = getServer().getAttribute(IEapServerAttributes.WEB_PORT, IEapServerAttributes.WEB_PORT_DEFAULT);
		try {
			return Integer.parseInt(configured);
		} catch (NumberFormatException e) {
			return 8080;
		}
	}

	public String getShowInBrowserConfigurationFile() {
		String configuredFile = getServer().getAttribute(IEapServerAttributes.CONFIG_FILE, IEapServerAttributes.CONFIG_FILE_DEFAULT);
		if (configuredFile == null || configuredFile.isBlank()) {
			return null;
		}
		File configured = new File(configuredFile);
		if (configured.isAbsolute()) {
			return configured.getAbsolutePath();
		}

		JBossServer jbossServer = getJBossServerAdapter();
		if (jbossServer != null) {
			String configDirectory = jbossServer.getConfigDirectory();
			if (configDirectory != null && !configDirectory.isBlank()) {
				return new File(configDirectory, configuredFile).getAbsolutePath();
			}
		}

		String serverHome = getRuntimeLocation();
		if (serverHome == null || serverHome.isBlank()) {
			serverHome = getServer().getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null);
		}
		if (serverHome == null || serverHome.isBlank()) {
			String homeFile = getServer().getAttribute(DefaultServerAttributes.SERVER_HOME_FILE, (String) null);
			if (homeFile != null && !homeFile.isBlank()) {
				serverHome = new File(homeFile).getParent();
			}
		}
		if (serverHome == null || serverHome.isBlank()) {
			return null;
		}

		String baseDirectory = getServer().getAttribute(IEapServerAttributes.BASE_DIRECTORY, IEapServerAttributes.BASE_DIRECTORY_DEFAULT);
		File configurationDir = new File(new File(serverHome, baseDirectory), "configuration");
		return new File(configurationDir, configuredFile).getAbsolutePath();
	}

	public String getDeploymentStrategy() {
		return "appendDeploymentNameRemoveSuffix";
	}

	public String[] getDeploymentUrls(String strat, String baseUrl, String deployableOutputName, DeployableState ds) {
		return new EapContextRootSupport().getDeploymentUrls(strat, baseUrl, deployableOutputName, ds);
	}

	private JBossServer getJBossServerAdapter() {
		org.eclipse.wst.server.core.IServer wtpServer = getServer().getAdapter(org.eclipse.wst.server.core.IServer.class);
		if (wtpServer == null) {
			return null;
		}
		try {
			return (JBossServer) ServerUtil.checkedGetServerAdapter(wtpServer, JBossServer.class);
		} catch (org.eclipse.core.runtime.CoreException e) {
			return null;
		}
	}

	private String getRuntimeLocation() {
		IRuntime runtime = getServer().getRuntime();
		if (runtime == null || runtime.getLocation() == null) {
			return null;
		}
		return runtime.getLocation().toOSString();
	}
}
