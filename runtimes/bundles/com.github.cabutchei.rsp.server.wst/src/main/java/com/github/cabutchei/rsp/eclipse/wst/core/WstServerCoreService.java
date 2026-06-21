package com.github.cabutchei.rsp.eclipse.wst.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.internal.RuntimeType;
import org.eclipse.wst.server.core.internal.ServerPreferences;
import org.eclipse.wst.server.core.internal.UpdateServerJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.wst.adapter.WstRspMapper;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerCore;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandlerRegistry;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstRuntimeTypeAdapter;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerAdapter;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerWorkingCopyAdapter;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeType;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;

final class WstServerCoreService implements IWstServerCore {
	private static final Logger LOG = LoggerFactory.getLogger(WstServerCoreService.class);
	private static final String BUNDLE_ID = "com.github.cabutchei.rsp.server.wst";
	private static final String PROP_AUTO_PUBLISH_SETTING = "auto-publish-setting";
	private static final int AUTO_PUBLISH_DISABLE = 1;
	private static final int AUTO_PUBLISH_RESOURCE = 2;

	@Override
	public IServer[] loadServers(IServerManagementModel managementModel) {
		return createServerProxies(ServerCore.getServers(), managementModel);
	}

	@Override
	public void updateServerStatus() {
		org.eclipse.wst.server.core.IServer[] servers = ServerCore.getServers();
		Job job = new UpdateServerJob(servers);
		job.schedule();
	}

	public IStatus setGlobalAutoPublishing(boolean enabled) {
		try {
			if (ServerCore.isAutoPublishing() != enabled) {
				ServerPreferences.getInstance().setAutoPublishing(enabled);
			}
			return Status.OK_STATUS;
		} catch (Exception e) {
			return new Status(IStatus.ERROR, BUNDLE_ID, "Failed to set global auto-publishing", e);
		}
	}

	public IStatus setAutoPublishingForAllServers(boolean enabled) {
		int setting = enabled ? AUTO_PUBLISH_RESOURCE : AUTO_PUBLISH_DISABLE;
		IProgressMonitor monitor = new NullProgressMonitor();
		IStatus firstError = null;
		for (org.eclipse.wst.server.core.IServer server : ServerCore.getServers()) {
			if (server == null) {
				continue;
			}
			try {
				org.eclipse.wst.server.core.IServerWorkingCopy wc = server.createWorkingCopy();
				if (wc.getAttribute(PROP_AUTO_PUBLISH_SETTING, AUTO_PUBLISH_RESOURCE) != setting) {
					wc.setAttribute(PROP_AUTO_PUBLISH_SETTING, setting);
					wc.save(false, monitor);
				}
			} catch (org.eclipse.core.runtime.CoreException e) {
				LOG.warn("Failed to set auto-publish setting for server {}", server.getId(), e);
				if (firstError == null) {
					firstError = new Status(IStatus.ERROR, BUNDLE_ID,
							"Failed to set auto-publish setting for server " + server.getId(), e);
				}
			}
		}
		return firstError == null ? Status.OK_STATUS : firstError;
	}

	@Override
	public IServerWorkingCopy createServer(IServerType serverType, String id, Map<String, Object> attributes,
			IServerManagementModel model) throws CoreException {
		if (serverType == null) {
			throw new IllegalArgumentException("serverType cannot be null");
		}
		IProgressMonitor monitor = new NullProgressMonitor();
		org.eclipse.wst.server.core.IServerType wstServerType = WstRspMapper.toWstServerType(serverType);
		if (wstServerType == null) {
			throw new CoreException(new Status(IStatus.ERROR, "",
					"Server Type " + serverType.getId() + " is unknown by this Eclipse application"));
		}
		try {
			IRuntimeType runtimeType = createRuntimeTypeProxy(wstServerType.getRuntimeType());
			org.eclipse.wst.server.core.IRuntime runtimeWC = createRuntimeWorkingCopy(runtimeType, attributes,
					new com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor());
			org.eclipse.wst.server.core.IServerWorkingCopy server = wstServerType.createServer(id, null, runtimeWC,
					monitor);
			server.setName(id);
			applyAttributes(server, attributes);
			IServerWorkingCopy serverAdapter = createServerWorkingCopyProxy(server, model);
			WstServerTypeHandler handler = WstServerTypeHandlerRegistry.find(serverType.getId());
			if (handler != null) {
				handler.configureServer(serverAdapter, attributes);
			}
			setServerAutoPublishing(server, false);
			return serverAdapter;
		} catch (org.eclipse.core.runtime.CoreException e) {
			throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
		}
	}

