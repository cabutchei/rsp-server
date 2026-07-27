package com.github.cabutchei.rsp.eclipse.wst.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerContribution;
import com.github.cabutchei.rsp.eclipse.wst.api.WstServerTypeHandler;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;

public final class WstServerContributionRegistry {
	private static final Logger LOG = LoggerFactory.getLogger(WstServerContributionRegistry.class);
	private static final long CONTRIBUTION_WAIT_TIMEOUT_MS = 5000L;
	private static final long CONTRIBUTION_WAIT_POLL_MS = 100L;
	private static final Set<String> REQUIRED_CONTRIBUTION_IDS = setOf(
			"com.github.cabutchei.rsp.server.eap",
			"com.github.cabutchei.rsp.server.liberty",
			"com.github.cabutchei.rsp.server.websphere");

	private WstServerContributionRegistry() {
		// utility
	}

	public static void addExtensions(IServerManagementModel model) {
		if (model == null) {
			return;
		}
		List<IWstServerContribution> contributions = waitForRequiredContributions();
		for (IWstServerContribution contribution : contributions) {
			try {
				contribution.addExtensions(model);
			} catch (RuntimeException e) {
				LOG.error("Failed to add WST server contribution {}", contribution.getId(), e);
			}
		}
	}

	public static WstServerTypeHandler findHandler(String serverTypeId) {
		if (serverTypeId == null) {
			return null;
		}
		for (IWstServerContribution contribution : getContributions()) {
			try {
				WstServerTypeHandler handler = contribution.getHandler();
				if (handler != null && handler.handles(serverTypeId)) {
					return handler;
				}
			} catch (RuntimeException e) {
				LOG.warn("Failed to inspect WST server contribution {}", contribution.getId(), e);
			}
		}
		return null;
	}

	private static List<IWstServerContribution> waitForRequiredContributions() {
		long deadline = System.currentTimeMillis() + CONTRIBUTION_WAIT_TIMEOUT_MS;
		List<IWstServerContribution> contributions = getContributions();
		while (!containsAllRequired(contributions) && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(CONTRIBUTION_WAIT_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			contributions = getContributions();
		}
		if (!containsAllRequired(contributions)) {
			LOG.warn("WST contribution services incomplete. expected={}, available={}",
					REQUIRED_CONTRIBUTION_IDS, contributionIds(contributions));
		}
		return contributions;
	}

	private static boolean containsAllRequired(Collection<IWstServerContribution> contributions) {
		return contributionIds(contributions).containsAll(REQUIRED_CONTRIBUTION_IDS);
	}

	private static Set<String> contributionIds(Collection<IWstServerContribution> contributions) {
		Set<String> ids = new LinkedHashSet<>();
		for (IWstServerContribution contribution : contributions) {
			if (contribution != null && contribution.getId() != null) {
				ids.add(contribution.getId());
			}
		}
		return ids;
	}

	private static List<IWstServerContribution> getContributions() {
		BundleContext context = getBundleContext();
		if (context == null) {
			return Collections.emptyList();
		}
		Collection<ServiceReference<IWstServerContribution>> refs;
		try {
			refs = context.getServiceReferences(IWstServerContribution.class, null);
		} catch (InvalidSyntaxException e) {
			LOG.error("Failed to query WST server contributions", e);
			return Collections.emptyList();
		}
		if (refs == null || refs.isEmpty()) {
			return Collections.emptyList();
		}
		List<IWstServerContribution> contributions = new ArrayList<>();
		for (ServiceReference<IWstServerContribution> ref : refs) {
			IWstServerContribution contribution = context.getService(ref);
			if (contribution != null) {
				contributions.add(contribution);
			}
		}
		return contributions;
	}

	private static BundleContext getBundleContext() {
		return FrameworkUtil.getBundle(WstServerContributionRegistry.class).getBundleContext();
	}

	private static Set<String> setOf(String... values) {
		Set<String> set = new LinkedHashSet<>();
		for (String value : values) {
			set.add(value);
		}
		return set;
	}
}
