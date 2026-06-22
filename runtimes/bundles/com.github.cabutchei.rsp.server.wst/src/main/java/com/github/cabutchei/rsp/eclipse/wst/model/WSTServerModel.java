package com.github.cabutchei.rsp.eclipse.wst.model;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.cabutchei.rsp.api.RSPWTPClient;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.dao.Attributes;
import com.github.cabutchei.rsp.api.dao.CreateServerResponse;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DeployableState;
import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerLaunchMode;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.ServerType;
import com.github.cabutchei.rsp.api.dao.UpdateServerRequest;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.api.dao.util.CreateServerAttributesUtility;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.MultiStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.eclipse.osgi.util.NLS;
import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerCore;
import com.github.cabutchei.rsp.eclipse.wst.model.launch.ServerLaunchMonitor;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerAdapter;
import com.github.cabutchei.rsp.eclipse.wst.proxy.WstServerWorkingCopyAdapter;
import com.github.cabutchei.rsp.launching.utils.IStatusRunnableWithProgress;
import com.github.cabutchei.rsp.secure.model.ISecureStorageProvider;
import com.github.cabutchei.rsp.server.ServerCoreActivator;
import com.github.cabutchei.rsp.server.model.internal.DaoUtilities;
import com.github.cabutchei.rsp.server.model.internal.Server;
import com.github.cabutchei.rsp.server.spi.client.ClientThreadLocal;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModel;
import com.github.cabutchei.rsp.server.spi.model.IServerModelListener;
import com.github.cabutchei.rsp.server.spi.servertype.CreateServerValidation;
import com.github.cabutchei.rsp.server.spi.servertype.IRuntime;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.servertype.IServerWorkingCopy;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class WSTServerModel implements IServerModel {
	private static final Logger LOG = LoggerFactory.getLogger(WSTServerModel.class);

	private static final String SERVERS_DIRECTORY = "servers";

	private final IWstServerCore wstServerManager;
	private final Map<String, IServerType> serverTypes;
	private final Map<String, IServer> servers;
	private final Map<String, IServerDelegate> serverDelegates;
	private final List<IServerModelListener> listeners = new ArrayList<>();
	private final Set<String> approvedAttributeTypes = new HashSet<>();
	private final IServerManagementModel managementModel;

	public WSTServerModel(IServerManagementModel managementModel, IWstServerCore wstServerManager) {
		this(managementModel, wstServerManager, new HashMap<String, IServerType>(), new HashMap<String, IServer>(),
				new HashMap<String, IServerDelegate>());
	}

	/** for testing purposes **/
	protected WSTServerModel(IServerManagementModel managementModel, IWstServerCore wstServerManager,
			Map<String, IServerType> serverTypes, Map<String, IServer> servers, Map<String, IServerDelegate> delegates) {
		this.wstServerManager = Objects.requireNonNull(wstServerManager, "wstServerManager");
		this.serverTypes = serverTypes;
		this.servers = servers;
		this.serverDelegates = delegates;
		this.managementModel = managementModel;
		ServerLaunchMonitor.getInstance().start(this);

		// Server attributes must be one of the following types
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_INT);
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_BOOL);
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_STRING);
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_LOCAL_FILE);
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_LOCAL_FOLDER);
		// List must be List<String>
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_LIST);
		// Map must be Map<String, String>
		approvedAttributeTypes.add(ServerManagementAPIConstants.ATTR_TYPE_MAP);
	}
	
	@Override
	public ISecureStorageProvider getSecureStorageProvider() {
		return managementModel.getSecureStorageProvider();
	}

	@Override
	public void addServerModelListener(IServerModelListener l) {
		listeners.add(l);
	}

	@Override
	public void removeServerModelListener(IServerModelListener l) {
		listeners.remove(l);
	}

	@Override
	public synchronized void addServerType(IServerType type) {
		if( type != null && type.getId() != null ) {
			serverTypes.put(type.getId(), type);
		}
	}
	
	@Override
	public void addServerTypes(IServerType[] types) {
		if (types == null) {
			return;
		}
		
		for (IServerType type : types) {
			addServerType(type);
		}
	}

	@Override
	public void removeServerType(IServerType type) {
		if( type != null && type.getId() != null ) {
			serverTypes.remove(type.getId());
		}
	}
	
	@Override
	public void removeServerTypes(IServerType[] types) {
		if (types == null) {
			return;
		}
		
		for (IServerType type : types) {
			removeServerType(type);
		}
	}

	@Override
	public IServer getServer(String id) {
		return servers.get(id);
	}
	
	@Override
	public Map<String, IServer> getServers() {
		return Collections.unmodifiableMap(servers);
	} 
	
	@Override
	public void saveServers() throws CoreException {
		for (IServer server : getServers().values()) {
			((IServerWorkingCopy)server).save(new NullProgressMonitor());
		}
	}
	
	private void saveServerLogError(IServer s) {
		try {
			((IServerWorkingCopy)s).save(new NullProgressMonitor());
		} catch(CoreException ce) {
			LOG.error(ce.getMessage(), ce);
		}
	}
	
	@Override
	public void loadServers() throws CoreException {
		IServer[] loadedServers = this.wstServerManager.loadServers(managementModel);
		this.wstServerManager.updateServerStatus();
		for (IServer server : loadedServers) {
			addServer(server);
		}
	}

	private void log(Exception e) {
		LOG.error(e.getMessage(), e);
	}

	private void logDebug(Exception e) {
		LOG.debug(e.getMessage(), e);
	}
	
	@Override
	public CreateServerResponse createServer(String serverType, String id, Map<String, Object> attributes) {
		try {
			return createServerUnprotected(serverType, id, attributes);
		} catch(CoreException e) {
			return new CreateServerResponse(StatusConverter.convert(e.getStatus()), Collections.emptyList());
		} catch(Exception e) {
			IStatus s = new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, 
					"An unexpected error occurred", e);
			return new CreateServerResponse(StatusConverter.convert(s), null);
		}
	}
	

		private CreateServerResponse createServerUnprotected(String serverType, String id, Map<String, Object> attributes) throws CoreException {
			if( getServer(id) != null) {
				throw new CoreException(new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, 
						"Server with id " + id + " already exists."));
			}
			if (attributes == null) {
				attributes = new HashMap<>();
			}
			IServerType type = serverTypes.get(serverType);
			if( type == null ) {
				return new CreateServerResponse(createDaoErrorStatus("Server Type " + serverType + " not found"), 
						Collections.emptyList());
			}
			IStatus validAttributes = validateAttributes(type, attributes, true, false);
			if( !validAttributes.isOK()) {
				return new CreateServerResponse(StatusConverter.convert(validAttributes), 
						getInvalidAttributeKeys(validAttributes));
			}

			IServerWorkingCopy serverWc = this.wstServerManager.createServer(type, id, attributes, managementModel);
			IServerDelegate del = serverWc.getDelegate();
			if( del == null ) {
				return new CreateServerResponse(
						StatusConverter.convert(
								new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, 
										"Error creating server delegate")), 
										Collections.EMPTY_LIST);
			}
			CreateServerValidation valid = del.validate();
			if( valid != null && valid.getStatus() != null && !valid.getStatus().isOK()) {
				return valid.toDao();
			}
			IServer server = serverWc.saveAll(false);

			addServer(server);
			return new CreateServerResponse(
				StatusConverter.convert(
					new Status(IStatus.OK, ServerCoreActivator.BUNDLE_ID, "Server created")),
						Collections.EMPTY_LIST);
		}
	
	private IStatus validateAttributes(IServerType type, 
			Map<String, Object> map, boolean updateMapWithConversions,
			boolean ignoreSecureAttributes) {
		MultiStatus ms = new MultiStatus(ServerCoreActivator.BUNDLE_ID, 0, 
				NLS.bind("There are missing/invalid required attributes for server type {0}", type.getId()), null);
		runValidateAttributes(type, map, type.getRequiredAttributes(), true, ignoreSecureAttributes, ms, updateMapWithConversions);
		runValidateAttributes(type, map, type.getOptionalAttributes(), false, ignoreSecureAttributes, ms, updateMapWithConversions);
		return ms;
	}
	
	private void runValidateAttributes(IServerType type, Map<String, Object> map, 
			 Attributes attr, boolean areRequired, boolean ignoreSecureAttributes,
			MultiStatus ms, boolean updateMapWithConversions) {
		CreateServerAttributesUtility util = new CreateServerAttributesUtility(attr);
		Set<String> required = util.listAttributes();
		for (String attrKey : required) {
			String attributeType = util.getAttributeType(attrKey);
			validateAttribute(attrKey, map, attributeType , ms, areRequired, 
					ignoreSecureAttributes, updateMapWithConversions);
		}
	}

	private void validateAttribute(String attrKey, Map<String, Object> attributeValues, String attributeType, 
			MultiStatus multiStatus, boolean required, boolean ignoreSecureAttributes, boolean updateMapWithConversions) {
		
		Object value = attributeValues.get(attrKey);
		if( value == null && ignoreSecureAttributes && attrKey.startsWith(SECURE_ATTRIBUTE_PREFIX))
			return;

		if (required && value == null) {
			multiStatus.add(
					new Status(IStatus.ERROR, attrKey, NLS.bind("Attribute {0} must not be null", attrKey)));
		} else if( value != null ){
			Class<?> expectedType = DaoUtilities.getAttributeTypeClass(attributeType);
			Class<?> actualType = value.getClass();
			if (!actualType.equals(expectedType)) {
				// Something's different than expectations based on json transfer
				// Try to convert it
				Object converted = convertJSonTransfer(value, expectedType);
				if (converted == null) {
					multiStatus.add(new Status(IStatus.ERROR, attrKey,
							NLS.bind("Attribute {0} must be of type {1} but is of type {2}",
									new String[] { attrKey, expectedType.getName(), actualType.getName() })));
				} else {
					if( updateMapWithConversions) {
						attributeValues.put(attrKey, converted);
					}
				}
			}
			if (required && String.class.equals(expectedType) 
					&& ((String) value).trim().isEmpty()) {
					multiStatus.add(new Status(IStatus.ERROR, attrKey,
							NLS.bind("Attribute {0} must not be empty", attrKey)));
			}
		}
	}

	private Object convertJSonTransfer(Object value, Class<?> expected) {
		// TODO check more things here for errors in the transfer
		if( Integer.class.equals(expected) && Double.class.equals(value.getClass())) {
			return Integer.valueOf(((Double)value).intValue());
		}
		
		if( Integer.class.equals(expected) && String.class.equals(value.getClass()) ) {
			try {
				return Integer.parseInt((String)value);
			} catch(NumberFormatException nfe) {
				return null;
			}
		}
		if( Boolean.class.equals(expected) && String.class.equals(value.getClass()) ) {
			boolean isBool = "true".equalsIgnoreCase((String)value) || 
					"false".equalsIgnoreCase((String)value);
			return isBool ? Boolean.parseBoolean((String)value) : null;
		}

		return null;
	}

	private List<String> getInvalidAttributeKeys(IStatus status) {
		return Arrays.stream(status.getChildren())
			.map(IStatus::getPlugin)
			.collect(Collectors.toList());
	}

	private Server createServer2(IServerType serverType, String id, Map<String, Object> attributes) {
		File serverFile = getServerFile(id);
		return new Server(serverFile, serverType, id, attributes, managementModel);
	}
	
	private File getServerFile(String id) {
		File data = this.managementModel.getDataStoreModel().getDataLocation();
		File serversDirectory = new File(data, SERVERS_DIRECTORY);
		if( !serversDirectory.exists()) {
			serversDirectory.mkdirs();
		}
		// TODO check for duplicates
		File serverFile = new File(serversDirectory, id);
		return serverFile;
	}

	protected void addServer(IServer server) {
		if (server == null || server.getId() == null) {
			return;
		}
		String serverId = server.getId();
		IServer previous = this.servers.put(serverId, server);
		IServerDelegate delegate = server.getDelegate();
		if (delegate != null) {
			this.serverDelegates.put(serverId, delegate);
		}
		if (previous == null) {
			fireServerAdded(server);
		}
	}

	private void fireServerAdded(IServer server) {
		for( IServerModelListener l : getListeners() ) {
			l.serverAdded(toHandle(server));
		}
	}

	@Override
	public void fireServerProcessTerminated(IServer server, String processId) {
		for( IServerModelListener l : getListeners() ) {
			l.serverProcessTerminated(toHandle(server), processId);
		}
	}

	@Override
	public void fireServerProcessCreated(IServer server, String processId) {
		for( IServerModelListener l : getListeners() ) {
			l.serverProcessCreated(toHandle(server), processId);
		}
	}

	@Override
	public void fireServerStreamAppended(IServer server, String processId, int streamType, String text) {
		for( IServerModelListener l : getListeners() ) {
			l.serverProcessOutputAppended(toHandle(server), processId, streamType, text);
		}
	}
	
	@Override
	public void fireServerStateChanged(IServer server, ServerState state) {
		for( IServerModelListener l : getListeners() ) {
			l.serverStateChanged(toHandle(server), state);
		}
	}
	
	private void fireServerRemoved(IServer server) {
		for( IServerModelListener l : getListeners() ) {
			l.serverRemoved(toHandle(server));
		}
	}
	
	private List<IServerModelListener> getListeners() {
		return new ArrayList<>(listeners);
	}
	
	private ServerHandle toHandle(IServer s) {
		String typeId = s.getTypeId();
		return new ServerHandle(s.getId(), createServerTypeDAO(typeId));
	}
	
	@Override
	public boolean removeServer(IServer toRemove) {
		if( toRemove == null || toRemove.getId() == null) {
			return false;
		}
		String serverId = toRemove.getId();
		try {
			toRemove.delete();
			this.servers.remove(serverId);
			IServerDelegate s = serverDelegates.get(serverId);
			serverDelegates.remove(serverId);
			if( s != null ) s.dispose();
			fireServerRemoved(toRemove);
		} catch (CoreException ce) {
			log(ce);
			return false;
		}
		return true;
	}
	
	@Override
	public ServerHandle[] getServerHandles() {
		Set<String> serverKeys = getServers().keySet();
		ArrayList<ServerHandle> handles = new ArrayList<>();
		for( String serverKey : serverKeys ) {
			String id = serverKey;
			String type = getServers().get(id).getTypeId();
			handles.add(new ServerHandle(id,  createServerTypeDAO(type)));
		}
		return handles.toArray(new ServerHandle[handles.size()]);
	}
	
	private ServerType createServerTypeDAO(String typeId) {
		IServerType st = serverTypes.get(typeId);
		if( st == null )
			return null;
		return new ServerType(typeId, st.getName(), st.getDescription());
	}
	
	@Override
	public IServerType getIServerType(String typeId) {
		return typeId == null ? null : serverTypes.get(typeId);
	}
	
	@Override
	public ServerType[] getServerTypes() {
		Set<String> types = serverTypes.keySet();
		ArrayList<String> types2 = new ArrayList<>(types);
		Collections.sort(types2);
		ArrayList<ServerType> ret = new ArrayList<>();
		for( String t : types2 ) {
			IServerType type = serverTypes.get(t);
			ret.add(new ServerType(t, type.getName(), type.getDescription()));
		}
		return ret.toArray(new ServerType[ret.size()]);
	}

	@Override
	public ServerType[] getAccessibleServerTypes() {
		List<ServerType> free = new ArrayList<>();
		List<ServerType> all = new ArrayList<>();
		
		Set<String> types = serverTypes.keySet();
		ArrayList<String> types2 = new ArrayList<>(types);
		Collections.sort(types2);
		for( String t : types2 ) {
			// Always add to 'all',   add to 'free' if type does not require secure storage
			IServerType type = serverTypes.get(t);
			ServerType st = new ServerType(t, type.getName(), type.getDescription());
			if( !hasSecureAttributes(type)) {
				free.add(st);
			}
			all.add(st);
		}
		
//		if (all.size() > free.size()
//				&& !hasPermissions()) {
//			return free.toArray(new ServerType[free.size()]);
//		}
//		
		return all.toArray(new ServerType[all.size()]);
	}
