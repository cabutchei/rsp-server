/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.github.cabutchei.rsp.api.RSPWTPClient;
import com.github.cabutchei.rsp.api.dao.DiscoveryPath;
import com.github.cabutchei.rsp.api.dao.JobHandle;
import com.github.cabutchei.rsp.api.dao.JobProgress;
import com.github.cabutchei.rsp.api.dao.JobRemoved;
import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerProcess;
import com.github.cabutchei.rsp.api.dao.ServerProcessOutput;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.VMDescription;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.jdt.launching.IVMInstall;
import com.github.cabutchei.rsp.eclipse.jdt.launching.IVMInstallChangedListener;
import com.github.cabutchei.rsp.eclipse.jdt.launching.PropertyChangeEvent;
import com.github.cabutchei.rsp.server.ServerManagementServerImpl;
import com.github.cabutchei.rsp.server.spi.discovery.IDiscoveryPathListener;
import com.github.cabutchei.rsp.server.spi.jobs.IJob;
import com.github.cabutchei.rsp.server.spi.jobs.IJobListener;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModelListener;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

public class RemoteEventManager implements IDiscoveryPathListener, 
	IVMInstallChangedListener, IServerModelListener, IJobListener {
	
	private ServerManagementServerImpl server;
	public RemoteEventManager(ServerManagementServerImpl serverManagementServerImpl) {
		this.server = serverManagementServerImpl; 
		serverManagementServerImpl.getModel().getDiscoveryPathModel().addListener(this);
		//serverManagementServerImpl.getModel().getVMInstallModel().addListener(this);
		serverManagementServerImpl.getModel().getServerModel().addServerModelListener(this);
		serverManagementServerImpl.getModel().getJobManager().addJobListener(this);
	}
	
	@Override
	public void discoveryPathAdded(DiscoveryPath path) {
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.discoveryPathAdded(path);
		}
	}
	@Override
	public void discoveryPathRemoved(DiscoveryPath path) {
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.discoveryPathRemoved(path);
		}
	}

	public void serverAdded(ServerHandle server2) {
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.serverAdded(server2);
		}
	}
	
	public void serverRemoved(ServerHandle server2) {
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.serverRemoved(server2);
		}
	}
	
	public void serverAttributesChanged(ServerHandle server) {
		// TODO 
	}
	
	public void serverStateChanged(ServerHandle server, ServerState state) {
		List<RSPWTPClient> l = this.server.getClients();
		if( this.server.getModel().getServerModel().getServer(server.getId()) != null ) {
			for( RSPWTPClient c : l) {
				c.serverStateChanged(state);
			}
		}
	}
	
	/*
	 * Initialize a new client with all server states
	 */
	public void initClientWithServerStates(RSPWTPClient client) {
		IServerModel model = server.getModel().getServerModel();
		List<IServer> all = new ArrayList<>(model.getServers().values());
		ServerState state = null;
		for( Iterator<IServer> it = all.iterator(); it.hasNext(); ) {
			state = it.next().getDelegate().getServerState();
			client.serverStateChanged(state);
		}
	}
	
	public void serverProcessCreated(ServerHandle server, String processId) {
		List<RSPWTPClient> l = this.server.getClients();
		for( RSPWTPClient c : l) {
			c.serverProcessCreated(new ServerProcess(server, processId));
		}
	}
	
	public void serverProcessTerminated(ServerHandle server, String processId) {
		List<RSPWTPClient> l = this.server.getClients();
		for( RSPWTPClient c : l) {
			c.serverProcessTerminated(new ServerProcess(server, processId));
		}
	}
	
	public void serverProcessOutputAppended(ServerHandle server, String processId, int streamType, String text) {
		List<RSPWTPClient> l = this.server.getClients();
		for( RSPWTPClient c : l) {
			c.serverProcessOutputAppended(new ServerProcessOutput(
					server, processId, streamType, text));
		}
	}
	
	
	
	// To be ignored
	@Override
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void vmChanged(PropertyChangeEvent event) {
		// TODO Auto-generated method stub
		
	}


	private VMDescription getDescription(IVMInstall vmi) {
		String vers = vmi.getJavaVersion();
		return new VMDescription(vmi.getId(), vmi.getInstallLocation().getAbsolutePath(), vers);
	}

	
	@Override
	public void vmAdded(IVMInstall vm) {
//		List<RSPWTPClient> l = server.getClients();
//		for( RSPWTPClient c : l) {
//			c.vmAdded(getDescription(vm));
//		}
	}
	@Override
	public void vmRemoved(IVMInstall vm) {
//		List<RSPWTPClient> l = server.getClients();
//		for( RSPWTPClient c : l) {
//			c.vmRemoved(getDescription(vm));
//		}
	}
	@Override
	public void jobAdded(IJob job) {
		JobHandle jh = new JobHandle(job.getName(), job.getId());
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.jobAdded(jh);
		}
	}
	@Override
	public void jobRemoved(IJob job, IStatus status) {
		JobHandle jh = new JobHandle(job.getName(), job.getId());
		JobRemoved rem = new JobRemoved(jh,  StatusConverter.convert(status));
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.jobRemoved(rem);
		}
	}
	@Override
	public void progressChanged(IJob job, double work) {
		JobProgress progress = new JobProgress(new JobHandle(job.getName(), job.getId()), work, job.getProgressMessage()); 
		List<RSPWTPClient> l = server.getClients();
		for( RSPWTPClient c : l) {
			c.jobChanged(progress);
		}
	}

	@Override
	public void serverPublishStarted(ServerHandle serverHandle) {
		List<RSPWTPClient> clients = server.getClients();
		for (RSPWTPClient client : clients) {
			client.serverPublishStarted(serverHandle);
		}
	}

	@Override
	public void serverPublishFinished(ServerHandle serverHandle) {
		List<RSPWTPClient> clients = server.getClients();
		for (RSPWTPClient client : clients) {
			client.serverPublishFinished(serverHandle);
		}
	}
}
