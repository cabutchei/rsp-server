/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import com.github.cabutchei.rsp.eclipse.osgi.util.NLS;
import com.github.cabutchei.rsp.server.spi.model.DelayedExtensionManager;
import com.github.cabutchei.rsp.server.spi.model.DelayedExtensionManager.IDelayedExtension;
import com.github.cabutchei.rsp.server.workspace.InitHandlerOptions;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerCoreActivator implements BundleActivator {

	public static final String BUNDLE_ID = "com.github.cabutchei.rsp.server";
	private static final Logger LOG = LoggerFactory.getLogger(ServerCoreActivator.class);
	private static volatile ILauncherFactory launcherFactory;
	private BundleContext context;

	@FunctionalInterface
	public interface ILauncherFactory {
		ServerManagementServerLauncher createLauncher(String portString, InitHandlerOptions initHandlerOptions,
				boolean loadServersOnLaunch);
	}

	public static void setLauncherFactory(ILauncherFactory factory) {
		launcherFactory = factory;
	}

	public static void clearLauncherFactory() {
		launcherFactory = null;
	}

	@Override
	public void start(final BundleContext context) throws Exception {
		this.context = context;
		ShutdownExecutor.getExecutor().setHandler(() -> { performStop(); });
		if (RSPFlags.isServerAutostartEnabled()) {
			startServer();
		} else {
			LOG.info("RSP bundle activated with auto-start disabled.");
		}
		LOG.debug(NLS.bind("{0} bundle started.", BUNDLE_ID));
	}

	public ServerManagementServerLauncher getLauncher() {
		return LauncherSingleton.getDefault().getLauncher();
	}

	private int getPort() {
		return RSPFlags.getServerPort();
	}

	public static ServerManagementServerLauncher createLauncher(String portString,
			InitHandlerOptions initHandlerOptions, boolean loadServersOnLaunch) {
		ILauncherFactory factory = launcherFactory;
		return factory != null
				? factory.createLauncher(portString, initHandlerOptions, loadServersOnLaunch)
				: new ServerManagementServerLauncher(portString, initHandlerOptions, loadServersOnLaunch);
	}

	private ServerManagementServerLauncher resolveLauncher(int port) {
		ServerManagementServerLauncher launcher = LauncherSingleton.getDefault().getLauncher();
		if (launcher != null) {
			return launcher;
		}
		ServerManagementServerLauncher created = createLauncher(String.valueOf(port),
				InitHandlerOptions.externalSocketDefaults(), true);
		LauncherSingleton.getDefault().setLauncher(created);
		return created;
	}

	private void startServer() {
		int port = getPort();
		ServerManagementServerLauncher launcher = null;
		
		try {
			launcher = resolveLauncher(port);
		} catch(RuntimeException re) {
			LOG.error("Unable to launch RSP server", re);
			performStop();
			return;
		}
		ServerManagementServerLauncher launcher2 = launcher;
		Thread serverThread = new Thread(() -> {
				addDelayedExtensionsToModel();
				try {
					launcher2.launch(port);
				} catch (Exception e) {
					LOG.error("Unable to launch RSP server", e);
				}
			}, 
			"Launch RSP Server");
		serverThread.start();
	}

	public static void addDelayedExtensionsToModel() {
		IDelayedExtension[] addToModel = DelayedExtensionManager.getDefault().getDelayedExtensions();
		for( int i = 0; i < addToModel.length; i++ ) {
			addToModel[i].addExtensionsToModel();
		}
	}

	private void performStop() {
		try {
			context.getBundle(0).stop();
		} catch (BundleException e) {
			LOG.error(NLS.bind("Stopping bundle {0} failed.", BUNDLE_ID), e);
		}
	}
	@Override
	public void stop(BundleContext context) throws Exception {
		LOG.debug(NLS.bind("{0} bundle stopped.", BUNDLE_ID));
	}
}
