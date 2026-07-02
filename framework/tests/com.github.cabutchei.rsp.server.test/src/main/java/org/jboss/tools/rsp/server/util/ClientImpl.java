/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.util;
/* --------------------------------------------------------------------------------------------
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */

import java.util.concurrent.CompletableFuture;

import com.github.cabutchei.rsp.api.RSPClient;
import com.github.cabutchei.rsp.api.RSPWTPServer;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DiscoveryPath;
import com.github.cabutchei.rsp.api.dao.JobHandle;
import com.github.cabutchei.rsp.api.dao.JobProgress;
import com.github.cabutchei.rsp.api.dao.JobRemoved;
import com.github.cabutchei.rsp.api.dao.MessageBoxNotification;
import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerProcess;
import com.github.cabutchei.rsp.api.dao.ServerProcessOutput;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.StringPrompt;

public class ClientImpl implements RSPClient {
	
public RSPWTPServer server;
	
	
public void initialize(RSPWTPServer server) throws Exception {
	this.server = server;
}

public RSPWTPServer getProxy() {
	return server;
}

	@Override
	public void discoveryPathAdded(DiscoveryPath message) {
		System.out.println("Added discovery path: " + message.getFilepath());
	}

	@Override
	public void discoveryPathRemoved(DiscoveryPath message) {
		System.out.println("Removed discovery path: " + message.getFilepath());
	}
	
	@Override
	public void serverAdded(ServerHandle server) {
		System.out.println("Server added: " + server.getType() + ":" + server.getId());
	}

	@Override
	public void serverRemoved(ServerHandle server) {
		System.out.println("Server removed: " + server.getType() + ":" + server.getId());
	}

	@Override
	public void serverAttributesChanged(ServerHandle server) {
		System.out.println("Server attribute changed: " + server.getType() + ":" + server.getId());
	}
	@Override
	public void messageBox(MessageBoxNotification notify) {
		System.out.println("MessageBoxNotification: " + notify.getMessage());
	}
	@Override
	public void serverStateChanged(ServerState state) {
		String stateString = null;
		switch(state.getState()) {
		case ServerManagementAPIConstants.STATE_STARTED:
			stateString = "started";
			break;
		case ServerManagementAPIConstants.STATE_STARTING:
			stateString = "starting";
			break;
		case ServerManagementAPIConstants.STATE_STOPPED:
			stateString = "stopped";
			break;
		case ServerManagementAPIConstants.STATE_STOPPING:
			stateString = "stopping";
			break;
			
		}
		System.out.println("Server state changed: " + state.getServer().getType() + ":" + state.getServer().getId() + " to " + stateString);
	}

	@Override
	public void serverProcessCreated(ServerProcess process) {
		System.out.println("Server process created: " + 
				process.getServer().getType() + ":" + process.getServer().getId() + " @ " 
				+ process.getProcessId());
	}

	@Override
	public void serverProcessTerminated(ServerProcess process) {
		System.out.println("Server process terminated: " 
				+ process.getServer().getType() + ":" + process.getServer().getId() + " @ " 
				+ process.getProcessId());
	}

	@Override
	public void serverProcessOutputAppended(ServerProcessOutput out) {
		System.out.println("ServerOutput: " 
				+ out.getServer().toString() + " ["
				+ out.getProcessId() + "][" 
				+ out.getStreamType() + "] " + out.getText());
	}

	@Override
	public CompletableFuture<String> promptString(StringPrompt prompt) {
		return CompletableFuture.completedFuture("this_is_a_password"); 
	}

	@Override
	public void jobAdded(JobHandle job) {
		System.out.println("Job " + job.getName() + " (" + job.getId() + ") is now running.");
	}

	@Override
	public void jobRemoved(JobRemoved removed) {
		JobHandle h = removed.getHandle();
		System.out.println("Job " + h.getName() + " (" + h.getId() + ") has stopped running: " + removed.getStatus().toString());
	}

	@Override
	public void jobChanged(JobProgress progress) {
		JobHandle h = progress.getHandle();
		System.out.println("Job " + h.getName() + " (" + h.getId() + ") is at " + progress.getPercent() + "%");
	}

}