//	
//	private boolean hasPermissions() {
//		return managementModel.getSecureStorageProvider().getSecureStorage(true) != null;
//	}

	@Override
	public boolean hasSecureAttributes(IServerType type) {
		return type.hasSecureAttributes();
	}

	@Override
	public Attributes getRequiredAttributes(IServerType serverType) {
		Attributes ret = serverType == null ? null : serverType.getRequiredAttributes();
		return validateAttributes(ret, serverType);
	}
	
	@Override
	public Attributes getOptionalAttributes(IServerType serverType) {
		Attributes ret = serverType == null ? null : serverType.getOptionalAttributes();
		return validateAttributes(ret, serverType);
	}

	@Override
	public List<ServerLaunchMode> getLaunchModes(IServerType serverType) {
		ServerLaunchMode[] ret = serverType == null ? null : serverType.getLaunchModes();
		return ret == null ? null : Arrays.asList(ret);
	}
	
	@Override
	public Attributes getRequiredLaunchAttributes(IServerType serverType) {
		Attributes ret = serverType == null ? null : serverType.getRequiredLaunchAttributes();
		return validateAttributes(ret, serverType);
	}
	
	@Override
	public Attributes getOptionalLaunchAttributes(IServerType serverType) {
		Attributes ret = serverType == null ? null : serverType.getOptionalLaunchAttributes();
		return validateAttributes(ret, serverType);
	}

	private Attributes validateAttributes(Attributes ret, IServerType serverType) {
		if( ret != null ) {
			CreateServerAttributesUtility util = new CreateServerAttributesUtility(ret);
			Set<String> all = util.listAttributes();
			for( String all1 : all ) {
				String attrType = util.getAttributeType(all1);
				if( !approvedAttributeTypes.contains(attrType)) {
					LOG.error("Extension for servertype {} is invalid and requires an attribute of an invalid type.", serverType);
					util.removeAttribute(all1);
				}
			}
			return util.toPojo();
		}
		return null;
	}

	@Override
	public IStatus addDeployable(IServer server, DeployableReference ref) {
		// temporary hack, need to handle this better. Maybe a custom object?
		// ref.setLabel(java.nio.file.Paths.get(ref.getLabel()).getFileName().toString());
		IServerDelegate s = serverDelegates.get(server.getId());
		if( s == null ) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, 
					"Server " + server.getId() + " not found.");
		}
		IStatus canAdd = s.canAddDeployable(ref);
		if(!canAdd.isOK()) {
			return canAdd;
		}
		IStatus ret = s.getServerPublishModel().addDeployable(ref);
		return ret;
	}

	@Override
	public IStatus removeDeployable(IServer server, DeployableReference reference) {
		IServerDelegate s = serverDelegates.get(server.getId());
		if( s == null ) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, 
					"Server " + server.getId() + " not found.");
		}
		IStatus canRemove = s.canRemoveDeployable(reference);
		if (!canRemove.isOK()) {
			return canRemove;
		}
		IStatus ret = s.getServerPublishModel().removeDeployable(reference);
		return ret;
	}

	@Override
	public List<DeployableState> getDeployables(IServer server) {
		IServerDelegate s = serverDelegates.get(server.getId());
		if( s != null ) {
			return s.getServerPublishModel().getDeployableStates();
		}
		return new ArrayList<>();
	}

	@Override
	public IStatus publish(IServer server, int kind) throws CoreException {
		IStatus canPublish = checkCanPublishError(server, kind);
		if( !canPublish.isOK())
			return canPublish;
		return getServerDelegate(server).publish(kind);
	}
	
	private IServerDelegate getServerDelegate(IServer server) {
		return serverDelegates.get(server.getId());
	}
	
	@Override
	public IStatus publishAsync(IServer server, int kind) throws CoreException {
		IStatus canPublish = checkCanPublishError(server, kind);
		if( !canPublish.isOK())
			return canPublish;
		
		final RSPWTPClient rspc = ClientThreadLocal.getActiveClient();
		IStatusRunnableWithProgress irwp = new IStatusRunnableWithProgress() {
			@Override
			public IStatus run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				ClientThreadLocal.setActiveClient(rspc);
				try {
					return getServerDelegate(server).publish(kind);
				} finally {
					ClientThreadLocal.setActiveClient(null);
				}
			}
		};
		String jobName = "Asynchronous Publish for Server " + server.getId();
		this.managementModel.getJobManager().scheduleJob(jobName, irwp);
		return Status.OK_STATUS;
	}

	private IStatus checkCanPublishError(IServer server, int kind) throws CoreException {
		if (kind != ServerManagementAPIConstants.PUBLISH_INCREMENTAL
				&& kind != ServerManagementAPIConstants.PUBLISH_FULL
				&& kind != ServerManagementAPIConstants.PUBLISH_CLEAN
				&& kind != ServerManagementAPIConstants.PUBLISH_AUTO) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
					NLS.bind("Publish request for server {0} failed: Publish kind constant {1} is invalid.",
							server.getId(), kind));
		}

		IServerDelegate s = serverDelegates.get(server.getId());
		if (s == null) {
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID,
					NLS.bind("Server Delegate for server {0} is not found.", server.getId()));
		}

		IStatus canPublish = s.canPublish();
		if (canPublish == null || !canPublish.isOK()) {
			String canPublishMsg = (canPublish == null ? "null" : canPublish.getMessage());
			return new Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, NLS
					.bind("Server {0} is not in a state that can be published to: {1}", server.getId(), canPublishMsg));
		}

		return Status.OK_STATUS;
	}
	private com.github.cabutchei.rsp.api.dao.Status createDaoErrorStatus(String message) {
		return new com.github.cabutchei.rsp.api.dao.Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, message, null);
	}
	private com.github.cabutchei.rsp.api.dao.Status errorStatus(String msg) {
		return errorStatus(msg, null);
	}
	private com.github.cabutchei.rsp.api.dao.Status errorStatus(String msg, Throwable t) {
		IStatus is = new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR, 
				ServerCoreActivator.BUNDLE_ID, 
				msg, t);
		return StatusConverter.convert(is);
	}
	private boolean isEqual(String one, String two) {
		return one == null ? two == null : one.equals(two);
	}
	
	@Override
	public UpdateServerResponse updateServer(UpdateServerRequest req) {
		UpdateServerResponse resp = new UpdateServerResponse();
		try {
			if (req == null) {
				resp.getValidation().setStatus(errorStatus("Update server request cannot be null"));
				return resp;
			}
			ServerHandle sh = req.getHandle();
			if( sh == null ) {
				resp.getValidation().setStatus(errorStatus("Server handle cannot be null"));
				return resp;
			}
			IServer server = managementModel.getServerModel().getServer(sh.getId());
			if( server == null ) {
				resp.getValidation().setStatus(errorStatus("Server " + sh.getId() + " not found in model"));
				return resp;
			}
			if (req.getHandle().getType() == null) {
				resp.getValidation().setStatus(errorStatus("Update server request's server type cannot be null"));
				return resp;
			}
			
			IServerWorkingCopy wc = server.createWorkingCopy();
			String json = req.getServerJson();
			try {
				if( json == null ) {
					throw new Exception("Error while reading server string: null");
				}
			} catch(Exception ce) {
				resp.getValidation().setStatus(errorStatus("Update Failed: " + ce.getMessage(), ce));
				return resp;
			}

			IServerType type = serverTypes.get(req.getHandle().getType().getId());
			if (type == null) {
				resp.getValidation().setStatus(errorStatus("Update server request contains unknown server type"));
				return resp;
			}
			if (!(wc instanceof WstServerWorkingCopyAdapter)) {
				resp.getValidation().setStatus(errorStatus("Could not update server"));
				return resp;
			}
			WSTBase base = new WSTBase();
			try {
				base.loadFromJson(json);
			} catch (CoreException e) {
				resp.getValidation().setStatus(errorStatus("Could not load from json"));
				return resp;
			}
			String[] unchangeable = new String[] {
				// Base.PROP_ID and Base.PROP_ID_SET are protected
				WstServerAdapter.TYPE_ID, "id", "id-set"
			};
			for (int i = 0; i < unchangeable.length; i++) {
				String key = unchangeable[i];
				String dsValue = base.getAttribute(key, (String) null);
				String current;
				if (WstServerAdapter.TYPE_ID.equals(key)) {
					current = server.getTypeId();
				} else if ("id".equals(key)) {
					current = server.getId();
				} else {
					current = server.getAttribute(key, (String) null);
				}
				if (!isEqual(dsValue, current)) {
					resp.getValidation().setStatus(errorStatus(
						NLS.bind("Field {0} may not be changed", key)));
						return resp;
					}
				}
			IStatus validAttributes = validateAttributes(type, base.getMap(), false, true);
			if( !validAttributes.isOK()) {
				resp.getValidation().setStatus(StatusConverter.convert(validAttributes));
				return resp;
			}

			applyDummyAttributes(wc, base.getMap(), type);
			wc.getDelegate().updateServer(wc, resp);
			wc.getDelegate().validate();
			if( resp.getValidation().getStatus() != null && 
			resp.getValidation().getStatus().getSeverity() == Status.ERROR) {
				return resp;
			}
			try {
				LOG.debug("Saving updated server {} with runtime {}", wc.getId(), describeRuntime(wc.getRuntime()));
				wc.saveAll(false);
				LOG.debug("Saved updated server {}. Model runtime is now {}", wc.getId(), describeRuntime(server.getRuntime()));
			} catch(CoreException ce) {
				resp.getValidation().setStatus(StatusConverter.convert(ce.getStatus()));
				return resp;
			}
			if( resp.getValidation().getStatus() == null ) {
				resp.getValidation().setStatus(StatusConverter.convert(
					com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
			}
			return resp;
		} catch (Throwable t) {
			String serverId = req != null && req.getHandle() != null ? req.getHandle().getId() : "<unknown>";
			LOG.error("Unexpected failure updating server {}", serverId, t);
			String message = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
			resp.getValidation().setStatus(errorStatus("Update Failed: " + message, t));
			return resp;
		}
	}

	private void applyDummyAttributes(IServerWorkingCopy wc, Map<String, Object> values, IServerType type) {
		if (wc == null || values == null || type == null) {
			return;
		}
		Map<String, String> types = collectAttributeTypes(type);
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key == null || !types.containsKey(key)) {
				continue;
			}
			Object value = entry.getValue();
			String attrType = types.get(key);
			if (ServerManagementAPIConstants.ATTR_TYPE_INT.equals(attrType)) {
				Integer intValue = coerceInteger(value);
				if (intValue != null) {
					wc.setAttribute(key, intValue.intValue());
				}
				continue;
			}
			if (ServerManagementAPIConstants.ATTR_TYPE_BOOL.equals(attrType)) {
				Boolean boolValue = coerceBoolean(value);
				if (boolValue != null) {
					wc.setAttribute(key, boolValue.booleanValue());
				}
				continue;
			}
			if (ServerManagementAPIConstants.ATTR_TYPE_LIST.equals(attrType) && value instanceof List) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				wc.setAttribute(key, list);
				continue;
			}
			if (ServerManagementAPIConstants.ATTR_TYPE_MAP.equals(attrType) && value instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, String> map = (Map<String, String>) value;
				wc.setAttribute(key, map);
				continue;
			}
			wc.setAttribute(key, value == null ? null : String.valueOf(value));
		}
	}

	private String describeRuntime(IRuntime runtime) {
		if (runtime == null) {
			return "<null>";
		}
		String typeId = runtime.getRuntimeType() == null ? "<null>" : runtime.getRuntimeType().getId();
		return "id=" + runtime.getId() + ", name=" + runtime.getName() + ", workingCopy=" + runtime.isWorkingCopy()
				+ ", type=" + typeId + ", location="
				+ (runtime.getLocation() == null ? "<null>" : runtime.getLocation().toOSString());
	}

	private Map<String, String> collectAttributeTypes(IServerType type) {
		Map<String, String> types = new HashMap<>();
		addAttributeTypes(types, type.getRequiredAttributes());
		addAttributeTypes(types, type.getOptionalAttributes());
		return types;
	}

	private void addAttributeTypes(Map<String, String> types, Attributes attrs) {
		if (attrs == null) {
			return;
		}
		CreateServerAttributesUtility util = new CreateServerAttributesUtility(attrs);
		Set<String> keys = util.listAttributes();
		for (String key : keys) {
			String attrType = util.getAttributeType(key);
			if (attrType != null) {
				types.put(key, attrType);
			}
		}
	}

	private Integer coerceInteger(Object value) {
		if (value instanceof Integer) {
			return (Integer) value;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		if (value instanceof String) {
			try {
				return Integer.valueOf((String) value);
			} catch (NumberFormatException nfe) {
				return null;
			}
		}
		return null;
	}

	private Boolean coerceBoolean(Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof String) {
			String s = ((String) value).trim();
			if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
				return Boolean.valueOf(s);
			}
		}
		return null;
	}
	
}
