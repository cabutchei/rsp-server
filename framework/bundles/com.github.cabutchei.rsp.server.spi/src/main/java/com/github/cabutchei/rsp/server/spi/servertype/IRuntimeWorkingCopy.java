package com.github.cabutchei.rsp.server.spi.servertype;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IPath;

/**
 * RSP-side wrapper around a WST {@code IRuntimeWorkingCopy}.
 */
public interface IRuntimeWorkingCopy extends IRuntime {
	public IRuntime save(boolean force) throws CoreException;

	public void setLocation(IPath path);
}
