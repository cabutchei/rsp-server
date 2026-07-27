package com.github.cabutchei.rsp.eclipse.wst.model;

import java.util.Collections;
import java.util.Objects;

import com.github.cabutchei.rsp.eclipse.workspace.ProjectsManager;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerCore;
import com.github.cabutchei.rsp.eclipse.wst.wtp.WTPService;
import com.github.cabutchei.rsp.server.model.ServerManagementModel;
import com.github.cabutchei.rsp.server.spi.filewatcher.IFileWatcherService;
import com.github.cabutchei.rsp.server.spi.model.IDataStoreModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.model.IWorkspaceModelCapability;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceInitializationService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceService;



public class WstServerManagementModel extends ServerManagementModel implements IWorkspaceModelCapability {
	private static final ThreadLocal<IWstServerCore> PENDING_SERVER_MANAGER = new ThreadLocal<>();
	private final IProjectsManager projectsManager;
	private final IWorkspaceInitializationService workspaceInitializationService;

	public WstServerManagementModel(IDataStoreModel dataLocation, IWstServerCore serverManager,
			IWorkspaceService workspaceService, IWorkspaceInitializationService workspaceInitializationService) {
		super(captureDependencies(dataLocation, Objects.requireNonNull(serverManager, "serverManager")));
		PENDING_SERVER_MANAGER.remove();
		IWorkspaceService resolvedWorkspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
		IWTPService wtpService = new WTPService();
		IFileWatcherService fileWatcherService = getFileWatcherService();
		this.projectsManager = new ProjectsManager(resolvedWorkspaceService, wtpService, fileWatcherService,
				Collections.emptyList());
		this.workspaceInitializationService = workspaceInitializationService != null
				? workspaceInitializationService
				: (resolvedWorkspaceService instanceof IWorkspaceInitializationService
						? (IWorkspaceInitializationService) resolvedWorkspaceService
						: null);
	}

	@Override
	protected IServerModel createServerModel() {
		IWstServerCore serverManager = PENDING_SERVER_MANAGER.get();
		if (serverManager == null) {
			throw new IllegalStateException("WST dependencies must be provided before createServerModel()");
		}
		return new WSTServerModel(this, serverManager);
	}

	@Override
	public IProjectsManager getProjectsManager() {
		return projectsManager;
	}

	@Override
	public IWorkspaceInitializationService getWorkspaceInitializationService() {
		return workspaceInitializationService;
	}

	@Override
	public void dispose() {
		try {
			projectsManager.dispose();
		} finally {
			super.dispose();
		}
	}

	private static IDataStoreModel captureDependencies(IDataStoreModel dataLocation, IWstServerCore serverManager) {
		PENDING_SERVER_MANAGER.set(Objects.requireNonNull(serverManager, "serverManager"));
		return dataLocation;
	}
}
