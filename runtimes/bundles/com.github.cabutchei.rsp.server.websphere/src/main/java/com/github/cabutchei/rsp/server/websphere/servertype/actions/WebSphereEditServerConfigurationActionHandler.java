package com.github.cabutchei.rsp.server.websphere.servertype.actions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponseItem;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.model.AbstractServerDelegate;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

import com.github.cabutchei.rsp.server.websphere.impl.AbstractWebSphereServerDelegate;
import com.github.cabutchei.rsp.server.websphere.impl.WebSphereWstServerAccess;

import com.ibm.ws.ast.st.v85.core.internal.util.ServerXmlFileHandler;



public class WebSphereEditServerConfigurationActionHandler {
	public static final String ACTION_ID = "WebSphereEditServerConfigurationActionHandler.actionId";
	public static final String ACTION_LABEL = "Edit Configuration File...";
	public static final String ACTION_EDIT_FILE_PROMPT_ID = "WebSphereEditServerConfigurationActionHandler.selection.id";
	public static final String ACTION_EDIT_FILE_PROMPT_LABEL = "WebSphereEditServerConfigurationActionHandler.selection.label";

	private final AbstractWebSphereServerDelegate delegate;

	public WebSphereEditServerConfigurationActionHandler(AbstractWebSphereServerDelegate delegate) {
		this.delegate = delegate;
	}

	public ServerActionWorkflow getInitialWorkflow() {
		WorkflowResponse workflow = new WorkflowResponse();
		ServerActionWorkflow action = new ServerActionWorkflow(ACTION_ID, ACTION_LABEL, workflow);
		List<WorkflowResponseItem> items = new ArrayList<>();
		workflow.setItems(items);

		String configFilePath = getConfigurationFile();
		if (configFilePath == null || !(new File(configFilePath).exists())) {
			workflow.setStatus(StatusConverter.convert(
					new Status(IStatus.CANCEL, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID, ACTION_LABEL)));
			return action;
		}

		WorkflowResponseItem item = new WorkflowResponseItem();
		item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_EDITOR);
		Map<String, String> propMap = new HashMap<>();
		propMap.put(ServerManagementAPIConstants.WORKFLOW_EDITOR_PROPERTY_PATH, configFilePath);
		item.setProperties(propMap);
		item.setId(ACTION_ID);
		item.setLabel(ACTION_LABEL);
		items.add(item);

		workflow.setStatus(StatusConverter.convert(
				new Status(IStatus.OK, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID, ACTION_LABEL)));
		return action;
	}

	private String getConfigurationFile() {
		try {
			ServerXmlFileHandler handler = WebSphereWstServerAccess.createServerXmlFileHandler(delegate.getServer());
			if (handler == null) {
				return null;
			}
			String path = handler.getServerXMLFilePath();
			if (path == null || path.isEmpty()) {
				return null;
			}
			return new File(path).getAbsolutePath();
		} catch (IOException | CoreException e) {
			return null;
		}
	}

	public WorkflowResponse handle(ServerActionRequest req) {
		if (req == null || req.getData() == null) {
			return AbstractServerDelegate.okWorkflowResponse();
		}
		return AbstractServerDelegate.okWorkflowResponse();
	}
}
