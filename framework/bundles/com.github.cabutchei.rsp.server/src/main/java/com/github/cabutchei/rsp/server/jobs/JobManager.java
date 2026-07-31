/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.cabutchei.rsp.api.dao.JobHandle;
import com.github.cabutchei.rsp.eclipse.core.runtime.IRunnableWithProgress;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.launching.utils.IStatusRunnableWithProgress;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.spi.jobs.IJob;
import com.github.cabutchei.rsp.server.spi.jobs.IJobListener;
import com.github.cabutchei.rsp.server.spi.jobs.IJobManager;
import com.github.cabutchei.rsp.server.spi.jobs.SimpleJob;

public class JobManager implements IJobManager {

	private List<IJobListener> listeners = new ArrayList<>();
	private Map<String, IJob> currentJobs = new HashMap<>();
	private ExecutorService executor = Executors.newFixedThreadPool(5);
	
	public JobManager() {
		super();
	}
	@Override
	public void addJobListener(IJobListener l) {
		listeners.add(l);
	}

	@Override
	public void removeJobListener(IJobListener l) {
		listeners.remove(l);
	}

	@Override
	public IJob scheduleJob(String jobName, IRunnableWithProgress runnable) {
		SimpleJob job = new SimpleJob(jobName, generateJobId(), runnable, this);
		if (!registerJob(job)) {
			return null;
		}
		submit(job);
		return job;
	}

	@Override
	public IJob scheduleJob(String jobName, IStatusRunnableWithProgress runnable) {
		SimpleJob job = new SimpleJob(jobName, generateJobId(), runnable, this);
		if (!registerJob(job)) {
			return null;
		}
		submit(job);
		return job;
	}

	@Override
	public IStatus scheduleJobAndWait(String jobName, IStatusRunnableWithProgress runnable) {
		SimpleJob job = new SimpleJob(jobName, generateJobId(), runnable, this);
		if (!registerJob(job)) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Failed to schedule job " + jobName);
		}
		try {
			return submit(job).get();
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, ie.getMessage(), ie);
		} catch (ExecutionException ee) {
			Throwable cause = ee.getCause() == null ? ee : ee.getCause();
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, cause.getMessage(), cause);
		}
	}

	private boolean registerJob(SimpleJob job) {
		IJob oldJob = currentJobs.get(job.getId());
		if( oldJob != null ) {
			return false;
		}
		currentJobs.put(job.getId(), job);
		fireJobAdded(job);
		return true;
	}
	
	private void fireJobAdded(IJob job) {
		ArrayList<IJobListener> tmp = new ArrayList<>(listeners);
		for( IJobListener l : tmp ) {
			l.jobAdded(job);
		}
	}

	private Future<IStatus> submit(SimpleJob job) {
		return executor.submit(() -> {
			IStatus s = null;
			try {
				s = job.run();
			} catch(Exception e) {
				s = new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, e.getMessage(), e);
			}
			fireJobComplete(job, s);
			return s;
		});
	}
	
	@Override
	public void cancel(IJob job) {
		if( job instanceof SimpleJob && ((SimpleJob)job).getProgressMonitor() != null ) {
			((SimpleJob)job).getProgressMonitor().setCanceled(true);
		}
	}
	
	private void fireJobComplete(SimpleJob job, IStatus s) {
		currentJobs.remove(job.getId());
		ArrayList<IJobListener> tmp = new ArrayList<>(listeners);
		for( IJobListener l : tmp ) {
			l.jobRemoved(job, s);
		}
	}
	
	private String generateJobId() {
		return UUID.randomUUID().toString();
	}

	@Override
	public void shutdown() {
		executor.shutdown();
	}
	@Override
	public void jobWorkChanged(IJob job) {
		ArrayList<IJobListener> tmp = new ArrayList<>(listeners);
		for( IJobListener l : tmp ) {
			l.progressChanged(job, job.getProgress());
		}
	}
	@Override
	public List<IJob> getJobs() {
		return new ArrayList<>(currentJobs.values());
	}
	
	@Override
	public IStatus cancelJob(JobHandle job) {
		IJob ijob = currentJobs.get(job.getId());
		if( ijob == null ) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Job not found: " + job.getId());
		}
		return ijob.cancel();
	}
}
