package com.github.cabutchei.rsp.server.spi.servertype;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.server.spi.util.IRspAdaptable;

/**
 * Lightweight RSP-side wrapper around a WST {@code IRuntimeType}.
 * <p>
 * Only methods whose signatures can be expressed with existing RSP wrappers or
 * plain Java types are exposed here.
 * </p>
 */
public interface IRuntimeType extends IRspAdaptable {
	String getId();

	String getName();

	String getDescription();

	String getVendor();

	String getVersion();

	boolean canCreate();

	IRuntimeWorkingCopy createRuntime(String id, IProgressMonitor monitor) throws CoreException;
}
