/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.workspace;

	import static org.junit.Assert.assertFalse;
	import static org.junit.Assert.assertEquals;
	import static org.junit.Assert.assertTrue;
	import static org.mockito.Mockito.never;
	import static org.mockito.ArgumentMatchers.eq;
	import static org.mockito.Mockito.mock;
	import static org.mockito.Mockito.verify;
	import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.github.cabutchei.rsp.api.dao.ExportEarRequest;
import com.github.cabutchei.rsp.api.dao.InitializeParams;
import com.github.cabutchei.rsp.api.dao.InitializeResult;
import com.github.cabutchei.rsp.api.dao.Status;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.server.ServerManagementServerImpl;
import com.github.cabutchei.rsp.server.model.RemoteEventManager;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.model.IWorkspaceModelCapability;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPService;

public class WorkspaceOperationsTest {
	private IServerManagementModel managementModel;
	private IProjectsManager projectsManager;
	private IWTPService wtpService;

	@Before
	public void setUp() {
		managementModel = mock(IServerManagementModel.class, withSettings().extraInterfaces(IWorkspaceModelCapability.class));
		projectsManager = mock(IProjectsManager.class);
		wtpService = mock(IWTPService.class);
		when(((IWorkspaceModelCapability) managementModel).getProjectsManager()).thenReturn(projectsManager);
		when(projectsManager.getWTPService()).thenReturn(wtpService);
		when(projectsManager.setAutoBuilding(false)).thenReturn(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS);
		when(projectsManager.getWatchPatterns()).thenReturn(Collections.emptyList());
	}

	@Test
	public void testExportEarDelegatesToWtpService() throws Exception {
		when(wtpService.exportEar(eq(Paths.get("/workspace/sample-ear").toAbsolutePath().normalize()), eq("sample-ear"),
				eq(Paths.get("/tmp/sample.ear").toAbsolutePath().normalize()), eq(true)))
				.thenReturn(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS);

		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		Status result = rsp.exportEar(new ExportEarRequest("/workspace/sample-ear", "sample-ear", "/tmp/sample.ear", true))
				.get();

		assertTrue(result.isOK());
		verify(wtpService).exportEar(Paths.get("/workspace/sample-ear").toAbsolutePath().normalize(), "sample-ear",
				Paths.get("/tmp/sample.ear").toAbsolutePath().normalize(), true);
	}

	@Test
	public void testExportEarRejectsMissingDestination() throws Exception {
		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		Status result = rsp.exportEar(new ExportEarRequest("/workspace/sample-ear", "sample-ear", "", false)).get();

		assertFalse(result.isOK());
		assertTrue(result.getMessage().length() > 0);
	}

	@Test
	public void testExportEarPropagatesWtpServiceFailure() throws Exception {
		when(wtpService.exportEar(eq(Paths.get("/workspace/sample-ear").toAbsolutePath().normalize()), eq("sample-ear"),
				eq(Paths.get("/tmp/sample.ear").toAbsolutePath().normalize()), eq(false)))
				.thenReturn(new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR, "test.bundle", "boom"));

		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		Status result = rsp.exportEar(new ExportEarRequest("/workspace/sample-ear", "sample-ear", "/tmp/sample.ear", false))
				.get();

		assertFalse(result.isOK());
		assertTrue(result.getMessage().contains("boom"));
	}

	@Test
	public void testInitializeFailsWhenProjectsManagerUnavailable() throws Exception {
		IServerModel serverModel = mock(IServerModel.class);
		when(managementModel.getServerModel()).thenReturn(serverModel);
		when(((IWorkspaceModelCapability) managementModel).getProjectsManager()).thenReturn(null);

		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		InitializeResult result = rsp.initialize(new InitializeParams()).get();

		assertFalse(result.getStatus().isOK());
		verify(projectsManager, never()).initializeProjects(Collections.emptyList());
		verify(serverModel, never()).loadServers();
	}
}
