/*******************************************************************************
 * Copyright (c) 2018-2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.util;

import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;

public class TestServerDelegate extends AbstractServerDelegate {

	public TestServerDelegate(IServer server) {
		super(server);
	}

	@Override
	protected void fireStateChanged(ServerState state) {
		// Do nothing
	}

}
