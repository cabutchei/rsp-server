package com.github.cabutchei.rsp.server.websphere.impl;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerWorkingCopyAdapter;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import com.github.cabutchei.rsp.server.servertype.impl.WebSphereContextRootSupport;
import com.github.cabutchei.rsp.server.servertype.impl.IWebSphereServerAttributes;

import com.ibm.ws.ast.st.common.core.internal.util.ProfileChangeHelper;
import com.ibm.ws.ast.st.v85.core.internal.WASServer;

public class WebSphereServerDelegate extends AbstractWebSphereServerDelegate {

	public WebSphereServerDelegate(IServer server) {
		super(server);
	}

	@Override
	public String[] getDeploymentUrls(String strat, String baseUrl, String deployableOutputName,
			DeployableState ds) {
		return new WebSphereContextRootSupport().getDeploymentUrls(strat, baseUrl, deployableOutputName, ds);
	}

	@Override
	public void updateServer(IServerWorkingCopy workingCopy, UpdateServerResponse resp) {
		if (workingCopy == null) {
			return;
		}
		try {
			synchronizeRuntimeLocation(workingCopy);
			WstServerWorkingCopyAdapter adapter = workingCopy.getAdapter(WstServerWorkingCopyAdapter.class);
			if (adapter == null) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
						"Unable to adapt server working copy to WstServerWorkingCopyAdapter"));
			}
			org.eclipse.wst.server.core.IServerWorkingCopy wstWorkingCopy =
					adapter.getAdapter(org.eclipse.wst.server.core.IServerWorkingCopy.class);
			WASServer wasServer = wstWorkingCopy == null ? null : (WASServer) wstWorkingCopy.loadAdapter(WASServer.class, null);
			if (wasServer == null) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
						"Unable to load WebSphere server adapter"));
			}
			String profileName = workingCopy.getAttribute(IWebSphereServerAttributes.WEBSPHERE_PROFILE, "AppSrv01");
			wasServer.setWebSphereProfileName(profileName);
			new ProfileChangeHelper().updateBaseServerForProfileChange(wstWorkingCopy, profileName);
			setJavaLaunchDependentDefaults(workingCopy);
		} catch (CoreException e) {
			if (resp != null && resp.getValidation() != null) {
				resp.getValidation().setStatus(StatusConverter.convert(e.getStatus()));
			}
		}
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
