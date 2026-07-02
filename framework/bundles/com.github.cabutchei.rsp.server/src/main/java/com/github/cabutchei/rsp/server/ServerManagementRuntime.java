/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;

/**
 * Transport-neutral runtime wrapper around the server-management core.
 */
public class ServerManagementRuntime {
	private static final Object EMBEDDED_LOCK = new Object();
	private static volatile ServerManagementRuntime activeEmbeddedRuntime;

	private final IServerManagementModel managementModel;
	private final ServerManagementServerImpl server;
	private final ServerManagementServerLauncher launcher;
	private final String host;
	private final int port;
	private final AtomicBoolean serversLoaded = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server) {
		this(managementModel, server, null, "localhost", -1);
	}

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server,
			ServerManagementServerLauncher launcher, String host, int port) {
		this.managementModel = managementModel;
		this.server = server;
		this.launcher = launcher;
		this.host = host == null ? "localhost" : host;
		this.port = port;
	}

	public IServerManagementModel getModel() {
		return managementModel;
	}

	public ServerManagementServerImpl getServer() {
		return server;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public boolean isSocketServer() {
		return launcher != null && port > 0;
	}

	public void ensureServersLoaded() throws CoreException {
		if (!serversLoaded.compareAndSet(false, true)) {
			return;
		}
		try {
			managementModel.getServerModel().loadServers();
		} catch (CoreException e) {
			serversLoaded.set(false);
			throw e;
		} catch (RuntimeException e) {
			serversLoaded.set(false);
			throw e;
		}
	}

	public void shutdown() {
		if (!shutdown.compareAndSet(false, true)) {
			return;
		}
		clearIfActiveEmbeddedRuntime(this);
		if (launcher != null) {
			launcher.shutdown();
			return;
		}
		server.dispose();
		try {
			managementModel.getDataStoreModel().unlock();
		} catch (IOException ioe) {
			// ignore shutdown-time unlock failures
		}
		ServerManagementServerImpl.shutdownAsyncExecutor();
		ShutdownExecutor.getExecutor().shutdown();
	}

	public static ServerManagementRuntime bootstrapEmbedded(String instanceId, Integer requestedPort,
			ServerManagementRuntimeOptions options)
			throws CoreException {
		synchronized (EMBEDDED_LOCK) {
			if (activeEmbeddedRuntime != null) {
				return activeEmbeddedRuntime;
			}
			ServerManagementRuntimeOptions resolvedOptions = options == null
					? ServerManagementRuntimeOptions.jdtlsOwnedWorkspaceDefaults()
					: options;
			int resolvedPort = requestedPort == null ? 0 : requestedPort.intValue();
			String launcherId = instanceId == null || instanceId.trim().isEmpty() ? "embedded" : instanceId.trim();
			ServerManagementServerLauncher launcher = ServerCoreActivator.createLauncher(launcherId,
					resolvedOptions.getInitHandlerOptions(), resolvedOptions.isLoadServersOnBootstrap());
			ServerCoreActivator.addDelayedExtensionsToModel();
			try {
				launcher.launch(resolvedPort);
			} catch (CoreException e) {
				throw e;
			} catch (Exception e) {
				throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
						"Failed to launch embedded RSP socket server.", e));
			}
			ServerManagementRuntime runtime = new ServerManagementRuntime(launcher.getModel(), launcher.serverImpl,
					launcher, "localhost", launcher.getBoundPort());
			activeEmbeddedRuntime = runtime;
			return runtime;
		}
	}

	public static ServerManagementRuntime getActiveEmbeddedRuntime() {
		return activeEmbeddedRuntime;
	}

	public static void shutdownEmbedded() {
		synchronized (EMBEDDED_LOCK) {
			ServerManagementRuntime runtime = activeEmbeddedRuntime;
			activeEmbeddedRuntime = null;
			if (runtime != null) {
				runtime.shutdown();
			}
		}
	}

	private static void clearIfActiveEmbeddedRuntime(ServerManagementRuntime runtime) {
		synchronized (EMBEDDED_LOCK) {
			if (activeEmbeddedRuntime == runtime) {
				activeEmbeddedRuntime = null;
			}
		}
	}
}
