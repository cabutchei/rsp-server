/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import com.github.cabutchei.rsp.server.workspace.InitHandlerOptions;

/**
 * Options for bootstrapping an embedded server-management runtime.
 */
public class ServerManagementRuntimeOptions {
	private final boolean loadServersOnBootstrap;
	private final InitHandlerOptions initHandlerOptions;

	public ServerManagementRuntimeOptions(boolean loadServersOnBootstrap, InitHandlerOptions initHandlerOptions) {
		this.loadServersOnBootstrap = loadServersOnBootstrap;
		this.initHandlerOptions = initHandlerOptions == null
				? InitHandlerOptions.externalSocketDefaults()
				: initHandlerOptions;
	}

	public boolean isLoadServersOnBootstrap() {
		return loadServersOnBootstrap;
	}

	public InitHandlerOptions getInitHandlerOptions() {
		return initHandlerOptions;
	}

	public static ServerManagementRuntimeOptions jdtlsOwnedWorkspaceDefaults() {
		return new ServerManagementRuntimeOptions(false, InitHandlerOptions.jdtlsOwnedWorkspaceDefaults());
	}
}
