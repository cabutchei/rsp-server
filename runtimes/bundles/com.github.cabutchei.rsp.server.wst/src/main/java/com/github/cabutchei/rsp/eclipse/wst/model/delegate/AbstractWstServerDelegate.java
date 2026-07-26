package com.github.cabutchei.rsp.eclipse.wst.model.delegate;

import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.ModuleState;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.debug.core.ILaunch;
import com.github.cabutchei.rsp.eclipse.debug.core.IStreamListener;
import com.github.cabutchei.rsp.eclipse.debug.core.model.IProcess;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstPublishListener;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerControl;
import com.github.cabutchei.rsp.eclipse.wst.model.launch.WstLaunchStreamAttacher;
import com.github.cabutchei.rsp.eclipse.wst.publishing.WSTServerPublishStateModel;
import com.github.cabutchei.rsp.eclipse.wst.stream.WSTServerStreamListener;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerListener;
import com.github.cabutchei.rsp.server.spi.servertype.IServerPublishModel;
import com.github.cabutchei.rsp.server.spi.servertype.ServerEvent;

public abstract class AbstractWstServerDelegate extends AbstractServerDelegate implements IModuleStateProvider {
	private static final String PROCESS_ID_KEY = "process.id.key";

	private final IWstServerControl wstServerControl;
	private final WstLaunchStreamAttacher launchStreamAttacher;
	private final IServerListener serverStateListener = new IServerListener() {
		@Override
		public void serverChanged(ServerEvent event) {
			if (event == null) {
				return;
			}
			int kind = event.getKind();
			if ((kind & ServerEvent.STATE_CHANGE) == 0) {
				return;
			}
			if ((kind & ServerEvent.SERVER_CHANGE) == 0) {
				return;
			}
			syncRunStateFromControl(true);
		}
	};
	private final IWstPublishListener publishListener = new IWstPublishListener() {
		@Override
		public void publishStarted() {
			if (getServer() != null && getServer().getServerModel() != null) {
				getServer().getServerModel().fireServerPublishStarted(getServer());
			}
		}

		@Override
		public void publishFinished() {
			if (getServer() != null && getServer().getServerModel() != null) {
				getServer().getServerModel().fireServerPublishFinished(getServer());
			}
		}
	};

	protected AbstractWstServerDelegate(IServer server) {
		super(server);
		this.wstServerControl = adaptWstServerControl(server);
		this.launchStreamAttacher = new WstLaunchStreamAttacher(server.getId(), this::handleLaunchReady);
		registerWstStateListener();
		registerWstPublishListener();
		syncRunStateFromControl(false);
	}

	private IWstServerControl adaptWstServerControl(IServer server) {
		if (server instanceof IWstServerControl) {
			return (IWstServerControl) server;
		}
		IWstServerControl adapted = server == null ? null : server.getAdapter(IWstServerControl.class);
		if (adapted != null) {
			return adapted;
		}
		throw new IllegalArgumentException("Server " + (server == null ? "<null>" : server.getId())
				+ " does not provide IWstServerControl");
	}

	@Override
	public IStatus canAddDeployable(DeployableReference ref) {
		return wstServerControl.canAddDeployable(ref);
	}

	@Override
	public IStatus canRemoveDeployable(DeployableReference ref) {
		return wstServerControl.canRemoveDeployable(ref);
	}

	@Override
	public IServerPublishModel createServerPublishModel() {
		return new WSTServerPublishStateModel(this, wstServerControl, getFileWatcherService(), getFullPublishRequiredCallback());
	}

	public IWstServerControl getWstServerControl() {
		return wstServerControl;
	}

	@Override
	public IStatus canPublish() {
		return wstServerControl.canPublish();
	}

	@Override
	public IStatus publish(int publishRequestType) {
		IStatus status = wstServerControl.publish(publishRequestType);
		if (status != null && status.isOK() && getServerPublishModel() instanceof WSTServerPublishStateModel) {
			((WSTServerPublishStateModel) getServerPublishModel()).markPublished();
		}
		fireStateChanged(getServerState());
		return status;
	}

	@Override
	public int getServerRunState() {
		return super.getServerRunState();
	}

	@Override
	public List<ModuleState> getModuleStates() {
		return wstServerControl == null ? Collections.emptyList() : wstServerControl.getModuleStates();
	}

	@Override
	public String getMode() {
		return wstServerControl.getMode();
	}

	@Override
	public IStatus canStart(String mode) {
		return wstServerControl.canStart(mode);
	}

	@Override
	public IStatus stop(boolean force) {
		wstServerControl.stop(force);
		return Status.OK_STATUS;
	}

	private void registerWstStateListener() {
		wstServerControl.addServerListener(serverStateListener);
	}

	private void registerWstPublishListener() {
		wstServerControl.addPublishListener(publishListener);
	}

	private void syncRunStateFromControl(boolean fire) {
		setDelegateRunState(wstServerControl.getServerRunState(), fire);
	}

	private void setDelegateRunState(int state, boolean fire) {
		super.setServerState(state, fire);
	}

	@Override
	public IStatus startModule(DeployableReference ref) {
		wstServerControl.startModule(ref);
		return Status.OK_STATUS;
	}

	@Override
	public IStatus stopModule(DeployableReference ref) {
		wstServerControl.stopModule(ref);
		return Status.OK_STATUS;
	}

	protected final void prepareLaunchAttacher() {
		launchStreamAttacher.reset();
		launchStreamAttacher.attach();
	}

	protected final void resetLaunchAttacher() {
		launchStreamAttacher.reset();
	}

	protected final ILaunch awaitLaunchWithProcess(long timeoutMillis) {
		return launchStreamAttacher.awaitLaunchWithProcess(timeoutMillis);
	}

	protected void handleLaunchReady(ILaunch launch) {
		addLaunchStreamListeners(launch, false, null);
	}

	public final void attachLaunchStreamListenersFromMonitor(ILaunch launch) {
		addLaunchStreamListeners(launch, true, null);
	}

	protected final void addLaunchStreamListeners(ILaunch launch, boolean skipAlreadyTagged, IntConsumer debugPortListener) {
		if (launch == null) {
			return;
		}
		String ctime = String.valueOf(System.currentTimeMillis());
		IProcess[] all = launch.getProcesses();
		if (all == null) {
			return;
		}
		for (int i = 0; i < all.length; i++) {
			IProcess process = all[i];
			if (process == null) {
				continue;
			}
			if (skipAlreadyTagged && process.getAttribute(PROCESS_ID_KEY) != null) {
				continue;
			}
			String pName = getServer().getTypeId() + ":" + getServer().getId() + ":" + ctime + ":p" + i;
			process.setAttribute(PROCESS_ID_KEY, pName);
			IStreamListener out = new WSTServerStreamListener(getServer(), getProcessId(process),
					ServerManagementAPIConstants.STREAM_TYPE_SYSOUT, debugPortListener);
			IStreamListener err = new WSTServerStreamListener(getServer(), getProcessId(process),
					ServerManagementAPIConstants.STREAM_TYPE_SYSERR, debugPortListener);
			process.getStreamsProxy().getOutputStreamMonitor().addListener(out);
			process.getStreamsProxy().getErrorStreamMonitor().addListener(err);
		}
	}
}
