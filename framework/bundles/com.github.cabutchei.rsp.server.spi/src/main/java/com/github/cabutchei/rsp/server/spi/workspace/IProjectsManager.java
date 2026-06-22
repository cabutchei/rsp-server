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
import java.util.List;

import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;

public interface IProjectsManager extends IWTPServiceProvider {
	/**
	 * Import the project located at the given project root.
	 */
	IStatus importProject(Path projectRoot);

	/**
	 * Import all discoverable projects under tracked workspace roots.
	 */
	IStatus importAllWorkspaceProjects();

	/**
	 * Import projects from the provided roots.
	 */
	IStatus importProjects(List<Path> projectRoots);

	/**
	 * Import projects from the provided roots.
	 */
	IStatus importProjects(Path[] projectRoots);

	/**
	 * Refresh a project from disk.
	 */
	IStatus refreshProject(String projectName);

	/**
	 * Enable or disable workspace auto-building.
	 */
	IStatus setAutoBuilding(boolean enabled);

	/**
	 * Initialize the workspace projects from the provided workspace roots.
	 * 
	 * @param workspaceRoots workspace roots to initialize
	 */
	void initializeProjects(Collection<Path> workspaceRoots);

	/**
	 * Update the workspace folders that should be considered part of the client workspace.
	 * 
	 * @param added newly added workspace roots
	 * @param removed removed workspace roots
	 */
	void updateWorkspaceFolders(Collection<Path> added, Collection<Path> removed);

	/**
	 * List workspace projects.
	 */
	List<WorkspaceProject> listWorkspaceProjects();

	/**
	 * Scan workspace projects for non-standard JRE containers that resolve to a VM
	 * install.
	 */
	List<JreContainerMapping> listNonStandardJreContainers();

	/**
	 * List glob-like watch patterns the client should monitor.
	 */
	List<String> getWatchPatterns();

	/**
	 * React to a watched-file change.
	 */
	IStatus fileChanged(Path path, int changeType);

	/**
	 * List classpath containers from workspace Java projects, including their
	 * resolved entries.
	 */
	List<ClasspathContainerMapping> listClasspathContainers();

	/**
	 * Register a listener for workspace classpath container changes.
	 */
	default void addClasspathContainersChangedListener(Runnable listener) {
	}

	/**
	 * Remove a previously registered workspace classpath container change listener.
	 */
	default void removeClasspathContainersChangedListener(Runnable listener) {
	}

	/**
	 * @return true if the workspace has been initialized
	 */
	boolean isInitialized();
}
