/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.api.dao;

public class JobProgress {
	private double percent;
	private JobHandle handle;
	private String message;

	public JobProgress() { 
		
	}
	public JobProgress( JobHandle handle, double percent) {
		this(handle, percent, null);
	}

	public JobProgress(JobHandle handle, double percent, String message) {
		this.handle = handle;
		this.percent = percent;
		this.message = message;
	}

	public double getPercent() {
		return percent;
	}

	public void setPercent(double percent) {
		this.percent = percent;
	}

	public JobHandle getHandle() {
		return handle;
	}

	public void setHandle(JobHandle handle) {
		this.handle = handle;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
