package com.github.cabutchei.rsp.eclipse.wst.proxy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ModuleState;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.wst.adapter.WstRspMapper;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerControl;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerListener;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;


public class WstServerWorkingCopyAdapter implements IServerWorkingCopy, IWstServerControl {
	private final org.eclipse.wst.server.core.IServerWorkingCopy wstServerWorkingCopy;
	private final IServerManagementModel managementModel;
	private final IServerModel serverModel;
    private IServerDelegate delegate;
	private IWstServerControl server;
	private IRuntime runtime;

	public WstServerWorkingCopyAdapter(org.eclipse.wst.server.core.IServerWorkingCopy wstServerWorkingCopy, IServerManagementModel managementModel) {
		this.wstServerWorkingCopy = Objects.requireNonNull(wstServerWorkingCopy, "wstServerWorkingCopy cannot be null");
		this.managementModel = managementModel;
		this.serverModel = managementModel.getServerModel();
		org.eclipse.wst.server.core.IServer wstServer = wstServerWorkingCopy.getOriginal();
		this.server = wstServer == null? null : new WstServerAdapter(wstServer, managementModel);
		org.eclipse.wst.server.core.IRuntime wstRuntime = wstServerWorkingCopy.getRuntime();// TODO:
		if (wstRuntime == null) {
			this.runtime = null;
		}
		else if (wstRuntime instanceof org.eclipse.wst.server.core.IRuntimeWorkingCopy) {
			org.eclipse.wst.server.core.IRuntimeWorkingCopy rwc = (org.eclipse.wst.server.core.IRuntimeWorkingCopy) wstRuntime;
			this.runtime = new WstRuntimeWorkingCopyAdapter(rwc);
		} else {
			this.runtime = new WstRuntimeAdapter(wstRuntime);
		}
	}

    @Override
	public String getName() {
        return wstServerWorkingCopy.getName();
    }

    @Override
    public String getId() {
        return wstServerWorkingCopy.getId();
    }

    @Override
    public void setAttribute(String key, int value) {
        wstServerWorkingCopy.setAttribute(key, value);
    }

    @Override
	public void setAttribute(String key, boolean value) {
        wstServerWorkingCopy.setAttribute(key, value);
    }

    @Override
	public void setAttribute(String key, String value) {
        wstServerWorkingCopy.setAttribute(key, value);
    }

    @Override
	public void setAttribute(String key, List<String> value) {
        wstServerWorkingCopy.setAttribute(key, value);
    }

    @Override
	public void setAttribute(String key, Map value) {
        wstServerWorkingCopy.setAttribute(key, value);
    }

    @Override
    public int getAttribute(String attributeName, int defaultValue) {
        return wstServerWorkingCopy.getAttribute(attributeName, defaultValue);
    }

    @Override
	public List<String> getAttribute(String attributeName, List<String> defaultValue) {
        return wstServerWorkingCopy.getAttribute(attributeName, defaultValue);
    }

    @Override
	public String getAttribute(String attributeName, String defaultValue) {
        return wstServerWorkingCopy.getAttribute(attributeName, defaultValue);
    }

    @Override
	public Map getAttribute(String attributeName, Map defaultValue) {
        return wstServerWorkingCopy.getAttribute(attributeName, defaultValue);
    }

    @Override
	public boolean getAttribute(String attributeName, boolean defaultValue) {
        return wstServerWorkingCopy.getAttribute(attributeName, defaultValue);
    }