	org.eclipse.wst.server.core.IRuntimeWorkingCopy createRuntimeWorkingCopy(IRuntimeType runtimeType,
			Map<String, Object> attributes, com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor monitor)
			throws CoreException {
		if (runtimeType == null) {
			return null;
		}
		IRuntimeWorkingCopy runtimeWc = runtimeType.createRuntime((String) null, monitor);
		return runtimeWc == null ? null : runtimeWc.getAdapter(org.eclipse.wst.server.core.IRuntimeWorkingCopy.class);
	}

	org.eclipse.wst.server.core.IRuntimeWorkingCopy createRuntimeWorkingCopy(IRuntimeType runtimeType,
			Map<String, Object> attributes) throws CoreException {
		return createRuntimeWorkingCopy(runtimeType, attributes,
				new com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor());
	}

	private IRuntimeType createRuntimeTypeProxy(org.eclipse.wst.server.core.IRuntimeType wstRuntimeType)
			throws CoreException {
		if (wstRuntimeType == null) {
			return null;
		}
		if (wstRuntimeType instanceof RuntimeType) {
			return new WstRuntimeTypeAdapter((RuntimeType) wstRuntimeType);
		}
		throw new CoreException(new Status(IStatus.ERROR, BUNDLE_ID,
				"Unsupported WST runtime type implementation: " + wstRuntimeType.getClass().getName()));
	}

	private IServer[] createServerProxies(org.eclipse.wst.server.core.IServer[] wstServers,
			IServerManagementModel managementModel) {
		if (wstServers == null || wstServers.length == 0) {
			return new IServer[0];
		}
		List<IServer> rspServers = new ArrayList<>();
		for (org.eclipse.wst.server.core.IServer wstServer : wstServers) {
			if (wstServer == null) {
				continue;
			}
			rspServers.add(createServerProxy(wstServer, managementModel, null));
		}
		return rspServers.toArray(new IServer[0]);
	}

	private IServerWorkingCopy createServerWorkingCopyProxy(
			org.eclipse.wst.server.core.IServerWorkingCopy wstServerWorkingCopy, IServerManagementModel managementModel) {
		Objects.requireNonNull(wstServerWorkingCopy, "wstServerWorkingCopy");
		return new WstServerWorkingCopyAdapter(wstServerWorkingCopy, managementModel);
	}

	private IServer createServerProxy(org.eclipse.wst.server.core.IServer wstServer,
			IServerManagementModel managementModel, IServerDelegate delegate) {
		Objects.requireNonNull(wstServer, "wstServer");
		WstServerAdapter proxy = new WstServerAdapter(wstServer, managementModel);
		if (delegate != null) {
			proxy.setDelegate(delegate);
		}
		return proxy;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void applyAttributes(org.eclipse.wst.server.core.IServerWorkingCopy server, Map<String, Object> attributes) {
		if (server == null || attributes == null || attributes.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (key == null || value == null) {
				continue;
			}
			if (value instanceof Integer) {
				server.setAttribute(key, ((Integer) value).intValue());
			} else if (value instanceof Boolean) {
				server.setAttribute(key, ((Boolean) value).booleanValue());
			} else if (value instanceof String) {
				server.setAttribute(key, (String) value);
			} else if (value instanceof List) {
				server.setAttribute(key, (List) value);
			} else if (value instanceof Map) {
				server.setAttribute(key, (Map) value);
			}
		}
	}

	private void setServerAutoPublishing(org.eclipse.wst.server.core.IServerWorkingCopy server, boolean enabled) {
		if (server == null) {
			return;
		}
		server.setAttribute(PROP_AUTO_PUBLISH_SETTING, enabled ? AUTO_PUBLISH_RESOURCE : AUTO_PUBLISH_DISABLE);
	}
}
