/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.api.dao;

import java.util.List;

public class WatchPatternsChangedParams {
	private List<String> added;
	private List<String> removed;

	public WatchPatternsChangedParams() {
	}

	public WatchPatternsChangedParams(List<String> added, List<String> removed) {
		this.added = added;
		this.removed = removed;
	}

	public List<String> getAdded() {
		return added;
	}

	public void setAdded(List<String> added) {
		this.added = added;
	}

	public List<String> getRemoved() {
		return removed;
	}

	public void setRemoved(List<String> removed) {
		this.removed = removed;
	}
}