	@Override
	public <T> T getAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstServerWorkingCopy)) {
			return adapterType.cast(wstServerWorkingCopy);
		}
		try {
			Object adapted = wstServerWorkingCopy.getAdapter(adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through to platform adapter manager.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(this, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(wstServerWorkingCopy, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return null;
	}

	@Override
	public <T> Object loadAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstServerWorkingCopy)) {
			return adapterType.cast(wstServerWorkingCopy);
		}
		if (wstServerWorkingCopy instanceof org.eclipse.wst.server.core.IServerAttributes) {
			try {
				Object adapted = ((org.eclipse.wst.server.core.IServerAttributes) wstServerWorkingCopy)
						.loadAdapter(adapterType, null);
				if (adapted != null) {
					return adapterType.cast(adapted);
				}
			} catch (Exception e) {
				// Fall back to getAdapter below.
			}
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(this, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager()
					.loadAdapter(wstServerWorkingCopy, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return getAdapter(adapterType);
	}

    @Override
    public IServer save(boolean force, IProgressMonitor monitor) throws CoreException {
        try {
            org.eclipse.wst.server.core.IServer server = wstServerWorkingCopy.save(force, new NullProgressMonitor());
			if (this.server == null) this.server = new WstServerAdapter(server, managementModel);
            return this.server;
        } catch (org.eclipse.core.runtime.CoreException e) {
            throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
        }
    }

    @Override
    public void save(IProgressMonitor monitor) throws CoreException {
        try {
            org.eclipse.wst.server.core.IServer wstServer = wstServerWorkingCopy.save(false, new NullProgressMonitor());
			if (this.server == null) this.server = new WstServerAdapter(wstServer, managementModel);
        } catch (org.eclipse.core.runtime.CoreException e) {
            throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
        }
    }

	@Override
	public IServer saveAll(boolean force) throws CoreException {
		try {
			org.eclipse.wst.server.core.IServer wstServer = wstServerWorkingCopy.saveAll(force, null);
			if (this.server == null) this.server = new WstServerAdapter(wstServer, managementModel);
			return this.server;
		} catch(org.eclipse.core.runtime.CoreException e) {
			throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
		}
	}
	@Override
	public String getTypeId() {
		IServerType serverType = getServerType();
		if (serverType != null) {
			return serverType.getId();
		}
		return wstServerWorkingCopy.getServerType() == null ? null : wstServerWorkingCopy.getServerType().getId();
	}

	@Override
	public IServerType getServerType() {
		return WstRspMapper.toRspServerType(wstServerWorkingCopy.getServerType(), serverModel);
	}

	@Override
	public IServerDelegate getDelegate() {
		if (delegate != null) {
			return delegate;
		}
		IServerType serverType = getServerType();
		if (serverType == null) {
			return null;
		}
		delegate = serverType.createServerDelegate(this);
		return delegate;
	}

	@Override
	public IServerWorkingCopy createWorkingCopy() {
		return this;
	}

	@Override
	public IRuntime getRuntime() {
		return this.runtime;
	}

	@Override
	public void setRuntime(IRuntime runtime) {
		org.eclipse.wst.server.core.IRuntime wstRuntime = runtime.getAdapter(org.eclipse.wst.server.core.IRuntime.class);
		this.wstServerWorkingCopy.setRuntime(wstRuntime);
		this.runtime = runtime;
	}

	public void setRuntimeId(String runtimeId) {
		this.wstServerWorkingCopy.getAdapter(org.eclipse.wst.server.core.internal.ServerWorkingCopy.class).setRuntimeId(runtimeId);
	}

	@Override
	public String asJson(IProgressMonitor monitor) throws CoreException {
		throw new UnsupportedOperationException("asJson is not supported for server working copies");
	}

	@Override
	public void load(IProgressMonitor monitor) throws CoreException {
		// No-op: WST is the source of truth.
	}

	@Override
	public void delete() throws CoreException {
		throw new UnsupportedOperationException("delete is not supported for server working copies");
	}

	@Override
	public IServerManagementModel getServerManagementModel() {
		return managementModel;
	}

	@Override
	public IServerModel getServerModel() {
		return serverModel;
	}

	private IStatus unsupported(String operation) {
		return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
				operation + " is not supported for server working copies");
	}

	@Override
	public IStatus addDeployable(DeployableReference reference) {
		return unsupported("addDeployable");
	}

	@Override
	public IStatus canAddDeployable(DeployableReference reference) {
		return unsupported("canAddDeployable");
	}

	@Override
	public IStatus removeDeployable(DeployableReference reference) {
		return unsupported("removeDeployable");
	}

	@Override
	public IStatus canRemoveDeployable(DeployableReference reference) {
		return unsupported("canRemoveDeployable");
	}

	@Override
	public IStatus publish(int publishRequestType) {
		return unsupported("publish");
	}

	@Override
	public IStatus canPublish() {
		return unsupported("canPublish");
	}

	@Override
	public int getServerPublishState() {
		return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
	}

	@Override
	public int getEffectiveServerPublishState() {
		return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
	}

	@Override
	public int getServerRunState() {
		return ServerManagementAPIConstants.STATE_UNKNOWN;
	}

	@Override
	public List<DeployableState> getDeployableStates() {
		return Collections.emptyList();
	}

	@Override
	public List<ModuleState> getModuleStates() {
		return Collections.emptyList();
	}

	@Override
	public DeployableState getDeployableState(DeployableReference reference) {
		return null;
	}

	@Override
	public void startAsync(String launchMode) throws CoreException {
		throw new CoreException(unsupported("startAsync"));
	}

	@Override
	public IStatus canStart(String launchMode) {
		return unsupported("canStart");
	}

	@Override
	public void stop(boolean force) {
		// no-op for working copies
	}

	@Override
	public IStatus canStop() {
		return unsupported("canStop");
	}

	@Override
	public void startModule(DeployableReference reference) {
		// no-op for working copies
	}

	@Override
	public void stopModule(DeployableReference reference) {
		// no-op for working copies
	}

	@Override
	public void addServerListener(IServerListener listener) {
		// no-op for working copies
	}

	@Override
	public String getMode() {
		return null;
	}

}
