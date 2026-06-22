package com.github.cabutchei.rsp.eclipse.wst.proxy;

import java.util.Objects;

import com.github.cabutchei.rsp.eclipse.core.runtime.IPath;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeType;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntimeWorkingCopy;

public class WstRuntimeAdapter implements IRuntime {
	protected final org.eclipse.wst.server.core.IRuntime wstRuntime;

	public WstRuntimeAdapter(org.eclipse.wst.server.core.IRuntime wstRuntime) {
		this.wstRuntime = Objects.requireNonNull(wstRuntime, "wstRuntime cannot be null");
	}

	@Override
	public String getId() {
		return wstRuntime.getId();
	}

	@Override
	public String getName() {
		return wstRuntime.getName();
	}

	@Override
	public IPath getLocation() {
		org.eclipse.core.runtime.IPath location = wstRuntime.getLocation();
		return location == null ? null : Path.fromOSString(location.toOSString());
	}

	@Override
	public IRuntimeType getRuntimeType() {
		org.eclipse.wst.server.core.IRuntimeType runtimeType = wstRuntime.getRuntimeType();
		return runtimeType instanceof org.eclipse.wst.server.core.internal.RuntimeType
				? new WstRuntimeTypeAdapter((org.eclipse.wst.server.core.internal.RuntimeType) runtimeType)
				: null;
	}

	@Override
	public IRuntimeWorkingCopy createWorkingCopy() {
		 org.eclipse.wst.server.core.IRuntimeWorkingCopy wstRuntimeWc = wstRuntime.createWorkingCopy();
			return new WstRuntimeWorkingCopyAdapter(wstRuntimeWc);
	}

	@Override
	public boolean isWorkingCopy() {
		return this.wstRuntime.isWorkingCopy();
	}

	@Override
	public <T> T getAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstRuntime)) {
			return adapterType.cast(wstRuntime);
		}
		try {
			Object adapted = wstRuntime.getAdapter(adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through to platform adapter manager.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(this, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(wstRuntime, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return null;
	}

	@Override
	public <T> Object loadAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstRuntime)) {
			return adapterType.cast(wstRuntime);
		}
		if (wstRuntime instanceof org.eclipse.wst.server.core.IRuntime) {
			try {
				Object adapted = ((org.eclipse.wst.server.core.IRuntime) wstRuntime).loadAdapter(adapterType, null);
				if (adapted != null) {
					return adapterType.cast(adapted);
				}
			} catch (Exception e) {
				// Fall back to getAdapter below.
			}
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(this, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(wstRuntime, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return getAdapter(adapterType);
	}
}
