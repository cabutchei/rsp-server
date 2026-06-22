package com.github.cabutchei.rsp.eclipse.wst.core;

import java.util.Map;

import org.eclipse.core.runtime.IPath;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.workspace.EclipseWorkspaceService;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerCore;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerManagementModelFactory;
import com.github.cabutchei.rsp.eclipse.wst.bootstrap.WstServerManagementServerLauncher;
import com.github.cabutchei.rsp.eclipse.wst.model.launch.ServerLaunchMonitor;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.ServerManagementServerLauncher;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeType;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceInitializationService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceService;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeLifecycleListener;
import org.eclipse.wst.server.core.ServerCore;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin entrypoint and static facade for WST-backed server operations.
 */
public class WSTServerCore extends Plugin {
	private static final Logger LOG = LoggerFactory.getLogger(WSTServerCore.class);
	private static final IWstServerCore SERVER_MANAGER = new WstServerCoreService();
	private static WSTServerCore plugin;

	private IWorkspaceService workspaceService;
	private IWorkspaceInitializationService workspaceInitializationService;
	private IRuntimeLifecycleListener runtimeLifecycleListener;

	public static WSTServerCore getDefault() {
		return plugin;
	}

	public static IWstServerCore getServerManager() {
		return SERVER_MANAGER;
	}

	public static IServer[] loadServers(IServerManagementModel managementModel) {
		return SERVER_MANAGER.loadServers(managementModel);
	}

	public static IServerWorkingCopy createServer(IServerType serverType, String id, Map<String, Object> attributes,
			IServerManagementModel model) throws CoreException {
		return SERVER_MANAGER.createServer(serverType, id, attributes, model);
	}

	public static org.eclipse.wst.server.core.IRuntimeWorkingCopy createRuntimeWorkingCopy(IRuntimeType runtimeType,
			Map<String, Object> attributes, com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor monitor)
			throws CoreException {
		if (runtimeType == null) {
			return null;
		}
		IRuntimeWorkingCopy runtimeWc = runtimeType.createRuntime((String) null, monitor);
		return runtimeWc == null ? null : runtimeWc.getAdapter(org.eclipse.wst.server.core.IRuntimeWorkingCopy.class);
	}

	public static org.eclipse.wst.server.core.IRuntimeWorkingCopy createRuntimeWorkingCopy(IRuntimeType runtimeType,
			Map<String, Object> attributes) throws CoreException {
		return createRuntimeWorkingCopy(runtimeType, attributes,
				new com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor());
	}

	public static void updateServerStatus() {
		SERVER_MANAGER.updateServerStatus();
	}

	public static IStatus setGlobalAutoPublishing(boolean enabled) {
		return ((WstServerCoreService) SERVER_MANAGER).setGlobalAutoPublishing(enabled);
	}

	public static IStatus setAutoPublishingForAllServers(boolean enabled) {
		return ((WstServerCoreService) SERVER_MANAGER).setAutoPublishingForAllServers(enabled);
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		workspaceService = new EclipseWorkspaceService();
		workspaceInitializationService = workspaceService instanceof IWorkspaceInitializationService
				? (IWorkspaceInitializationService) workspaceService
				: null;
		runtimeLifecycleListener = new IRuntimeLifecycleListener() {
			@Override
			public void runtimeAdded(IRuntime runtime) {
				LOG.debug("WST runtimeAdded: {}", describeRuntime(runtime));
			}

			@Override
			public void runtimeChanged(IRuntime runtime) {
				LOG.debug("WST runtimeChanged: {}", describeRuntime(runtime));
			}

			@Override
			public void runtimeRemoved(IRuntime runtime) {
				LOG.debug("WST runtimeRemoved: {}", describeRuntime(runtime));
			}
		};
		ServerCore.addRuntimeLifecycleListener(runtimeLifecycleListener);
		ServerManagementServerLauncher.setServerManagementModelFactory(new WstServerManagementModelFactory(
				SERVER_MANAGER, workspaceService, workspaceInitializationService));
		ServerCoreActivator.setLauncherFactory(WstServerManagementServerLauncher::new);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (runtimeLifecycleListener != null) {
			ServerCore.removeRuntimeLifecycleListener(runtimeLifecycleListener);
			runtimeLifecycleListener = null;
		}
		ServerLaunchMonitor.getInstance().stop();
		ServerCoreActivator.clearLauncherFactory();
		ServerManagementServerLauncher.clearServerManagementModelFactory();
		workspaceInitializationService = null;
		workspaceService = null;
		plugin = null;
		super.stop(context);
	}

	private static String describeRuntime(IRuntime runtime) {
		if (runtime == null) {
			return "<null>";
		}
		IPath location = runtime.getLocation();
		String runtimeTypeId = runtime.getRuntimeType() == null ? "<null>" : runtime.getRuntimeType().getId();
		return "id=" + runtime.getId() + ", name=" + runtime.getName() + ", type=" + runtimeTypeId
				+ ", location=" + (location == null ? "<null>" : location.toOSString());
	}
}
