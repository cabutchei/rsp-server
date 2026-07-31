/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.spi.workspace;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;

/**
 * WTP-specific project/deployable operations.
 */
public interface IWTPService extends IWTPConfiguration {
	/**
	 * Set current workspace roots for filtering workspace-scoped results.
	 */
	void setWorkspaceRoots(Collection<Path> workspaceRoots);

	void invalidateDeployableResourceCache();

	void invalidateDeployableResourceCache(ServerHandle server);

	List<DeployableArtifact> listDeployableResources(ServerHandle server);

	List<WorkspaceProject> listEarProjects();

	List<WorkspaceProject> listDeploymentAssemblyProjects(Path projectPath, String projectName);

	List<DeploymentAssemblyEntry> getDeploymentAssembly(Path projectPath, String projectName);

	IStatus exportEar(Path projectPath, String projectName, Path destinationPath, boolean exportSource);

	default IStatus exportEar(Path projectPath, String projectName, Path destinationPath, boolean exportSource,
			com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor monitor) {
		return exportEar(projectPath, projectName, destinationPath, exportSource);
	}

	IStatus addDeploymentAssemblyEntry(Path projectPath, String projectName, DeploymentAssemblyEntry entry);

	IStatus removeDeploymentAssemblyEntry(Path projectPath, String projectName, DeploymentAssemblyEntry entry);

	IStatus ensureFacets(String projectName, List<String> facetIds);

	IStatus updateFacets(String projectName, List<String> add, List<String> remove);

	default Set<Path> getDeploymentWatchPaths(Path deployablePath, String projectName) {
		if (deployablePath == null) {
			return Collections.emptySet();
		}
		Set<Path> single = new LinkedHashSet<>();
		single.add(deployablePath.toAbsolutePath().normalize());
		return single;
	}

}
