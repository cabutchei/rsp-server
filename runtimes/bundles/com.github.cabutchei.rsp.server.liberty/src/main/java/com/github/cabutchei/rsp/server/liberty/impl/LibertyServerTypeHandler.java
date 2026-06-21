package com.github.cabutchei.rsp.server.liberty.impl;


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
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.tomcat.servertype.impl.ILibertyServerAttributes;



final class LibertyServerTypeHandler implements WstServerTypeHandler {
	private static final String PROFILE_ATTRIBUTE = ILibertyServerAttributes.LIBERTY_PROFILE;
	private static final String BUNDLE_ID = "com.github.cabutchei.rsp.server.liberty";

	@Override
	public boolean handles(String serverTypeId) {
		return serverTypeId != null
				&& serverTypeId.startsWith(ILibertyServerAttributes.LIBERTY_SERVER_TYPE_PREFIX);
	}

	@Override
	public void configureServer(IServerWorkingCopy server, IRuntimeWorkingCopy runtime, Map<String, Object> attributes, IProgressMonitor monitor) throws CoreException {
		String profileName = (String) attributes.get(PROFILE_ATTRIBUTE);
		server.setAttribute("serverName", profileName);
	}

	@Override
	public void configureServer(com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy server, Map<String, Object> attributes) throws CoreException {
		String runtimeLocation = getStringAttribute(attributes, DefaultServerAttributes.SERVER_HOME_DIR, null);
		IRuntime runtime = server.getRuntime();
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
		String profileName = (String) attributes.get(PROFILE_ATTRIBUTE);
		server.setAttribute("serverName", profileName);
	}

	private String getStringAttribute(Map<String, Object> attributes, String key, String defaultValue) {
		if (attributes == null || key == null) {
			return defaultValue;
		}
		Object value = attributes.get(key);
		return value instanceof String ? (String) value : defaultValue;
	}

}
