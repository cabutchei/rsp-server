/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.workspace;

/**
 * Policy knobs for workspace initialization behavior.
 */
public class InitHandlerOptions {
	private final Boolean autoBuilding;
	private final Boolean autoPublishing;

	public InitHandlerOptions(Boolean autoBuilding, Boolean autoPublishing) {
		this.autoBuilding = autoBuilding;
		this.autoPublishing = autoPublishing;
	}

	public Boolean getAutoBuilding() {
		return autoBuilding;
	}

	public Boolean getAutoPublishing() {
		return autoPublishing;
	}

	public static InitHandlerOptions externalSocketDefaults() {
		return new InitHandlerOptions(Boolean.FALSE, Boolean.FALSE);
	}

	public static InitHandlerOptions jdtlsOwnedWorkspaceDefaults() {
		return new InitHandlerOptions(null, Boolean.FALSE);
	}
}
