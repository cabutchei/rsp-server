package com.github.cabutchei.rsp.eclipse.wst.bootstrap;

import com.github.cabutchei.rsp.server.ServerManagementServerImpl;
import com.github.cabutchei.rsp.server.ServerManagementServerLauncher;
import com.github.cabutchei.rsp.server.persistence.DataLocationCore;

public class WstServerManagementServerLauncher extends ServerManagementServerLauncher {

	public WstServerManagementServerLauncher(String portString) {
		this(portString, true);
	}

	public WstServerManagementServerLauncher(String portString, boolean loadServersOnLaunch) {
		super(portString, loadServersOnLaunch);
	}

	@Override
	protected ServerManagementServerImpl createImpl() {
		DataLocationCore dlc = new DataLocationCore(this.portString);
		return new ServerManagementServerImpl(this, createServerManagementModel(dlc));
	}
}
