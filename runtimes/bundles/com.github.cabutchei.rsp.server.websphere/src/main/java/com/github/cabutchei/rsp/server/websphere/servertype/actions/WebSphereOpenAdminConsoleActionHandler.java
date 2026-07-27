package com.github.cabutchei.rsp.server.websphere.servertype.actions;

import java.util.ArrayList;
import java.util.List;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionWorkflow;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.WorkflowResponseItem;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import com.github.cabutchei.rsp.server.spi.util.WorkflowUtility;
import com.github.cabutchei.rsp.server.websphere.impl.AbstractWebSphereServerDelegate;
import com.github.cabutchei.rsp.server.websphere.impl.WebSphereWstServerAccess;

public class WebSphereOpenAdminConsoleActionHandler {
	public static final String ACTION_ID = "WebSphereOpenAdminConsoleActionHandler.actionId";
	public static final String ACTION_LABEL = "Open Admin Console";
	private static final String ADMIN_CONSOLE_PATH = "/ibm/console";

	private final AbstractWebSphereServerDelegate delegate;

	public WebSphereOpenAdminConsoleActionHandler(AbstractWebSphereServerDelegate delegate) {
		this.delegate = delegate;
	}

	public ServerActionWorkflow getInitialWorkflow() {
		WorkflowResponse workflow = new WorkflowResponse();
		ServerActionWorkflow action = new ServerActionWorkflow(ACTION_ID, ACTION_LABEL, workflow);
		workflow.setItems(new ArrayList<>());
		workflow.setStatus(StatusConverter.convert(
				new Status(IStatus.INFO, com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID, ACTION_LABEL)));
		return action;
	}

	private String getAdminConsoleUrl() {
		try {
			String host = WebSphereWstServerAccess.getServerAdminHostName(delegate.getServer());
			int port = WebSphereWstServerAccess.getAdminConsolePortNum(delegate.getServer());
			if (host == null || host.isEmpty() || port <= 0) {
				return null;
			}
			return "http://" + host + ":" + port + ADMIN_CONSOLE_PATH;
		} catch (CoreException | RuntimeException e) {
			return null;
		}
	}

	public WorkflowResponse handle(ServerActionRequest req) {
		String adminConsoleUrl = getAdminConsoleUrl();
		if (adminConsoleUrl == null || adminConsoleUrl.isEmpty()) {
			return WorkflowUtility.quickResponse(IStatus.ERROR,
					"Admin console is only available while the server is running.", 0);
		}

		WorkflowResponseItem item = new WorkflowResponseItem();
		item.setItemType(ServerManagementAPIConstants.WORKFLOW_TYPE_OPEN_BROWSER);
		item.setId(ACTION_ID);
		item.setLabel("Open the WebSphere admin console");
		item.setContent(adminConsoleUrl);

		WorkflowResponse resp = new WorkflowResponse();
		List<WorkflowResponseItem> items = new ArrayList<>();
		items.add(item);
		resp.setItems(items);
		resp.setStatus(StatusConverter.convert(Status.OK_STATUS));
		return resp;
	}
}
