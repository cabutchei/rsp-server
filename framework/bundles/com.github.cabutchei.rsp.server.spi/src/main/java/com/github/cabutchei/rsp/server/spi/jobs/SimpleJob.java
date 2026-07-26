/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.spi.jobs;

import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IRunnableWithProgress;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.launching.utils.IStatusRunnableWithProgress;
import com.github.cabutchei.rsp.launching.utils.SimpleProgressMonitor;
import com.github.cabutchei.rsp.server.spi.SPIActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleJob implements IJob {
	private static final Logger LOG = LoggerFactory.getLogger(SimpleJob.class);

	private String name;
	private String id;
	private IRunnableWithProgress runnable;
	private IStatusRunnableWithProgress statusRunnable;
	private JobProgressMonitor monitor = null;
	private IJobManager manager;
	
	public SimpleJob(String name, String id, IRunnableWithProgress runnable, IJobManager manager) {
		this(name, id, manager);
		this.runnable = runnable;
	}

	public SimpleJob(String name, String id, IStatusRunnableWithProgress statusRunnable, IJobManager manager) {
		this(name, id, manager);
		this.statusRunnable = statusRunnable;
	}

	private SimpleJob(String name, String id, IJobManager manager) {
		this.name = name;
		this.id = id;
		this.manager = manager;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public double getProgress() {
		return ((JobProgressMonitor)getProgressMonitor()).getPercentage();
	}

	@Override
	public String getProgressMessage() {
		return ((JobProgressMonitor) getProgressMonitor()).getDisplayMessage();
	}
	
	public synchronized IProgressMonitor getProgressMonitor() {
		if( monitor == null ) {
			monitor = new JobProgressMonitor(); 
		}
		return monitor;
	}
	
	private class JobProgressMonitor extends SimpleProgressMonitor {
		private String taskName;
		private String subTaskName;

		@Override
		public void beginTask(String name, int totalWork) {
			super.beginTask(name, totalWork);
			boolean changed = setTaskNameInternal(name);
			if (changed) {
				manager.jobWorkChanged(SimpleJob.this);
			}
		}

		@Override
		public void setTaskName(String name) {
			boolean changed = setTaskNameInternal(name);
			if (changed) {
				manager.jobWorkChanged(SimpleJob.this);
			}
		}

		@Override
		public void subTask(String name) {
			String normalized = normalizeMessage(name);
			if (!equalsNullable(subTaskName, normalized)) {
				subTaskName = normalized;
				manager.jobWorkChanged(SimpleJob.this);
			}
		}

		public void worked(int work) {
			double d = getPercentage();
			super.worked(work);
			double d2 = getPercentage();
			// Don't fire events if the old and new pctg is exactly the same
			if( d2 > d )
				manager.jobWorkChanged(SimpleJob.this);
		}
		@Override
		public void done() {
			super.done();
			manager.jobWorkChanged(SimpleJob.this);
		}

		private boolean setTaskNameInternal(String name) {
			String normalized = normalizeMessage(name);
			if (equalsNullable(taskName, normalized)) {
				return false;
			}
			taskName = normalized;
			return true;
		}

		private String getDisplayMessage() {
			if (subTaskName != null && !subTaskName.isBlank()) {
				return subTaskName;
			}
			if (taskName != null && !taskName.isBlank() && !taskName.equals(SimpleJob.this.getName())) {
				return taskName;
			}
			return null;
		}
	}

	private static boolean equalsNullable(String left, String right) {
		return left == null ? right == null : left.equals(right);
	}

	private static String normalizeMessage(String message) {
		if (message == null) {
			return null;
		}
		String trimmed = message.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
	
	public IStatus run() {
		if( runnable != null ) {
			try {
				runnable.run(getProgressMonitor());
				return Status.OK_STATUS;
			} catch(InterruptedException ie) {
				Thread.currentThread().interrupt();
				LOG.error(ie.getMessage(), ie);
				return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, ie.getMessage());
			} catch(Exception e) {
				LOG.error(e.getMessage(), e);
				return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, e.getMessage());
			}
		} else if( statusRunnable != null ) {
			try {
				return statusRunnable.run(getProgressMonitor());
			} catch(InterruptedException ie) {
				Thread.currentThread().interrupt();
				LOG.error(ie.getMessage(), ie);
				return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, ie.getMessage());
			} catch(Exception e) {
				LOG.error(e.getMessage(), e);
				return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, e.getMessage());
			}
		}
		// Should not happen
		return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, "No runnable found");
	}

	@Override
	public IStatus cancel() {
		IProgressMonitor monitor = getProgressMonitor();
		if( monitor == null )
			return new Status(IStatus.ERROR, SPIActivator.BUNDLE_ID, "Unable to cancel job");
		monitor.setCanceled(true);
		return Status.OK_STATUS;
	}
}
