package com.github.cabutchei.rsp.eclipse.wst.api;

import java.util.List;

import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ModuleState;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerListener;

/**
 * Rich WST-backed server control surface used by WST delegates.
 */
public interface IWstServerControl extends IServer {

	IRuntime getRuntime();

	IStatus addDeployable(DeployableReference reference);

	IStatus canAddDeployable(DeployableReference reference);

	IStatus removeDeployable(DeployableReference reference);

	IStatus canRemoveDeployable(DeployableReference reference);

	IStatus publish(int publishRequestType);

	IStatus publish(int publishRequestType, IProgressMonitor monitor);

	IStatus canPublish();

	int getServerPublishState();

	int getEffectiveServerPublishState();

	int getServerRunState();

	List<DeployableState> getDeployableStates();

	List<ModuleState> getModuleStates();

	DeployableState getDeployableState(DeployableReference reference);

	void startAsync(String launchMode) throws CoreException;

	IStatus canStart(String launchMode);

	void stop(boolean force);

	IStatus canStop();

	void startModule(DeployableReference reference);

	void stopModule(DeployableReference reference);

	void addServerListener(IServerListener listener);

	void addPublishListener(IWstPublishListener listener);

	String getMode();
}
