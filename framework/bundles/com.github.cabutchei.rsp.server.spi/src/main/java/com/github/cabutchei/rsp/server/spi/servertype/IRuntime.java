package com.github.cabutchei.rsp.server.spi.servertype;

import com.github.cabutchei.rsp.eclipse.core.runtime.IPath;
import com.github.cabutchei.rsp.server.spi.util.IRspAdaptable;

/**
 * Lightweight RSP-side wrapper around a WST {@code IRuntime}/{@code IRuntimeWorkingCopy}.
 */
public interface IRuntime extends IRspAdaptable {
	String getId();

	String getName();

	IRuntimeType getRuntimeType();

	IRuntimeWorkingCopy createWorkingCopy();

	boolean isWorkingCopy();

	default <T> Object loadAdapter(Class<T> adapterType) {
		return null;
	}

	IPath getLocation();
}
