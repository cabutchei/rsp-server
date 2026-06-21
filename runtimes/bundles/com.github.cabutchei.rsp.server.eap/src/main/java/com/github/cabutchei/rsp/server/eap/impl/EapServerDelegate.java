package com.github.cabutchei.rsp.server.eap.impl;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.dao.CommandLineDetails;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.StartServerResponse;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.eclipse.jdt.JDTPlugin;
import com.github.cabutchei.rsp.eclipse.wst.model.delegate.AbstractWstServerDelegate;
import com.github.cabutchei.rsp.launching.java.ILaunchModes;
import com.github.cabutchei.rsp.launching.utils.LaunchingDebugProperties;
import com.github.cabutchei.rsp.server.eap.servertype.publishing.EapPublishController;
import com.github.cabutchei.rsp.server.eap.servertype.IEapServerAttributes;
import com.github.cabutchei.rsp.server.eap.adapter.IJBossRuntimeAdapter;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

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
		IStatus status = super.publish(publishRequestType);
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
}
