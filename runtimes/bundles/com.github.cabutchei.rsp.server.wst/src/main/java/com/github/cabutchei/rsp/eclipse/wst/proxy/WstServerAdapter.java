package com.github.cabutchei.rsp.eclipse.wst.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.ServerUtil;
import com.github.cabutchei.rsp.api.DefaultServerAttributes;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ModuleReference;
import com.github.cabutchei.rsp.api.dao.ModuleState;
import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerType;
import com.github.cabutchei.rsp.api.dao.Attributes;
import com.github.cabutchei.rsp.api.dao.util.CreateServerAttributesUtility;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.wst.adapter.WstRspMapper;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerControl;
import com.github.cabutchei.rsp.launching.memento.IMemento;
import com.github.cabutchei.rsp.launching.memento.JSONMemento;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;



/** A proxy for a WST server that implements the IServer interface. */

public class WstServerAdapter implements IWstServerControl {
	// TODO: do we need these properties?
	public static final String TYPE_ID = "server-type-id";
	private static final String MAP_PROPERTIES_KEY = "mapProperties";
	private static final String LIST_PROPERTIES_KEY = "listProperties";
	private static final String MAP_PROPERTY_KEY_PREFIX = "mapProperty";
	private static final String LIST_PROPERTY_KEY_PREFIX = "listProperty";
	private static final String PROPERTY_KEY_VALUE_PREFIX = "value";

	private final IRuntime runtime;
	private final org.eclipse.wst.server.core.IServer wstServer;
	private final IServerManagementModel managementModel;
	private final IServerModel serverModel;
	private IServerDelegate delegate;

	public WstServerAdapter(org.eclipse.wst.server.core.IServer wstServer, IServerManagementModel managementModel) {
		this.wstServer = Objects.requireNonNull(wstServer, "wstServer cannot be null");
		this.runtime = this.wstServer.getRuntime() == null ? null
				: (this.wstServer.getRuntime() instanceof org.eclipse.wst.server.core.IRuntimeWorkingCopy
						? new WstRuntimeWorkingCopyAdapter(
								(org.eclipse.wst.server.core.IRuntimeWorkingCopy) this.wstServer.getRuntime())
						: new WstRuntimeAdapter(this.wstServer.getRuntime()));
		this.managementModel = managementModel;
		this.serverModel = managementModel.getServerModel();
		if(getServerType() != null ) {
			// setAttribute(TYPE_ID, serverType.getId());
			this.delegate = getServerType().createServerDelegate(this);
		}
		if( delegate != null && delegate.getServerPublishModel() != null ) {
			delegate.getServerPublishModel().initialize(Collections.emptyList());
		}
		// if( this.delegate != null ) {
		// 	this.delegate.setDefaults(this);
		// }
		// setAttributes(attributes);
		// if( this.delegate != null ) {
		// 	this.delegate.setDependentDefaults(this);
		// }
	}

	public void setDelegate(IServerDelegate delegate) {
		this.delegate = delegate;
		if( delegate != null && delegate.getServerPublishModel() != null )
			delegate.getServerPublishModel().initialize(Collections.EMPTY_LIST);
	}

	@Override
	public String getName() {
		return wstServer.getName();
	}

	@Override
	public String getId() {
		return wstServer.getId();
	}

	@Override
	public String getTypeId() {
		if (getServerType() != null) {
			return getServerType().getId();
		}
		if (wstServer.getServerType() != null) {
			return wstServer.getServerType().getId();
		}
		return null;
	}

	@Override
	public IServerType getServerType() {
		return WstRspMapper.toRspServerType(wstServer.getServerType(), serverModel);
	}

	public void setServerType(IServerType type) {
	}

	@Override
	public IServerDelegate getDelegate() {
		return delegate;
	}

	@Override
	public IServerWorkingCopy createWorkingCopy() {
		org.eclipse.wst.server.core.IServerWorkingCopy copy = wstServer.createWorkingCopy();
		return new WstServerWorkingCopyAdapter(copy, managementModel);
	}

	@Override
	public IRuntime getRuntime() {
		return this.runtime;
	}

