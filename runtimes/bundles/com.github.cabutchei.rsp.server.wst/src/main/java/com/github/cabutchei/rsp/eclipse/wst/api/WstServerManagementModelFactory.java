package com.github.cabutchei.rsp.eclipse.wst.api;

import java.util.Objects;

import com.github.cabutchei.rsp.eclipse.workspace.EclipseWorkspaceService;
import com.github.cabutchei.rsp.eclipse.wst.core.WSTServerCore;
import com.github.cabutchei.rsp.eclipse.wst.model.WstServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IDataStoreModel;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModelFactory;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceInitializationService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceService;

public class WstServerManagementModelFactory implements IServerManagementModelFactory {
	private final IWstServerCore serverManager;
	private final IWorkspaceService workspaceService;
	private final IWorkspaceInitializationService workspaceInitializationService;

	public WstServerManagementModelFactory() {
		this(WSTServerCore.getServerManager(), new EclipseWorkspaceService());
	}

	public WstServerManagementModelFactory(IWstServerCore serverManager, IWorkspaceService workspaceService) {
		this(serverManager, workspaceService, workspaceService instanceof IWorkspaceInitializationService
				? (IWorkspaceInitializationService) workspaceService
				: null);
	}

	public WstServerManagementModelFactory(IWstServerCore serverManager, IWorkspaceService workspaceService,
			IWorkspaceInitializationService workspaceInitializationService) {
		this.serverManager = Objects.requireNonNull(serverManager, "serverManager");
		this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
		this.workspaceInitializationService = workspaceInitializationService;
	}

	@Override
	public IServerManagementModel create(IDataStoreModel dataStoreModel) {
		return new WstServerManagementModel(dataStoreModel, serverManager, workspaceService,
				workspaceInitializationService);
	}
}
