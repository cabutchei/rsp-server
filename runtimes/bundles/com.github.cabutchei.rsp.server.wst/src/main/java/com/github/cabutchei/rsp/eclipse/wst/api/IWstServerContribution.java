package com.github.cabutchei.rsp.eclipse.wst.api;

import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;

public interface IWstServerContribution {
	String getId();

	WstServerTypeHandler getHandler();

	void addExtensions(IServerManagementModel model);

	void removeExtensions(IServerManagementModel model);
}
