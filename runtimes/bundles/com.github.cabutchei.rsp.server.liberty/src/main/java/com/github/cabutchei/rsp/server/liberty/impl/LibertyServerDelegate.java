package com.github.cabutchei.rsp.server.liberty.impl;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.StartServerResponse;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import com.github.cabutchei.rsp.server.tomcat.servertype.impl.LibertyContextRootSupport;

public class LibertyServerDelegate extends AbstractLibertyServerDelegate implements IServerDelegate, IModuleStateProvider {

	public LibertyServerDelegate(IServer server) {
		super(server);
	}

	@Override
	public void setDependentDefaults(IServerWorkingCopy server) {
		setJavaLaunchDependentDefaults(server);
	}

	@Override
	public String[] getDeploymentUrls(String strat, String baseUrl, String deployableOutputName, DeployableState ds) {
		return new LibertyContextRootSupport().getDeploymentUrls(strat, baseUrl, deployableOutputName, ds);
	}

	@Override
	public void updateServer(IServerWorkingCopy workingCopy, UpdateServerResponse resp) {
		if (workingCopy == null) {
			return;
		}
		try {
			synchronizeRuntimeLocation(workingCopy);
			workingCopy.setAttribute("serverName", getLibertyServerId(workingCopy));
			setJavaLaunchDependentDefaults(workingCopy);
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
		try {
			prepareLaunchAttacher();
			getWstServerControl().startAsync(mode);
		} catch (CoreException e) {
			resetLaunchAttacher();
			s = StatusConverter.convert(e.getStatus());
			return new StartServerResponse(s, null);
		}
		return new StartServerResponse(StatusConverter.convert(Status.OK_STATUS), null);
	}

	@Override
	protected void handleLaunchReady(ILaunch launch) {
		setStartLaunch(launch);
		addLaunchStreamListeners(launch, false, null);
	}

	@Override
	public IStatus stopModule(DeployableReference ref) {
		getWstServerControl().stopModule(ref);
		return Status.OK_STATUS;
	}

	private void synchronizeRuntimeLocation(IServerWorkingCopy workingCopy) throws CoreException {
		String runtimeLocation = workingCopy.getAttribute(DefaultServerAttributes.SERVER_HOME_DIR, (String) null);
		if (runtimeLocation == null || runtimeLocation.isBlank()) {
			return;
		}
		IRuntime runtime = workingCopy.getRuntime();
		if (runtime == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID, "Runtime is required"));
		}
		IRuntimeWorkingCopy runtimeWc = runtime.isWorkingCopy() ? (IRuntimeWorkingCopy) runtime : runtime.createWorkingCopy();
		if (runtimeWc == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Runtime must be a working copy in order to make changes"));
		}
		runtimeWc.setLocation(new Path(runtimeLocation));
		if (runtimeWc != runtime) {
			workingCopy.setRuntime(runtimeWc);
		}
	}
}