	@Override
	public <T> T getAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstServer)) {
			return adapterType.cast(wstServer);
		}
		try {
			Object adapted = wstServer.getAdapter(adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through to platform adapter manager.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(this, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(wstServer, adapterType);
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return null;
	}

	@Override
	public <T> Object loadAdapter(Class<T> adapterType) {
		if (adapterType == null) {
			return null;
		}
		if (adapterType.isInstance(this)) {
			return adapterType.cast(this);
		}
		if (adapterType.isInstance(wstServer)) {
			return adapterType.cast(wstServer);
		}
		if (wstServer instanceof org.eclipse.wst.server.core.IServerAttributes) {
			try {
				Object adapted = ((org.eclipse.wst.server.core.IServerAttributes) wstServer).loadAdapter(adapterType, null);
				if (adapted != null) {
					return adapterType.cast(adapted);
				}
			} catch (Exception e) {
				// Fall back to getAdapter below.
			}
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(this, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		try {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().loadAdapter(wstServer, adapterType.getName());
			if (adapterType.isInstance(adapted)) {
				return adapterType.cast(adapted);
			}
		} catch (Exception e) {
			// Fall through.
		}
		return getAdapter(adapterType);
	}

	@Override
	public String asJson(IProgressMonitor monitor) throws CoreException {
		JSONMemento memento = JSONMemento.createWriteRoot();
		Map<String, Object> attributes = buildAttributeMap();
		saveAttributes(memento, attributes);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			memento.save(out);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
					"Could not save server to stream", e));
		}
		return new String(out.toByteArray());
	}

	@Override
	public void load(IProgressMonitor monitor) throws CoreException {
		// No-op: WST is the source of truth.
	}

	@Override
	public void delete() throws CoreException {
		try {
			wstServer.delete();
		} catch (org.eclipse.core.runtime.CoreException ce) {
			throw new CoreException(WstRspMapper.toRspStatus(ce.getStatus()));
		}
	}

	@Override
	public IServerManagementModel getServerManagementModel() {
		return managementModel;
	}

	@Override
	public IServerModel getServerModel() {
		return serverModel;
	}

	@Override
	public int getAttribute(String attributeName, int defaultValue) {
		return wstServer.getAttribute(attributeName, defaultValue);
	}

	@Override
	public boolean getAttribute(String attributeName, boolean defaultValue) {
		return wstServer.getAttribute(attributeName, defaultValue);
	}

	@Override
	public String getAttribute(String attributeName, String defaultValue) {
		return wstServer.getAttribute(attributeName, defaultValue);
	}

	@Override
	public List<String> getAttribute(String attributeName, List<String> defaultValue) {
		return wstServer.getAttribute(attributeName, defaultValue);
	}

	@Override
	public Map getAttribute(String attributeName, Map defaultValue) {
		return wstServer.getAttribute(attributeName, defaultValue);
	}

	private Map<String, Object> buildAttributeMap() {
		Map<String, Object> map = new HashMap<>();
		String id = wstServer.getId();
		map.put("id", id);
		map.put("id-set", Boolean.toString(true));
		if (wstServer.getName() != null) {
			map.put("name", wstServer.getName());
		}
		String typeId = getTypeId();
		if (typeId != null) {
			map.put(TYPE_ID, typeId);
		}
		if (getServerType() != null) {
			addAttributesFromType(map, getServerType().getRequiredAttributes());
			addAttributesFromType(map, getServerType().getOptionalAttributes());
		}
		return map;
	}

	private void addAttributesFromType(Map<String, Object> map, Attributes attrs) {
		if (attrs == null) {
			return;
		}
		CreateServerAttributesUtility util = new CreateServerAttributesUtility(attrs);
		Set<String> keys = util.listAttributes();
		for (String key : keys) {
			if (map.containsKey(key)) {
				continue;
			}
			String type = util.getAttributeType(key);
			Object defaultVal = util.getAttributeDefaultValue(key);
			Object value = readAttribute(key, type, defaultVal);
			if (value != null) {
				map.put(key, value);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Object readAttribute(String key, String type, Object defaultVal) {
		if (DefaultServerAttributes.SERVER_HOME_DIR.equals(key)
				|| DefaultServerAttributes.SERVER_HOME_FILE.equals(key)) {
			String runtimeLocation = getRuntimeLocation();
			if (runtimeLocation != null) {
				return runtimeLocation;
			}
		}
		if (ServerManagementAPIConstants.ATTR_TYPE_INT.equals(type)) {
			int def = defaultVal instanceof Integer ? ((Integer) defaultVal).intValue() : 0;
			return Integer.valueOf(wstServer.getAttribute(key, def));
		}
		if (ServerManagementAPIConstants.ATTR_TYPE_BOOL.equals(type)) {
			boolean def = defaultVal instanceof Boolean ? ((Boolean) defaultVal).booleanValue() : false;
			return Boolean.valueOf(wstServer.getAttribute(key, def));
		}
		if (ServerManagementAPIConstants.ATTR_TYPE_LIST.equals(type)) {
			List<String> def = defaultVal instanceof List ? (List<String>) defaultVal : Collections.emptyList();
			return wstServer.getAttribute(key, def);
		}
		if (ServerManagementAPIConstants.ATTR_TYPE_MAP.equals(type)) {
			Map<String, String> def = defaultVal instanceof Map ? (Map<String, String>) defaultVal : Collections.emptyMap();
			return wstServer.getAttribute(key, def);
		}
		String def = defaultVal instanceof String ? (String) defaultVal : null;
		return wstServer.getAttribute(key, def);
	}

	private String getRuntimeLocation() {
		if (runtime == null || runtime.getLocation() == null) {
			return null;
		}
		return runtime.getLocation().toOSString();
	}

	private void saveAttributes(IMemento memento, Map<String, Object> map) {
		Set<String> keys = map.keySet();
		ArrayList<String> keyList = new ArrayList<>(keys);
		Collections.sort(keyList);
		Iterator<String> iterator = keyList.iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			Object obj = map.get(key);
			if (obj instanceof String) {
				memento.putString(key, (String) obj);
			} else if (obj instanceof Integer) {
				memento.putInteger(key, ((Integer) obj).intValue());
			} else if (obj instanceof Boolean) {
				memento.putBoolean(key, ((Boolean) obj).booleanValue());
			} else if (obj instanceof List) {
				List<String> list = (List<String>) obj;
				saveList(memento, key, list);
			} else if (obj instanceof Map) {
				Map<String, String> map2 = (Map<String, String>) obj;
				saveMap(memento, key, map2);
			}
		}
	}

	private void saveMap(IMemento memento, String key, Map<String, String> map2) {
		IMemento toUse = null;
		if (key.startsWith(MAP_PROPERTY_KEY_PREFIX)) {
			toUse = memento;
		} else {
			toUse = memento.getChild(MAP_PROPERTIES_KEY);
			if (toUse == null) {
				toUse = memento.createChild(MAP_PROPERTIES_KEY);
			}
		}

		IMemento keyChild = toUse.createChild(key);
		Iterator<String> iterator = map2.keySet().iterator();
		while (iterator.hasNext()) {
			String s = iterator.next();
			keyChild.putString(s, map2.get(s));
		}
	}

	private void saveList(IMemento memento, String key, List<String> list) {
		IMemento toUse = null;
		if (key.startsWith(LIST_PROPERTY_KEY_PREFIX)) {
			toUse = memento;
		} else {
			toUse = memento.getChild(LIST_PROPERTIES_KEY);
			if (toUse == null) {
				toUse = memento.createChild(LIST_PROPERTIES_KEY);
			}
		}

		IMemento keyChild = toUse.createChild(key);
		int i = 0;
		Iterator<String> iterator = list.iterator();
		while (iterator.hasNext()) {
			String s = iterator.next();
			keyChild.putString(PROPERTY_KEY_VALUE_PREFIX + (i++), s);
		}
	}

	private IProject getProject(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return null;
		}
		return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
	}

	private IProject getProjectByLocation(String location) {
		if (location == null || location.isEmpty()) {
			return null;
		}
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project == null || !project.exists()) {
				continue;
			}
			if (project.getLocation() != null && location.equals(project.getLocation().toOSString())) {
				return project;
			}
			if (location.equals(project.getName())) {
				return project;
			}
		}
		return null;
	}

	private IModule[] getRootModules(IProject project) throws org.eclipse.core.runtime.CoreException {
		IModule[] candidates = ServerUtil.getModules(project);
		if (candidates == null || candidates.length == 0) {
			return new IModule[0];
		}
		List<IModule> roots = new ArrayList<>();
		for (IModule module : candidates) {
			IModule[] allRoots = wstServer.getRootModules(module, null);
			for (IModule root : allRoots) {
				if (!roots.contains(root)) {
					roots.add(root);
				}
			}
		}
		return roots.toArray(new IModule[0]);
	}

	private IModule getRootModuleById(String moduleId) throws org.eclipse.core.runtime.CoreException {
		if (moduleId == null || moduleId.isEmpty()) {
			return null;
		}
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project == null || !project.exists() || !project.isAccessible()) {
				continue;
			}
			IModule[] roots = getRootModules(project);
			for (IModule root : roots) {
				if (moduleId.equals(root.getId())) {
					return root;
				}
			}
		}
		return null;
	}

	private IModule[] resolveModulesForDeployable(DeployableReference reference)
			throws org.eclipse.core.runtime.CoreException {
		String moduleId = reference == null ? null : reference.getId();
		if (moduleId != null && !moduleId.isEmpty()) {
			IModule module = getRootModuleById(moduleId);
			return module == null ? null : new IModule[] { module };
		}
		IProject project = getProject(reference == null ? null : reference.getLabel());
		if (project == null || !project.exists()) {
			project = getProjectByLocation(reference == null ? null : reference.getPath());
		}
		if (project == null || !project.exists()) {
			return null;
		}
		return getRootModules(project);
	}

	private IStatus unresolvedDeployableStatus(DeployableReference reference) {
		String id = reference == null ? null : reference.getId();
		if (id != null && !id.isEmpty()) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
					NLS.bind("No module with id {0} found in workspace", id));
		}
		String label = reference == null ? null : reference.getLabel();
		return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
				NLS.bind("{0} isn't bound to any workspace project", label));
	}

	private IModule[] getModules() {
		IModule[] modules = wstServer.getModules();
		return modules == null ? new IModule[0] : modules;
	}

	private IModule getModule(String name) {
		if (name == null) {
			return null;
		}
		for (IModule module : getModules()) {
			if (name.equals(module.getName())) {
				return module;
			}
		}
		return null;
	}

	private int getModulePublishState(IModule[] modulePath) {
		if (modulePath == null || modulePath.length == 0) {
			return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
		}
		return WstRspMapper.toRspPublishState(wstServer.getModulePublishState(modulePath));
	}

	private int normalizeEffectivePublishState(int publishState) {
		switch (publishState) {
		case ServerManagementAPIConstants.PUBLISH_STATE_ADD:
		case ServerManagementAPIConstants.PUBLISH_STATE_REMOVE:
		case ServerManagementAPIConstants.PUBLISH_STATE_FULL:
			return ServerManagementAPIConstants.PUBLISH_STATE_FULL;
		case ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL:
			return ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL;
		case ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN:
			return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
		default:
			return ServerManagementAPIConstants.PUBLISH_STATE_NONE;
		}
	}

	private int mergeEffectivePublishState(int current, int candidate) {
		int normalized = normalizeEffectivePublishState(candidate);
		if (normalized == ServerManagementAPIConstants.PUBLISH_STATE_FULL) {
			return ServerManagementAPIConstants.PUBLISH_STATE_FULL;
		}
		if (normalized == ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL
				&& current != ServerManagementAPIConstants.PUBLISH_STATE_FULL) {
			return ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL;
		}
		if (normalized == ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN
				&& current == ServerManagementAPIConstants.PUBLISH_STATE_NONE) {
			return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
		}
		return current;
	}

	private int getModuleRunState(IModule[] modulePath) {
		if (modulePath == null || modulePath.length == 0) {
			return ServerManagementAPIConstants.STATE_UNKNOWN;
		}
		int moduleState = WstRspMapper.toRspServerState(wstServer.getModuleState(modulePath));
		// int serverState = getServerRunState();
		// if (serverState == ServerManagementAPIConstants.STATE_STOPPED
		// 		|| serverState == ServerManagementAPIConstants.STATE_STOPPING) {
		// 	return serverState;
		// }
		// if (serverState == ServerManagementAPIConstants.STATE_UNKNOWN
		// 		&& moduleState == ServerManagementAPIConstants.STATE_STARTED) {
		// 	return ServerManagementAPIConstants.STATE_UNKNOWN;
		// }
		// if (serverState == ServerManagementAPIConstants.STATE_STARTED
		// 		&& moduleState == ServerManagementAPIConstants.STATE_UNKNOWN) {
		// 	return ServerManagementAPIConstants.STATE_STARTED;
		// }
		return moduleState;
	}

	private DeployableReference toDeployableReference(IModule module) {
		String id = module.getId();
		String label = module.getName();
		String path = module.getProject() != null ? module.getProject().getLocation().toOSString() : module.getName();
		String typeId = module.getModuleType() == null ? null : module.getModuleType().getId();
		DeployableReference ref = new DeployableReference(id, label, path, typeId);
		return ref;
	}

	private ModuleReference toModuleReference(IModule module) {
		String typeId = module.getModuleType() == null ? null : module.getModuleType().getId();
		return new ModuleReference(module.getId(), module.getName(), typeId);
	}

	private void collectModuleStates(DeployableReference deployable, IModule[] parentPath, List<ModuleState> out) {
		IModule[] children = wstServer.getChildModules(parentPath, new NullProgressMonitor());
		if (children == null) {
			return;
		}
		for (IModule child : children) {
			IModule[] childPath = append(parentPath, child);
			ModuleReference moduleRef = toModuleReference(child);
			int runState = getModuleRunState(childPath);
			int publishState = getModulePublishState(childPath);
			out.add(new ModuleState(deployable, moduleRef, runState, publishState));
			collectModuleStates(deployable, childPath, out);
		}
	}

	private IModule[] append(IModule[] path, IModule module) {
		IModule[] result = new IModule[path.length + 1];
		System.arraycopy(path, 0, result, 0, path.length);
		result[path.length] = module;
		return result;
	}

	private ServerHandle getServerHandle() {
		IServerType type = getServerType();
		ServerType serverType = type == null ? null : new ServerType(type.getId(), type.getName(), type.getDescription());
		return new ServerHandle(getId(), serverType);
	}

	@Override
	public IStatus addDeployable(DeployableReference reference) {
		if (reference == null) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Deployable reference is required");
		}
		IModule[] modules;
		try {
			modules = resolveModulesForDeployable(reference);
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
		if (modules == null || modules.length == 0) {
			return unresolvedDeployableStatus(reference);
		}
		org.eclipse.wst.server.core.IServerWorkingCopy serverWc = wstServer.createWorkingCopy();
		try {
			serverWc.modifyModules(modules, null, new NullProgressMonitor());
			serverWc.save(false, new NullProgressMonitor());
			return Status.OK_STATUS;
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
	}

	@Override
	public IStatus canAddDeployable(DeployableReference reference) {
		if (reference == null) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Deployable reference is required");
		}
		IModule[] modules;
		try {
			modules = resolveModulesForDeployable(reference);
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
		if (modules == null || modules.length == 0) {
			return unresolvedDeployableStatus(reference);
		}
		org.eclipse.core.runtime.IStatus status = wstServer.canModifyModules(modules, null, new NullProgressMonitor());
		return WstRspMapper.toRspStatus(status);
	}

	@Override
	public IStatus removeDeployable(DeployableReference reference) {
		if (reference == null) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Deployable reference is required");
		}
		IModule[] modules;
		try {
			modules = resolveModulesForDeployable(reference);
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
		if (modules == null || modules.length == 0) {
			return unresolvedDeployableStatus(reference);
		}
		org.eclipse.wst.server.core.IServerWorkingCopy copy = wstServer.createWorkingCopy();
		try {
			copy.modifyModules(null, modules, new NullProgressMonitor());
			copy.save(false, new NullProgressMonitor());
			return Status.OK_STATUS;
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
	}

	@Override
	public IStatus canRemoveDeployable(DeployableReference reference) {
		if (reference == null) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Deployable reference is required");
		}
		IModule[] modules;
		try {
			modules = resolveModulesForDeployable(reference);
		} catch (org.eclipse.core.runtime.CoreException e) {
			return WstRspMapper.toRspStatus(e.getStatus());
		}
		if (modules == null || modules.length == 0) {
			return unresolvedDeployableStatus(reference);
		}
		org.eclipse.core.runtime.IStatus status = wstServer.canModifyModules(null, modules, new NullProgressMonitor());
		return WstRspMapper.toRspStatus(status);
	}

	@Override
	public IStatus publish(int publishRequestType) {
		org.eclipse.core.runtime.IStatus status = wstServer.publish(
				WstRspMapper.toWstPublishKind(publishRequestType), new NullProgressMonitor());
		return WstRspMapper.toRspStatus(status);
	}

	@Override
	public IStatus canPublish() {
		return WstRspMapper.toRspStatus(wstServer.canPublish());
	}

	private int getRawServerPublishState() {
		return WstRspMapper.toRspPublishState(wstServer.getServerPublishState());
	}

	@Override
	public int getServerPublishState() {
		return getRawServerPublishState();
	}

	@Override
	public int getEffectiveServerPublishState() {
		org.eclipse.wst.server.core.internal.Server internalServer =
				wstServer instanceof org.eclipse.wst.server.core.internal.Server
						? (org.eclipse.wst.server.core.internal.Server) wstServer
						: null;
		if (internalServer == null) {
			return getServerPublishState();
		}
		if (internalServer.isPublishUnknown()) {
			return ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN;
		}
		if (!internalServer.shouldPublish()) {
			return ServerManagementAPIConstants.PUBLISH_STATE_NONE;
		}

		int effectiveState = normalizeEffectivePublishState(getRawServerPublishState());
		for (DeployableState deployableState : getDeployableStates()) {
			effectiveState = mergeEffectivePublishState(effectiveState, deployableState.getPublishState());
		}
		for (ModuleState moduleState : getModuleStates()) {
			effectiveState = mergeEffectivePublishState(effectiveState, moduleState.getPublishState());
		}
		if (effectiveState == ServerManagementAPIConstants.PUBLISH_STATE_NONE
				|| effectiveState == ServerManagementAPIConstants.PUBLISH_STATE_UNKNOWN) {
			return ServerManagementAPIConstants.PUBLISH_STATE_INCREMENTAL;
		}
		return effectiveState;
	}

	@Override
	public int getServerRunState() {
		return WstRspMapper.toRspServerState(wstServer.getServerState());
	}

	@Override
	public List<DeployableState> getDeployableStates() {
		List<DeployableState> states = new ArrayList<>();
		ServerHandle handle = getServerHandle();
		for (IModule module : getModules()) {
			DeployableReference reference = toDeployableReference(module);
			IModule[] modulePath = new IModule[] { module };
			int runState = getModuleRunState(modulePath);
			int publishState = getModulePublishState(modulePath);
			states.add(new DeployableState(handle, reference, runState, publishState));
		}
		return states;
	}

	@Override
	public List<ModuleState> getModuleStates() {
		List<ModuleState> states = new ArrayList<>();
		for (IModule module : getModules()) {
			DeployableReference deployable = toDeployableReference(module);
			collectModuleStates(deployable, new IModule[] { module }, states);
		}
		return states;
	}

	@Override
	public DeployableState getDeployableState(DeployableReference reference) {
		if (reference == null) {
			return null;
		}
		IModule module = getModule(reference.getLabel());
		if (module == null) {
			return null;
		}
		IModule[] modulePath = new IModule[] { module };
		return new DeployableState(getServerHandle(), reference, getModuleRunState(modulePath),
				getModulePublishState(modulePath));
	}

	@Override
	public void startAsync(String launchMode) throws CoreException {
		wstServer.start(launchMode, result -> {
			// no-op; asynchronous start notification is handled by WST listeners
		});
	}

	@Override
	public IStatus canStart(String launchMode) {
		return WstRspMapper.toRspStatus(wstServer.canStart(launchMode));
	}

	@Override
	public void stop(boolean force) {
		wstServer.stop(force);
	}

	@Override
	public IStatus canStop() {
		return WstRspMapper.toRspStatus(wstServer.canStop());
	}

	@Override
	public void startModule(DeployableReference reference) {
		IModule module = getModule(reference == null ? null : reference.getLabel());
		if (module != null) {
			wstServer.startModule(new IModule[] { module }, null);
		}
	}

	@Override
	public void stopModule(DeployableReference reference) {
		IModule module = getModule(reference == null ? null : reference.getLabel());
		if (module != null) {
			wstServer.stopModule(new IModule[] { module }, null);
		}
	}

	@Override
	public String getMode() {
		return wstServer.getMode();
	}

	@Override
	public void addServerListener(com.github.cabutchei.rsp.server.spi.servertype.IServerListener listener) {
		org.eclipse.wst.server.core.IServerListener wrapper = new org.eclipse.wst.server.core.IServerListener() {
			public void serverChanged(org.eclipse.wst.server.core.ServerEvent event) {
				com.github.cabutchei.rsp.server.spi.servertype.ServerEvent rspEvent = WstRspMapper.toRspServerEvent(event,
						WstServerAdapter.this);
				listener.serverChanged(rspEvent);
			}
		};
		this.wstServer.addServerListener(wrapper);
	}

}
