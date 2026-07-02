/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import com.github.cabutchei.rsp.server.model.ServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IDataStoreModel;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModelFactory;

/**
 * Shared registry for resolving the server-management model factory regardless of
 * transport.
 */
public final class ServerManagementModelFactoryRegistry {
	private static volatile IServerManagementModelFactory modelFactory;

	private ServerManagementModelFactoryRegistry() {
	}

	public static void set(IServerManagementModelFactory factory) {
		modelFactory = factory;
	}

	public static void clear() {
		modelFactory = null;
	}

	public static IServerManagementModel create(IDataStoreModel dataStoreModel) {
		IServerManagementModelFactory factory = modelFactory;
		if (factory != null) {
			return factory.create(dataStoreModel);
		}
		return new ServerManagementModel(dataStoreModel);
	}
}
