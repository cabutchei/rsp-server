package com.github.cabutchei.rsp.server.liberty.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.WorkflowPromptDetails;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponseItem;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

public class LibertyShowInBrowserActionHandler {
	public static final String ACTION_ID = "ShowInBrowserActionHandler.actionId";
	public static final String ACTION_LABEL = "Show in browser...";
	public static final String ACTION_SELECTED_PROMPT_ID = "ShowInBrowserActionHandler.selection.id";
	public static final String ACTION_SELECTED_PROMPT_LABEL =
			"Which deployment do you want to show in the web browser?";

	private final AbstractLibertyServerDelegate delegate;

	public LibertyShowInBrowserActionHandler(AbstractLibertyServerDelegate delegate) {
		this.delegate = delegate;
	}

	public ServerActionWorkflow getInitialWorkflow() {
		WorkflowResponse workflow = new WorkflowResponse();
		workflow.setStatus(StatusConverter.convert(
				new Status(IStatus.INFO, com.github.cabutchei.rsp.server.liberty.LibertyPluginConstants.BUNDLE_ID, ACTION_LABEL)));
		ServerActionWorkflow action = new ServerActionWorkflow(ACTION_ID, ACTION_LABEL, workflow);

		List<WorkflowResponseItem> items = new ArrayList<>();
		WorkflowPromptDetails prompt = new WorkflowPromptDetails();
		prompt.setResponseSecret(false);
		prompt.setResponseType(ServerManagementAPIConstants.ATTR_TYPE_STRING);
		prompt.setValidResponses(getDeploymentChoices());

		WorkflowResponseItem item = new WorkflowResponseItem();
		item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_PROMPT_SMALL);
		item.setId(ACTION_SELECTED_PROMPT_ID);
		item.setLabel(ACTION_SELECTED_PROMPT_LABEL);
		item.setPrompt(prompt);
		items.add(item);
		workflow.setItems(items);

		return action;
	}

	private List<String> getDeploymentChoices() {
		List<String> urls = new ArrayList<>();
		String baseUrl = delegate.getShowInBrowserBaseUrl();
		if (baseUrl != null && !baseUrl.isEmpty()) {
			urls.add(baseUrl);
		}
		for (DeployableState ds : getDeployableStates()) {
			String[] depUrls = getDeploymentUrls(ds, baseUrl);
			if (depUrls != null) {
				urls.addAll(Arrays.asList(depUrls));
			}
		}
		return urls;
	}

	private List<DeployableState> getDeployableStates() {
		return delegate.getServerPublishModel().getDeployableStatesWithOptions();
	}

	private String[] getDeploymentUrls(DeployableState ds, String baseUrl) {
		String strategy = delegate.getDeploymentStrategy();
		String outputName = getOutputName(ds.getReference());
		String[] fromDelegate = delegate.getDeploymentUrls(strategy, baseUrl, outputName, ds);
		if (fromDelegate != null) {
			return fromDelegate;
		}
		String depName = outputName;
		if ("appendDeploymentNameRemoveSuffix".equals(strategy)) {
			int dot = depName == null ? -1 : depName.lastIndexOf('.');
			if (dot > 0) {
				depName = depName.substring(0, dot);
			}
		}
		if (baseUrl == null || depName == null) {
			return new String[0];
		}
		return new String[] { baseUrl + "/" + depName };
	}

	private String getOutputName(DeployableReference ref) {
		Map<String, Object> options = ref.getOptions();
		String def = null;
		if (ref.getPath() != null) {
			def = new File(ref.getPath()).getName();
		}
		String key = ServerManagementAPIConstants.DEPLOYMENT_OPTION_OUTPUT_NAME;
		if (options != null && options.get(key) != null) {
			return (String) options.get(key);
		}
		return def;
	}

	public WorkflowResponse handle(ServerActionRequest req) {
		if (req == null || req.getData() == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}
		String choice = (String) req.getData().get(ACTION_SELECTED_PROMPT_ID);
		if (choice == null) {
			return AbstractServerDelegate.cancelWorkflowResponse();
		}
		String url = findUrlFromChoice(choice);
		if (url != null) {
			WorkflowResponseItem item = new WorkflowResponseItem();
			item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_BROWSER);
			item.setLabel("Open the following url: " + url);
			item.setContent(url);
			WorkflowResponse resp = new WorkflowResponse();
			resp.setItems(Arrays.asList(item));
			resp.setStatus(StatusConverter.convert(Status.OK_STATUS));
			return resp;
		}
		return AbstractServerDelegate.cancelWorkflowResponse();
	}

	private String findUrlFromChoice(String choice) {
		if (choice == null) {
			return null;
		}
		String lower = choice.toLowerCase();
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return choice;
		}
		return null;
	}
}
