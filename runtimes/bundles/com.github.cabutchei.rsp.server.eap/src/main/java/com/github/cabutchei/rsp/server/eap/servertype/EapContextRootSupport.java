/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.eap.servertype;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.server.generic.jee.ContextRootSupport;

public class EapContextRootSupport extends ContextRootSupport {
	private static final Pattern CONTEXT_ROOT_PATTERN =
			Pattern.compile("<context-root>\\s*([^<]+?)\\s*</context-root>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	@Override
	public String[] getDeploymentUrls(String strat, String baseUrl, String deployableOutputName, DeployableState ds) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return new String[0];
		}
		if (deployableOutputName == null || deployableOutputName.isBlank()) {
			return new String[] { baseUrl };
		}
		if (deployableOutputName.equalsIgnoreCase("root.war") || deployableOutputName.equalsIgnoreCase("root")) {
			return new String[] { baseUrl };
		}

		String[] fromDescriptor = findFromDescriptor(ds);
		if (fromDescriptor != null && fromDescriptor.length > 0) {
			return toUrls(fromDescriptor, baseUrl);
		}

		String deploymentPath = deployableOutputName;
		if ("appendDeploymentNameRemoveSuffix".equals(strat)) {
			int dot = deploymentPath.lastIndexOf('.');
			if (dot > 0) {
				deploymentPath = deploymentPath.substring(0, dot);
			}
		}
		return new String[] { append(deploymentPath, baseUrl) };
	}

	private String[] toUrls(String[] contextRoots, String baseUrl) {
		List<String> urls = new ArrayList<>();
		for (String contextRoot : contextRoots) {
			if (contextRoot == null) {
				continue;
			}
			String trimmed = contextRoot.trim();
			if (trimmed.isEmpty() || "/".equals(trimmed)) {
				urls.add(baseUrl);
			} else {
				urls.add(append(trimmed, baseUrl));
			}
		}
		return urls.toArray(new String[0]);
	}

	@Override
	protected String[] getCustomWebDescriptorsRelativePath() {
		return new String[] { "WEB-INF/jboss-web.xml", "jboss-web.xml" };
	}

	@Override
	protected String findFromWebDescriptorString(String descriptorContents) {
		if (descriptorContents == null) {
			return null;
		}
		Matcher matcher = CONTEXT_ROOT_PATTERN.matcher(descriptorContents);
		if (matcher.find()) {
			return matcher.group(1).trim();
		}
		return null;
	}
}
