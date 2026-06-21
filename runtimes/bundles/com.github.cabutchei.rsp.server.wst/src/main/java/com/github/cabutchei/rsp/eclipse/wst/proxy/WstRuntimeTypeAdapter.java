package com.github.cabutchei.rsp.eclipse.wst.proxy;

import java.util.Objects;

import org.eclipse.wst.server.core.internal.RuntimeType;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.wst.adapter.WstRspMapper;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeType;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;

public class WstRuntimeTypeAdapter implements IRuntimeType {
	private final RuntimeType wstRuntimeType;

	public WstRuntimeTypeAdapter(RuntimeType wstRuntimeType) {
		this.wstRuntimeType = Objects.requireNonNull(wstRuntimeType, "wstRuntimeType cannot be null");
	}

	@Override
	public String getId() {
		return wstRuntimeType.getId();
	}

	@Override
	public String getName() {
		return wstRuntimeType.getName();
	}

	@Override
	public String getDescription() {
		return wstRuntimeType.getDescription();
	}

	@Override
	public String getVendor() {
		return wstRuntimeType.getVendor();
	}

	@Override
	public String getVersion() {
		return wstRuntimeType.getVersion();
	}

	@Override
	public boolean canCreate() {
		return wstRuntimeType.canCreate();
	}

	@Override
	public IRuntimeWorkingCopy createRuntime(String id, IProgressMonitor monitor) throws CoreException {
		try {
			org.eclipse.wst.server.core.IRuntimeType runtimeType = wstRuntimeType;
			org.eclipse.wst.server.core.IRuntimeWorkingCopy runtimeWc = runtimeType.createRuntime(id,
					toWstProgressMonitor(monitor));
			return runtimeWc == null ? null : new WstRuntimeWorkingCopyAdapter(runtimeWc);
		} catch (org.eclipse.core.runtime.CoreException e) {
			throw new CoreException(WstRspMapper.toRspStatus(e.getStatus()));
		}
	}

	@Override
	public <T> T getAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstRuntimeType)) {
			return adapterType.cast(wstRuntimeType);
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(wstRuntimeType, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return null;
	}

	// @Override
	// public <T> Object loadAdapter(Class<T> adapterType) {
	// 	if (adapterType == null) {
	// 		return null;
	// 	}
	// 	if (adapterType.isInstance(this)) {
	// 		return adapterType.cast(this);
	// 	}
	// 	if (adapterType.isInstance(wstRuntimeType)) {
	// 		return adapterType.cast(wstRuntimeType);
	// 	}
	// 	try {
	// 		Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(wstRuntimeType,
	// 				adapterType.getName());
	// 		if (adapterType.isInstance(adapted)) {
	// 			return adapterType.cast(adapted);
	// 		}
	// 	} catch (Exception e) {
	// 		// Fall through.
	// 	}
	// 	return getAdapter(adapterType);
	// }

	private org.eclipse.core.runtime.IProgressMonitor toWstProgressMonitor(IProgressMonitor monitor) {
		if (monitor == null) {
			return new org.eclipse.core.runtime.NullProgressMonitor();
		}
		return new org.eclipse.core.runtime.IProgressMonitor() {
			@Override
			public void beginTask(String name, int totalWork) {
				monitor.beginTask(name, totalWork);
			}

			@Override
			public void done() {
				monitor.done();
			}

			@Override
			public void internalWorked(double work) {
				monitor.internalWorked(work);
			}

			@Override
			public boolean isCanceled() {
				return monitor.isCanceled();
			}

			@Override
			public void setCanceled(boolean value) {
				monitor.setCanceled(value);
			}

			@Override
			public void setTaskName(String name) {
				monitor.setTaskName(name);
			}

			@Override
			public void subTask(String name) {
				monitor.subTask(name);
			}

			@Override
			public void worked(int work) {
				monitor.worked(work);
			}
		};
	}
}
