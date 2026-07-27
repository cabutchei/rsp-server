package com.github.cabutchei.rsp.eclipse.wst.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WstServerTypeHandlerRegistry {
	private static final List<WstServerTypeHandler> HANDLERS = new CopyOnWriteArrayList<>();

	private WstServerTypeHandlerRegistry() {
		// static utility
	}

	public static void register(WstServerTypeHandler handler) {
		if( handler != null ) {
			HANDLERS.add(handler);
		}
	}

	public static void unregister(WstServerTypeHandler handler) {
		if( handler != null ) {
			HANDLERS.remove(handler);
		}
	}

	public static WstServerTypeHandler find(String serverTypeId) {
		WstServerTypeHandler contributed = com.github.cabutchei.rsp.eclipse.wst.core.WstServerContributionRegistry
				.findHandler(serverTypeId);
		if (contributed != null) {
			return contributed;
		}
		if( serverTypeId == null ) {
			return null;
		}
		for( WstServerTypeHandler handler : HANDLERS ) {
			if( handler != null && handler.handles(serverTypeId) ) {
				return handler;
			}
		}
		return null;
	}
}
