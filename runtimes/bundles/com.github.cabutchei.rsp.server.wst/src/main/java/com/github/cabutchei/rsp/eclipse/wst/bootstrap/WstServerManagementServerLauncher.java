package com.github.cabutchei.rsp.eclipse.wst.bootstrap;

import com.github.cabutchei.rsp.server.ServerManagementServerImpl;
import com.github.cabutchei.rsp.server.ServerManagementServerLauncher;
import com.github.cabutchei.rsp.server.persistence.DataLocationCore;
import com.github.cabutchei.rsp.server.workspace.InitHandlerOptions;

public class WstServerManagementServerLauncher extends ServerManagementServerLauncher {

	public WstServerManagementServerLauncher(String portString) {
		this(portString, InitHandlerOptions.externalSocketDefaults(), true);
	}

	public WstServerManagementServerLauncher(String portString, InitHandlerOptions initHandlerOptions,
			boolean loadServersOnLaunch) {
		super(portString, initHandlerOptions, loadServersOnLaunch);
	}

	@Override
	protected ServerManagementServerImpl createImpl() {
		DataLocationCore dlc = new DataLocationCore(this.portString);
		return new ServerManagementServerImpl(this, createServerManagementModel(dlc), getInitHandlerOptions());
	}
}
