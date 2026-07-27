package com.github.cabutchei.rsp.server.liberty.impl;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerDelegateAccess;
import com.github.cabutchei.rsp.server.spi.servertype.IServerAttributes;
import com.ibm.ws.st.core.internal.WebSphereServer;
import com.ibm.ws.st.core.internal.WebSphereServerInfo;

public final class LibertyWstServerAccess implements IWstServerDelegateAccess<WebSphereServer> {
	private LibertyWstServerAccess() {
		// utility class
	}

	public static final LibertyWstServerAccess INSTANCE = new LibertyWstServerAccess();

	@Override
	public Class<WebSphereServer> getDelegateType() {
		return WebSphereServer.class;
	}

	// public static WebSphereServer getWstDelegate(Object rspServerOrWorkingCopy) throws CoreException {
	// 	return INSTANCE.getDelegate(rspServerOrWorkingCopy);
	// }

	public static WebSphereServer getDelegate(IServerAttributes server) {
		return server.getAdapter(WebSphereServer.class);
	}

	public static IStatus validateLibertyServerExists(IServerAttributes server) throws CoreException{
		WebSphereServer wstDelegate = getDelegate(server);
		if (wstDelegate == null) {
			throw new CoreException(new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.liberty.LibertyPluginConstants.BUNDLE_ID,
						"WST WebSphereServer delegate not found"));
					}
		WebSphereServerInfo serverInfo = wstDelegate.getServerInfo();
		if (serverInfo == null) {
			return new Status(IStatus.ERROR, com.github.cabutchei.rsp.server.liberty.LibertyPluginConstants.BUNDLE_ID, "The specified server is not a Liberty profile.");
		}
		return Status.OK_STATUS;
	}
}
