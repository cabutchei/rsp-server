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
import com.github.cabutchei.rsp.server.spi.model.IWorkspaceModelCapability;

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
	private final String logFilePath;
	private final boolean wstPublishWatcherEnabled;
	private final AtomicBoolean serversLoaded = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server) {
		this(managementModel, server, null, "localhost", -1, null, RSPFlags.isWstPublishWatcherEnabled());
	}

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server,
			ServerManagementServerLauncher launcher, String host, int port) {
		this(managementModel, server, launcher, host, port, null, RSPFlags.isWstPublishWatcherEnabled());
	}

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server,
			ServerManagementServerLauncher launcher, String host, int port, String logFilePath) {
		this(managementModel, server, launcher, host, port, logFilePath, RSPFlags.isWstPublishWatcherEnabled());
	}

	public ServerManagementRuntime(IServerManagementModel managementModel, ServerManagementServerImpl server,
			ServerManagementServerLauncher launcher, String host, int port, String logFilePath,
			boolean wstPublishWatcherEnabled) {
		this.managementModel = managementModel;
		this.server = server;
		this.launcher = launcher;
		this.host = host == null ? "localhost" : host;
		this.port = port;
		this.logFilePath = logFilePath;
		this.wstPublishWatcherEnabled = wstPublishWatcherEnabled;
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

	public String getLogFilePath() {
		return logFilePath;
	}

	public boolean isWstPublishWatcherEnabled() {
		return wstPublishWatcherEnabled;
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
		shutdown(true);
	}

	void shutdown(boolean stopFramework) {
		if (!shutdown.compareAndSet(false, true)) {
			return;
		}
		clearIfActiveEmbeddedRuntime(this);
		if (launcher != null) {
			launcher.shutdown(false);
			if (LauncherSingleton.getDefault().getLauncher() == launcher) {
				LauncherSingleton.getDefault().setLauncher(null);
			}
			EmbeddedRuntimeLog.close();
			return;
		}
		server.dispose();
		try {
			managementModel.getDataStoreModel().unlock();
		} catch (IOException ioe) {
			// ignore shutdown-time unlock failures
		}
		ServerManagementServerImpl.shutdownAsyncExecutor();
		if (stopFramework) {
			ShutdownExecutor.getExecutor().shutdown();
		}
		EmbeddedRuntimeLog.close();
	}

	public static ServerManagementRuntime bootstrapEmbedded(String instanceId, Integer requestedPort,
			ServerManagementRuntimeOptions options)
			throws CoreException {
		synchronized (EMBEDDED_LOCK) {
			ServerManagementRuntimeOptions resolvedOptions = options == null
					? ServerManagementRuntimeOptions.jdtlsOwnedWorkspaceDefaults()
					: options;
			if (activeEmbeddedRuntime != null) {
				if (resolvedOptions.requiresWorkspaceModelCapability() && !isWorkspaceCapable(activeEmbeddedRuntime)) {
					EmbeddedRuntimeLog.append("[embedded] restarting stale runtime with non-workspace model: "
							+ describeModel(activeEmbeddedRuntime.getModel()));
					ServerManagementRuntime staleRuntime = activeEmbeddedRuntime;
					activeEmbeddedRuntime = null;
					staleRuntime.shutdown();
				} else if (activeEmbeddedRuntime.isWstPublishWatcherEnabled()
						!= resolvedOptions.isWstPublishWatcherEnabled()) {
					EmbeddedRuntimeLog.append("[embedded] restarting stale runtime with mismatched WST publish watcher mode. "
							+ "active=" + activeEmbeddedRuntime.isWstPublishWatcherEnabled() + ", requested="
							+ resolvedOptions.isWstPublishWatcherEnabled());
					ServerManagementRuntime staleRuntime = activeEmbeddedRuntime;
					activeEmbeddedRuntime = null;
					staleRuntime.shutdown();
				} else {
					applyRuntimeSystemProperties(resolvedOptions);
					return activeEmbeddedRuntime;
				}
			}
			try {
				EmbeddedRuntimeLog.configure(resolvedOptions.getLogFilePath());
			} catch (IOException ioe) {
				throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
						"Failed to initialize embedded runtime log.", ioe));
			}
			int resolvedPort = requestedPort == null ? 0 : requestedPort.intValue();
			String launcherId = instanceId == null || instanceId.trim().isEmpty() ? "embedded" : instanceId.trim();
			ServerManagementServerLauncher launcher = ServerCoreActivator.createLauncher(launcherId);
			IServerManagementModel model = launcher.getModel();
			EmbeddedRuntimeLog.append("[embedded] created launcher " + launcher.getClass().getName()
					+ " with management model " + describeModel(model));
			if (resolvedOptions.requiresWorkspaceModelCapability() && !isWorkspaceCapable(model)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
						"Embedded bootstrap did not create a workspace-capable management model. Got "
								+ describeModel(model) + "."));
			}
			try {
				applyRuntimeSystemProperties(resolvedOptions);
				LauncherSingleton.getDefault().setLauncher(launcher);
				ServerCoreActivator.addDelayedExtensionsToModel();
				launcher.launch(resolvedPort);
			} catch (CoreException e) {
				EmbeddedRuntimeLog.append("[embedded] bootstrap failed: " + e.getMessage());
				if (LauncherSingleton.getDefault().getLauncher() == launcher) {
					LauncherSingleton.getDefault().setLauncher(null);
				}
				throw e;
			} catch (Exception e) {
				EmbeddedRuntimeLog.append("[embedded] bootstrap failed: " + e.getMessage());
				if (LauncherSingleton.getDefault().getLauncher() == launcher) {
					LauncherSingleton.getDefault().setLauncher(null);
				}
				throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
						"Failed to launch embedded RSP socket server.", e));
			}
			ServerManagementRuntime runtime = new ServerManagementRuntime(launcher.getModel(), launcher.serverImpl,
					launcher, "localhost", launcher.getBoundPort(), EmbeddedRuntimeLog.getPath(),
					resolvedOptions.isWstPublishWatcherEnabled());
			EmbeddedRuntimeLog.append("[embedded] socket server listening on localhost:" + launcher.getBoundPort());
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

	private static boolean isWorkspaceCapable(ServerManagementRuntime runtime) {
		return runtime != null && isWorkspaceCapable(runtime.getModel());
	}

	private static boolean isWorkspaceCapable(IServerManagementModel model) {
		return model instanceof IWorkspaceModelCapability;
	}

	private static String describeModel(IServerManagementModel model) {
		return model == null ? "<null>" : model.getClass().getName();
	}

	private static void applyRuntimeSystemProperties(ServerManagementRuntimeOptions options) {
		if (options == null) {
			return;
		}
		System.setProperty(RSPFlags.SYSPROP_WST_PUBLISH_WATCHER_ENABLED,
				Boolean.toString(options.isWstPublishWatcherEnabled()));
	}
}
