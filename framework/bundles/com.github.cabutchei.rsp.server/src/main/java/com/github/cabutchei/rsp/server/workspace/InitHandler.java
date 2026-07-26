/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.workspace;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.InitializeParams;
import com.github.cabutchei.rsp.api.dao.InitializeResult;
import com.github.cabutchei.rsp.api.dao.Status;
import com.github.cabutchei.rsp.api.dao.WorkspaceFolder;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPConfiguration;

public class InitHandler {
	private final IServerManagementModel managementModel;
	private final Supplier<IProjectsManager> projectsManagerSupplier;
	private final InitHandlerOptions options;
	private final AtomicBoolean serversLoaded = new AtomicBoolean(false);

	public InitHandler(IServerManagementModel managementModel, IProjectsManager projectsManager) {
		this(managementModel, projectsManager, InitHandlerOptions.externalSocketDefaults());
	}

	public InitHandler(IServerManagementModel managementModel, IProjectsManager projectsManager,
			InitHandlerOptions options) {
		this(managementModel, () -> projectsManager, options);
	}

	public InitHandler(IServerManagementModel managementModel, Supplier<IProjectsManager> projectsManagerSupplier,
			InitHandlerOptions options) {
		this.managementModel = managementModel;
		this.projectsManagerSupplier = projectsManagerSupplier;
		this.options = options == null ? InitHandlerOptions.externalSocketDefaults() : options;
	}

	public InitializeResult initialize(InitializeParams params) {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return new InitializeResult(errorStatus("Projects manager unavailable"), Collections.emptyList());
		}
		// List<Path> workspaceRoots = toPaths(params == null ? null : params.getWorkspaceFolders());
		// projectsManager.initializeProjects(workspaceRoots);
		IStatus autoBuildStatus = configureAutoBuilding();
		if (!autoBuildStatus.isOK()) {
			return new InitializeResult(StatusConverter.convert(autoBuildStatus), projectsManager.getWatchPatterns());
		}
		IStatus autoPublishStatus = configureAutoPublishing();
		if (!autoPublishStatus.isOK()) {
			return new InitializeResult(StatusConverter.convert(autoPublishStatus), projectsManager.getWatchPatterns());
		}
		// IStatus loadStatus = ensureServersLoaded();
		// if (!loadStatus.isOK()) {
		// 	return new InitializeResult(StatusConverter.convert(loadStatus), projectsManager.getWatchPatterns());
		// }
		// projectsManager.syncDeployableWatchPatterns(collectActiveDeployables());
		return new InitializeResult(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS),
				projectsManager.getWatchPatterns());
	}

	private List<DeployableReference> collectActiveDeployables() {
		IServerModel serverModel = managementModel == null ? null : managementModel.getServerModel();
		if (serverModel == null || serverModel.getServers() == null || serverModel.getServers().isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, DeployableReference> deployables = new LinkedHashMap<>();
		for (IServer server : serverModel.getServers().values()) {
			if (server == null) {
				continue;
			}
			List<DeployableState> states = serverModel.getDeployables(server);
			if (states == null) {
				continue;
			}
			for (DeployableState state : states) {
				DeployableReference reference = state == null ? null : state.getReference();
				if (reference == null || reference.getPath() == null || reference.getPath().isBlank()) {
					continue;
				}
				deployables.put(reference.getLabel() + "|" + reference.getPath(), new DeployableReference(reference));
			}
		}
		return new ArrayList<>(deployables.values());
	}

	private IStatus configureAutoBuilding() {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR,
					ServerCoreActivator.BUNDLE_ID, "Projects manager unavailable");
		}
		Boolean autoBuilding = options.getAutoBuilding();
		if (autoBuilding == null) {
			return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		}
		return projectsManager.setAutoBuilding(autoBuilding.booleanValue());
	}

	private IStatus configureAutoPublishing() {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR,
					ServerCoreActivator.BUNDLE_ID, "Projects manager unavailable");
		}
		Boolean autoPublishing = options.getAutoPublishing();
		if (autoPublishing == null) {
			return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		}
		IWTPConfiguration wtpConfiguration = projectsManager.getWTPService();
		if (wtpConfiguration == null) {
			return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		}
		IStatus globalStatus = wtpConfiguration.setGlobalAutoPublishing(autoPublishing.booleanValue());
		if (globalStatus != null && !globalStatus.isOK()) {
			return globalStatus;
		}
		IStatus perServerStatus = wtpConfiguration.setAutoPublishingForAllServers(autoPublishing.booleanValue());
		if (perServerStatus != null && !perServerStatus.isOK()) {
			return perServerStatus;
		}
		return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
	}

	private IStatus ensureServersLoaded() {
		if (!serversLoaded.compareAndSet(false, true)) {
			return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		}
		try {
			managementModel.getServerModel().loadServers();
			return com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		} catch (CoreException e) {
			serversLoaded.set(false);
			return e.getStatus();
		} catch (Exception e) {
			serversLoaded.set(false);
			return new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR,
					ServerCoreActivator.BUNDLE_ID, "Failed to load servers", e);
		}
	}

	private List<Path> toPaths(List<WorkspaceFolder> folders) {
		if (folders == null || folders.isEmpty()) {
			return Collections.emptyList();
		}
		List<Path> roots = new ArrayList<>();
		for (WorkspaceFolder folder : folders) {
			Path path = toPath(folder == null ? null : folder.getUri());
			if (path != null) {
				roots.add(path);
			}
		}
		return roots;
	}

	private Path toPath(String uri) {
		if (uri == null || uri.isBlank()) {
			return null;
		}
		try {
			java.net.URI parsed = new java.net.URI(uri);
			if (parsed.getScheme() == null) {
				return Paths.get(uri).toAbsolutePath().normalize();
			}
			if ("file".equalsIgnoreCase(parsed.getScheme())) {
				return Paths.get(parsed).toAbsolutePath().normalize();
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	private Status errorStatus(String message) {
		return StatusConverter.convert(new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR,
				ServerCoreActivator.BUNDLE_ID, message));
	}

	private IProjectsManager getProjectsManager() {
		return projectsManagerSupplier == null ? null : projectsManagerSupplier.get();
	}
}
