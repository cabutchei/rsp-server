/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.spi.model;

import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerState;

public class ServerModelListenerAdapter implements IServerModelListener {

	@Override
	public void serverAdded(ServerHandle server) {
	}

	@Override
	public void serverRemoved(ServerHandle server) {
	}

	@Override
	public void serverAttributesChanged(ServerHandle server) {
	}

	@Override
	public void serverStateChanged(ServerHandle server, ServerState state) {
	}

	@Override
	public void serverProcessCreated(ServerHandle server, String processId) {
	}

	@Override
	public void serverProcessTerminated(ServerHandle server, String processId) {
	}

	@Override
	public void serverProcessOutputAppended(ServerHandle server, String processId, int streamType, String text) {
	}

	@Override
	public void serverPublishStarted(ServerHandle server) {
	}

	@Override
	public void serverPublishFinished(ServerHandle server) {
	}

}
