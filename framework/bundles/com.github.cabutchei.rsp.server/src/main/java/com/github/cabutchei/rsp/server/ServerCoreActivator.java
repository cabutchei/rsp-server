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
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerCoreActivator implements BundleActivator {

	public static final String BUNDLE_ID = "com.github.cabutchei.rsp.server";
	private static final Logger LOG = LoggerFactory.getLogger(ServerCoreActivator.class);
	private static volatile ILauncherFactory launcherFactory;

	@FunctionalInterface
	public interface ILauncherFactory {
		ServerManagementServerLauncher createLauncher(String portString);
	}

	public static void setLauncherFactory(ILauncherFactory factory) {
		launcherFactory = factory;
	}

	public static void clearLauncherFactory() {
		launcherFactory = null;
	}

	@Override
	public void start(final BundleContext context) throws Exception {
		LOG.info("RSP bundle activated. Startup is managed exclusively by the embedded runtime.");
		LOG.debug(NLS.bind("{0} bundle started.", BUNDLE_ID));
	}

	public static ServerManagementServerLauncher createLauncher(String portString) {
		ILauncherFactory factory = launcherFactory;
		return factory != null
				? factory.createLauncher(portString)
				: new ServerManagementServerLauncher(portString);
	}

	public static void addDelayedExtensionsToModel() {
		IDelayedExtension[] addToModel = DelayedExtensionManager.getDefault().getDelayedExtensions();
		for( int i = 0; i < addToModel.length; i++ ) {
			addToModel[i].addExtensionsToModel();
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		LOG.debug(NLS.bind("{0} bundle stopped.", BUNDLE_ID));
	}
}
