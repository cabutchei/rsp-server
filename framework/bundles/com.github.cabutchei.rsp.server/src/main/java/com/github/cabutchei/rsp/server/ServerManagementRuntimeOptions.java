/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

/**
 * Options for bootstrapping an embedded server-management runtime.
 */
public class ServerManagementRuntimeOptions {
	private final boolean loadServersOnBootstrap;
	private final String logFilePath;
	private final boolean requireWorkspaceModelCapability;

	public ServerManagementRuntimeOptions(boolean loadServersOnBootstrap) {
		this(loadServersOnBootstrap, null, false);
	}

	public ServerManagementRuntimeOptions(boolean loadServersOnBootstrap, String logFilePath) {
		this(loadServersOnBootstrap, logFilePath, false);
	}

	public ServerManagementRuntimeOptions(boolean loadServersOnBootstrap, String logFilePath,
			boolean requireWorkspaceModelCapability) {
		this.loadServersOnBootstrap = loadServersOnBootstrap;
		this.logFilePath = logFilePath;
		this.requireWorkspaceModelCapability = requireWorkspaceModelCapability;
	}

	public boolean isLoadServersOnBootstrap() {
		return loadServersOnBootstrap;
	}

	public String getLogFilePath() {
		return logFilePath;
	}

	public boolean requiresWorkspaceModelCapability() {
		return requireWorkspaceModelCapability;
	}

	public static ServerManagementRuntimeOptions jdtlsOwnedWorkspaceDefaults() {
		return jdtlsOwnedWorkspaceDefaults(null);
	}

	public static ServerManagementRuntimeOptions jdtlsOwnedWorkspaceDefaults(String logFilePath) {
		return new ServerManagementRuntimeOptions(false, logFilePath, true);
	}
}
