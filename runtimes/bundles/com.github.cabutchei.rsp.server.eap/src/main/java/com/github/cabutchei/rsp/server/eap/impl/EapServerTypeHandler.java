package com.github.cabutchei.rsp.server.eap.impl;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ControllableServerBehavior;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ILaunchServerController;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.jdt.JDTPlugin;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerWorkingCopyAdapter;
import com.github.cabutchei.rsp.server.eap.adapter.IJBossRuntimeAdapter;
import com.github.cabutchei.rsp.server.eap.servertype.IEapServerAttributes;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;

final class EapServerTypeHandler implements WstServerTypeHandler {
	private static final String CUSTOM_LAUNCH_SUBSYSTEM = "launch.my.local";
	private static final String BUNDLE_ID = "com.github.cabutchei.rsp.server.eap";

	@Override
	public boolean handles(String serverTypeId) {
		return IEapServerAttributes.EAP_70_SERVER_TYPE.equals(serverTypeId);
	}

	@Override
	public void configureServer(IServerWorkingCopy server, IRuntimeWorkingCopy runtime, Map<String, Object> attributes, IProgressMonitor monitor) throws CoreException {
		// noop
	}

	@Override
	public void configureServer(com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy serverWc, Map<String, Object> attributes) throws CoreException {
		String configFile = getStringAttribute(attributes, IEapServerAttributes.CONFIG_FILE, IEapServerAttributes.CONFIG_FILE_DEFAULT);

		String baseDir = getStringAttribute(attributes, IEapServerAttributes.BASE_DIRECTORY,
				IEapServerAttributes.BASE_DIRECTORY_DEFAULT);
		serverWc.setAttribute(IEapServerAttributes.BASE_DIRECTORY, baseDir);

		String restartPattern = getStringAttribute(attributes, IEapServerAttributes.RESTART_FILE_PATTERN,
				IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT);
		boolean useDefaultRestartPattern = shouldUseDefaultRestartPattern(restartPattern);
		serverWc.setAttribute(IEapServerAttributes.USE_DEFAULT_RESTART_FILE_PATTERN, useDefaultRestartPattern);
		if (useDefaultRestartPattern) {
			restartPattern = IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT;
		}
		serverWc.setAttribute(IEapServerAttributes.RESTART_FILE_PATTERN, restartPattern);

		serverWc.setAttribute(IEapServerAttributes.ATTACH_DEBUGGER, false);
		serverWc.setAttribute(
				ControllableServerBehavior.PROPERTY_PREFIX + ILaunchServerController.SYSTEM_ID,
				CUSTOM_LAUNCH_SUBSYSTEM);

		String vmInstallLocation = getStringAttribute(attributes, IEapServerAttributes.VM_INSTALL_PATH,
				IEapServerAttributes.VM_INSTALL_PATH_DEFAULT);
		WstServerWorkingCopyAdapter serverAdapter = serverWc.getAdapter(WstServerWorkingCopyAdapter.class);
		if (serverAdapter == null) {
			throw new CoreException(new Status(IStatus.ERROR, BUNDLE_ID, "Could not cast to WstServerAdapter"));
		}
		String runtimeLocation = getStringAttribute(attributes, DefaultServerAttributes.SERVER_HOME_DIR, null);
		IRuntime runtime = serverAdapter.getRuntime();
		if (runtime == null) {
			throw new CoreException(new Status(IStatus.ERROR, BUNDLE_ID, "Runtime is required"));
		}
		if (!runtime.isWorkingCopy()) {
			throw new CoreException(new Status(IStatus.ERROR, BUNDLE_ID,
					"Runtime must be a working copy in order to make changes"));
		}
		if (runtimeLocation != null && !runtimeLocation.isBlank()) {
			((com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy) runtime)
					.setLocation(new Path(runtimeLocation));
		}
		IJBossRuntimeAdapter jbossRuntimeAdapter = (IJBossRuntimeAdapter) runtime.loadAdapter(IJBossRuntimeAdapter.class);
		jbossRuntimeAdapter.setConfigurationFile(configFile);
		jbossRuntimeAdapter.setVM(JDTPlugin.getVMService().findOrCreateVMInstall(vmInstallLocation));
	}

	private String getStringAttribute(Map<String, Object> attributes, String key, String defaultValue) {
		if (attributes == null || key == null) {
			return defaultValue;
		}
		Object value = attributes.get(key);
		return value instanceof String ? (String) value : defaultValue;
	}

	private boolean shouldUseDefaultRestartPattern(String pattern) {
		if (pattern == null) {
			return true;
		}
		return IEapServerAttributes.RESTART_FILE_PATTERN_DEFAULT.equals(pattern);
	}
}
