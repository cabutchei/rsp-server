package com.github.cabutchei.rsp.server.eap.servertype.publishing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.server.eap.servertype.IEapServerAttributes;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;

public class EapPublishController {
	private static final Logger LOG = LoggerFactory.getLogger(EapPublishController.class);

	private static final String[] MARKER_FILES = {
		".dodeploy",
		".redeploy",
		".skipdeploy",
		".isdeploying",
		".deployed",
		".failed",
		".isundeploying",
		".undeployed",
		".pending",
		".undeploy"
	};

	private final IServer server;

	public EapPublishController(IServer server) {
		this.server = server;
	}

	public void publishFinished(int publishRequestType, List<DeployableState> deployableStates, int serverRunState) {
		Path deploymentFolder = getDeploymentFolder();
		if (deploymentFolder == null) {
			return;
		}
		if (deployableStates == null || deployableStates.isEmpty()) {
			return;
		}
		for (DeployableState state : deployableStates) {
			DeployableReference reference = state == null ? null : state.getReference();
			if (reference == null) {
				continue;
			}
			String outputName = resolveOutputName(reference);
			if (outputName == null || outputName.isEmpty()) {
				continue;
			}
			Path deploymentPath = deploymentFolder.resolve(outputName);
			if (state.getPublishState() == ServerManagementAPIConstants.PUBLISH_STATE_REMOVE) {
				cleanAllMarkers(deploymentPath.toString());
				continue;
			}
			String marker = chooseMarker(publishRequestType, state.getPublishState(), deploymentPath.toFile());
			cleanAllMarkers(deploymentPath.toString());
			createMarker(deploymentPath.toString(), marker);
		}
	}

	private Path getDeploymentFolder() {
		String home = getRuntimeLocation();
		if (home == null || home.isEmpty()) {
			home = server.getAttribute(IEapServerAttributes.SERVER_HOME, (String) null);
		}
		if (home == null || home.isEmpty()) {
			LOG.warn("Cannot determine EAP server home directory for {}", server.getId());
			return null;
		}
		String base = server.getAttribute(IEapServerAttributes.BASE_DIRECTORY, IEapServerAttributes.BASE_DIRECTORY_DEFAULT);
		if (base == null || base.isEmpty()) {
			base = IEapServerAttributes.BASE_DIRECTORY_DEFAULT;
		}
		return new File(home).toPath().resolve(base).resolve("deployments");
	}

	private String resolveOutputName(DeployableReference reference) {
		String fromOptions = getOutputNameFromOptions(reference);
		if (fromOptions != null && !fromOptions.isEmpty()) {
			return fromOptions;
		}
		String path = reference.getPath();
		if (path != null) {
			File file = new File(path);
			if (file.isFile()) {
				return file.getName();
			}
		}
		String label = reference.getLabel();
		if (label == null || label.isEmpty()) {
			return null;
		}
		String extension = resolveExtension(reference.getTypeId(), label);
		if (extension != null && !label.toLowerCase(Locale.ROOT).endsWith(extension)) {
			return label + extension;
		}
		return label;
	}

	private String getOutputNameFromOptions(DeployableReference reference) {
		Map<String, Object> options = reference.getOptions();
		if (options == null) {
			return null;
		}
		Object outputName = options.get(ServerManagementAPIConstants.DEPLOYMENT_OPTION_OUTPUT_NAME);
		if (outputName instanceof String) {
			return (String) outputName;
		}
		return null;
	}

	private String resolveExtension(String typeId, String label) {
		if (label == null) {
			return null;
		}
		if (typeId == null) {
			return null;
		}
		if (typeId.contains("jst.web")) {
			return ".war";
		}
		if (typeId.contains("jst.ear")) {
			return ".ear";
		}
		if (typeId.contains("jst.ejb") || typeId.contains("jst.utility") || typeId.contains("jst.appclient")) {
			return ".jar";
		}
		if (typeId.contains("jst.connector")) {
			return ".rar";
		}
		return null;
	}

	private String chooseMarker(int publishRequestType, int modulePublishState, File deployment) {
		return ".dodeploy";
	}

	private void createMarker(String modulePath, String marker) {
		File toTouch = new File(modulePath + marker);
		try {
			touch(toTouch);
		} catch (IOException ioe) {
			LOG.warn("Error creating deployment marker file: {}", toTouch.getAbsolutePath(), ioe);
		}
	}

	private void touch(File file) throws IOException {
		long timestamp = System.currentTimeMillis();
		if (!file.exists()) {
			new FileOutputStream(file).close();
		}

		if (!file.setLastModified(timestamp)) {
			LOG.debug("Unable to set timestamp on file {}", file.getAbsolutePath());
		}
	}

	private void cleanAllMarkers(String modulePath) {
		for (String marker : MARKER_FILES) {
			File file = new File(modulePath + marker);
			if (file.exists() && !file.delete()) {
				LOG.warn("Cannot remove marker file {}", file.getAbsolutePath());
			}
		}
	}

	private String getRuntimeLocation() {
		IRuntime runtime = server.getRuntime();
		if (runtime == null || runtime.getLocation() == null) {
			return null;
		}
		return runtime.getLocation().toOSString();
	}
}
