package com.github.cabutchei.rsp.server.liberty.impl;

import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerContribution;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;

public class LibertyWstServerContribution implements IWstServerContribution {
	private static final WstServerTypeHandler HANDLER = new LibertyServerTypeHandler();

	@Override
	public String getId() {
		return "com.github.cabutchei.rsp.server.liberty";
	}

	@Override
	public WstServerTypeHandler getHandler() {
		return HANDLER;
	}

	@Override
	public void addExtensions(IServerManagementModel model) {
		ExtensionHandler.addExtensions(model);
	}

	@Override
	public void removeExtensions(IServerManagementModel model) {
		ExtensionHandler.removeExtensions(model);
	}
}
