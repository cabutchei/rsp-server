package com.github.cabutchei.rsp.server.websphere.impl;

import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.IServerWorkingCopy;

import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;

import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerWorkingCopyAdapter;
import com.github.cabutchei.rsp.server.servertype.impl.IWebSphereServerAttributes;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;

import com.ibm.ws.ast.st.common.core.internal.util.ProfileChangeHelper;
import com.ibm.ws.ast.st.v85.core.internal.WASServer;



final class WebSphereWstServerTypeHandler implements WstServerTypeHandler {
	private static final String PROFILE_ATTRIBUTE = IWebSphereServerAttributes.WEBSPHERE_PROFILE;

	@Override
	public boolean handles(String serverTypeId) {
		return serverTypeId != null
				&& serverTypeId.startsWith(IWebSphereServerAttributes.WEBSPHERE_SERVER_TYPE_PREFIX);
	}

	@Override
	public void configureServer(IServerWorkingCopy server,
			IRuntimeWorkingCopy runtime,
			Map<String, Object> attributes,
			IProgressMonitor monitor) throws CoreException {
		configureWebSphereProfile(server, attributes);
	}

	@Override
	public void configureServer(com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy server,
			Map<String, Object> attributes) throws CoreException {
		String runtimeLocation = getStringAttribute(attributes, DefaultServerAttributes.SERVER_HOME_DIR, null);
		IRuntime runtime = server.getRuntime();
		if (runtime == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID, "Runtime is required"));
		}
		if (!runtime.isWorkingCopy()) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Runtime must be a working copy in order to make changes"));
		}
		if (runtimeLocation != null && !runtimeLocation.isBlank()) {
			((com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy) runtime)
					.setLocation(new Path(runtimeLocation));
		}
		WstServerWorkingCopyAdapter adapter = server.getAdapter(WstServerWorkingCopyAdapter.class);
		if (adapter == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Unable to adapt server working copy to WstServerWorkingCopyAdapter"));
		}
		org.eclipse.wst.server.core.IServerWorkingCopy wstServer = adapter.getAdapter(org.eclipse.wst.server.core.IServerWorkingCopy.class);
		configureWebSphereProfile(wstServer, attributes);
	}

	private void configureWebSphereProfile(org.eclipse.wst.server.core.IServerWorkingCopy server,
			Map<String, Object> attributes) throws CoreException {
		String profileName = attributes == null ? null : (String) attributes.get(PROFILE_ATTRIBUTE);
		WASServer wasServer = server == null ? null : (WASServer) server.loadAdapter(WASServer.class, null);
		if( wasServer == null ) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Unable to load WebSphere server adapter"));
		}
		wasServer.setWebSphereProfileName(profileName);
		new ProfileChangeHelper().updateBaseServerForProfileChange(server, profileName);
	}

	private String getStringAttribute(Map<String, Object> attributes, String key, String defaultValue) {
		if (attributes == null || key == null) {
			return defaultValue;
		}
		Object value = attributes.get(key);
		return value instanceof String ? (String) value : defaultValue;
	}
}
