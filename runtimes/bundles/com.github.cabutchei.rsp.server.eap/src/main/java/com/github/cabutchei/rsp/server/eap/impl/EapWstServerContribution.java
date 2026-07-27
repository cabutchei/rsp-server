package com.github.cabutchei.rsp.server.eap.impl;

import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerContribution;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;

public class EapWstServerContribution implements IWstServerContribution {
	private static final WstServerTypeHandler HANDLER = new EapServerTypeHandler();

	@Override
	public String getId() {
		return "com.github.cabutchei.rsp.server.eap";
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
