package com.github.cabutchei.rsp.eclipse.wst.proxy;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IPath;
import com.github.cabutchei.rsp.eclipse.wst.adapter.WstRspMapper;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;


public class WstRuntimeWorkingCopyAdapter extends WstRuntimeAdapter implements IRuntimeWorkingCopy {

	org.eclipse.wst.server.core.IRuntimeWorkingCopy wstRuntimeWorkingCopy;
	WstRuntimeAdapter runtime;

	public WstRuntimeWorkingCopyAdapter(org.eclipse.wst.server.core.IRuntimeWorkingCopy wstRuntimeWorkingCopy) {
		super(wstRuntimeWorkingCopy);
		this.wstRuntimeWorkingCopy = wstRuntimeWorkingCopy;
		org.eclipse.wst.server.core.IRuntime wstRuntime = wstRuntimeWorkingCopy.getOriginal();
		this.runtime = wstRuntime == null ? null : new WstRuntimeAdapter(wstRuntime);
	}

	@Override
	public void setLocation(IPath path) {
		org.eclipse.core.runtime.IPath p = new org.eclipse.core.runtime.Path(path.toOSString());
		this.wstRuntimeWorkingCopy.setLocation(p);
	}

	@Override
	public IRuntime save(boolean force) throws CoreException {
		try {
			this.runtime.getAdapter(org.eclipse.wst.server.core.IRuntimeWorkingCopy.class).save(force, null);
			return this.runtime;
		} catch (org.eclipse.core.runtime.CoreException e) {
            throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
		}
	}

}
