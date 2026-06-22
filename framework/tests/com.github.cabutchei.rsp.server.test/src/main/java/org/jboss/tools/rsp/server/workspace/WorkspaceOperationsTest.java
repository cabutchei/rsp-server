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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.github.cabutchei.rsp.api.RSPWTPClient;
import com.github.cabutchei.rsp.api.SocketLauncher;
import com.github.cabutchei.rsp.api.dao.ClasspathContainerMappings;
import com.github.cabutchei.rsp.api.dao.ExportEarRequest;
import com.github.cabutchei.rsp.api.dao.Status;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.server.ServerManagementServerImpl;
import com.github.cabutchei.rsp.server.model.RemoteEventManager;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IWorkspaceModelCapability;
import com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerEntry;
import com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerMapping;
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
	public void testClasspathContainerListenerNotifiesConnectedClients() {
		ArgumentCaptor<Runnable> listenerCaptor = ArgumentCaptor.forClass(Runnable.class);
		when(projectsManager.listClasspathContainers()).thenReturn(Arrays.asList(new ClasspathContainerMapping(
				"sample-web",
				"file:///workspace/sample-web",
				"org.eclipse.jst.j2ee.internal.web.container/sample-web",
				"Web App Libraries",
				Arrays.asList(new ClasspathContainerEntry(1, "/tmp/a.jar", null, null, null, false)))));

		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		verify(projectsManager).addClasspathContainersChangedListener(listenerCaptor.capture());

		RSPWTPClient client = mock(RSPWTPClient.class);
		doNothing().when(client).jdtlsClasspathContainersDetected(any(ClasspathContainerMappings.class));
		SocketLauncher<RSPWTPClient> launcher = mock(SocketLauncher.class);
		when(launcher.getRemoteProxy()).thenReturn(client);
		rsp.addClient(launcher);

		listenerCaptor.getValue().run();

		ArgumentCaptor<ClasspathContainerMappings> mappingsCaptor = ArgumentCaptor.forClass(ClasspathContainerMappings.class);
		verify(client).jdtlsClasspathContainersDetected(mappingsCaptor.capture());
		assertEquals(1, mappingsCaptor.getValue().getMappings().size());
		assertEquals("org.eclipse.jst.j2ee.internal.web.container/sample-web",
				mappingsCaptor.getValue().getMappings().get(0).getContainerPath());
	}

	@Test
	public void testClasspathContainerListenerSendsEmptySnapshot() {
		ArgumentCaptor<Runnable> listenerCaptor = ArgumentCaptor.forClass(Runnable.class);
		when(projectsManager.listClasspathContainers()).thenReturn(Collections.emptyList());

		ServerManagementServerImpl rsp = new ServerManagementServerImpl(null, managementModel) {
			@Override
			protected RemoteEventManager createRemoteEventManager() {
				return null;
			}
		};

		verify(projectsManager).addClasspathContainersChangedListener(listenerCaptor.capture());

		RSPWTPClient client = mock(RSPWTPClient.class);
		doNothing().when(client).jdtlsClasspathContainersDetected(any(ClasspathContainerMappings.class));
		SocketLauncher<RSPWTPClient> launcher = mock(SocketLauncher.class);
		when(launcher.getRemoteProxy()).thenReturn(client);
		rsp.addClient(launcher);

		listenerCaptor.getValue().run();

		ArgumentCaptor<ClasspathContainerMappings> mappingsCaptor = ArgumentCaptor.forClass(ClasspathContainerMappings.class);
		verify(client).jdtlsClasspathContainersDetected(mappingsCaptor.capture());
		assertTrue(mappingsCaptor.getValue().getMappings().isEmpty());
	}
}
