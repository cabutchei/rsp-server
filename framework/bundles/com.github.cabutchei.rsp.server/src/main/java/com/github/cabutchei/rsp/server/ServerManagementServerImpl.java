/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.github.cabutchei.rsp.api.RSPWTPClient;
import com.github.cabutchei.rsp.api.RSPServer;
import com.github.cabutchei.rsp.api.WTPServer;
import com.github.cabutchei.rsp.api.ServerManagementAPIConstants;
import com.github.cabutchei.rsp.api.SocketLauncher;
import com.github.cabutchei.rsp.api.dao.Attributes;
import com.github.cabutchei.rsp.api.dao.ClientCapabilitiesRequest;
import com.github.cabutchei.rsp.api.dao.CommandLineDetails;
import com.github.cabutchei.rsp.api.dao.CreateServerResponse;
import com.github.cabutchei.rsp.api.dao.CreateServerWorkflowRequest;
import com.github.cabutchei.rsp.api.dao.DeploymentAssemblyRequest;
import com.github.cabutchei.rsp.api.dao.DeploymentAssemblyResponse;
import com.github.cabutchei.rsp.api.dao.DeploymentAssemblyUpdateRequest;
import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.api.dao.DiscoveryPath;
import com.github.cabutchei.rsp.api.dao.DownloadRuntimeDescription;
import com.github.cabutchei.rsp.api.dao.DownloadSingleRuntimeRequest;
import com.github.cabutchei.rsp.api.dao.DidChangeWatchedFilesParams;
import com.github.cabutchei.rsp.api.dao.DidChangeWorkspaceFoldersParams;
import com.github.cabutchei.rsp.api.dao.ExportEarRequest;
import com.github.cabutchei.rsp.api.dao.GetServerJsonResponse;
import com.github.cabutchei.rsp.api.dao.InitializeParams;
import com.github.cabutchei.rsp.api.dao.InitializeResult;
import com.github.cabutchei.rsp.api.dao.JobHandle;
import com.github.cabutchei.rsp.api.dao.JobProgress;
import com.github.cabutchei.rsp.api.dao.ClasspathContainerEntry;
import com.github.cabutchei.rsp.api.dao.ClasspathContainerMapping;
import com.github.cabutchei.rsp.api.dao.ClasspathContainerMappings;
import com.github.cabutchei.rsp.api.dao.JreContainerMappings;
import com.github.cabutchei.rsp.api.dao.LaunchAttributesRequest;
import com.github.cabutchei.rsp.api.dao.LaunchParameters;
import com.github.cabutchei.rsp.api.dao.ListDeployableResourcesResponse;
import com.github.cabutchei.rsp.api.dao.ListDeployablesResponse;
import com.github.cabutchei.rsp.api.dao.ListDeploymentOptionsResponse;
import com.github.cabutchei.rsp.api.dao.ListDownloadRuntimeResponse;
import com.github.cabutchei.rsp.api.dao.ListServerActionResponse;
import com.github.cabutchei.rsp.api.dao.ListWorkspaceProjectsResponse;
import com.github.cabutchei.rsp.api.dao.ModuleState;
import com.github.cabutchei.rsp.api.dao.PublishServerRequest;
import com.github.cabutchei.rsp.api.dao.ServerActionRequest;
import com.github.cabutchei.rsp.api.dao.ServerAttributes;
import com.github.cabutchei.rsp.api.dao.ServerBean;
import com.github.cabutchei.rsp.api.dao.ServerCapabilitiesResponse;
import com.github.cabutchei.rsp.api.dao.ServerDeployableReference;
import com.github.cabutchei.rsp.api.dao.ServerHandle;
import com.github.cabutchei.rsp.api.dao.ServerLaunchMode;
import com.github.cabutchei.rsp.api.dao.ServerStartingAttributes;
import com.github.cabutchei.rsp.api.dao.ServerState;
import com.github.cabutchei.rsp.api.dao.ServerType;
import com.github.cabutchei.rsp.api.dao.StartServerResponse;
import com.github.cabutchei.rsp.api.dao.Status;
import com.github.cabutchei.rsp.api.dao.StopServerAttributes;
import com.github.cabutchei.rsp.api.dao.UpdateServerRequest;
import com.github.cabutchei.rsp.api.dao.UpdateServerResponse;
import com.github.cabutchei.rsp.api.dao.WorkspaceProject;
import com.github.cabutchei.rsp.api.dao.WorkflowResponse;
import com.github.cabutchei.rsp.api.dao.util.CreateServerAttributesUtility;
import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IPath;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.MultiStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.NullProgressMonitor;
import com.github.cabutchei.rsp.eclipse.core.runtime.Path;
import com.github.cabutchei.rsp.eclipse.osgi.util.NLS;
import com.github.cabutchei.rsp.runtime.core.model.DownloadRuntime;
import com.github.cabutchei.rsp.runtime.core.model.IDownloadRuntimeRunner;
import com.github.cabutchei.rsp.runtime.core.model.IDownloadRuntimesProvider;
import com.github.cabutchei.rsp.server.core.internal.ServerStringConstants;
import com.github.cabutchei.rsp.server.discovery.serverbeans.ServerBeanLoader;
import com.github.cabutchei.rsp.server.model.RemoteEventManager;
import com.github.cabutchei.rsp.server.spi.client.ClientThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cabutchei.rsp.server.spi.jobs.IJob;
import com.github.cabutchei.rsp.server.spi.model.IServerManagementModel;
import com.github.cabutchei.rsp.server.spi.model.IWorkspaceModelCapability;
import com.github.cabutchei.rsp.server.spi.servertype.IServer;
import com.github.cabutchei.rsp.server.spi.servertype.IServerDelegate;
import com.github.cabutchei.rsp.server.spi.servertype.IModuleStateProvider;
import com.github.cabutchei.rsp.server.spi.servertype.IServerType;
import com.github.cabutchei.rsp.server.spi.util.AlphanumComparator;
import com.github.cabutchei.rsp.server.spi.util.StatusConverter;
import com.github.cabutchei.rsp.server.spi.workspace.DeploymentAssemblyEntry;
import com.github.cabutchei.rsp.server.spi.workspace.DeployableArtifact;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceInitializationService;
import com.github.cabutchei.rsp.server.workspace.InitHandler;
import com.github.cabutchei.rsp.server.workspace.WorkspaceFolderChangeHandler;
import com.github.cabutchei.rsp.server.workspace.WorkspaceEventsHandler;



public class ServerManagementServerImpl implements RSPServer, WTPServer {

	private static final Logger LOG = LoggerFactory.getLogger(ServerManagementServerImpl.class);
	private static final int ASYNC_THREAD_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors());
	private static final int ASYNC_QUEUE_CAPACITY = 256;
	private static final Object ASYNC_EXECUTOR_LOCK = new Object();
	private static volatile ExecutorService osgiExecutor;

	private final List<RSPWTPClient> clients = new CopyOnWriteArrayList<>();
	private final List<SocketLauncher<RSPWTPClient>> launchers = new CopyOnWriteArrayList<>();
	
	private final IServerManagementModel managementModel;
	private final RemoteEventManager remoteEventManager;
	private final InitHandler initHandler;
	private final WorkspaceEventsHandler workspaceEventsHandler;
	private ServerManagementServerLauncher launcher;
	
	public ServerManagementServerImpl(ServerManagementServerLauncher launcher, 
			IServerManagementModel managementModel) {
		this.launcher = launcher;
		this.managementModel = managementModel;
		this.remoteEventManager = createRemoteEventManager();
		IProjectsManager projectsManager = getProjectsManager();
		this.initHandler = new InitHandler(managementModel, projectsManager);
		this.workspaceEventsHandler = new WorkspaceEventsHandler(projectsManager);
	}
	
	protected RemoteEventManager createRemoteEventManager() {
		return new RemoteEventManager(this);
	}
	
	public List<RSPWTPClient> getClients() {
		return new ArrayList<>(clients);
	}
	
	/**
	 * Connect the given client.
	 * Return a runnable which should be executed to disconnect the client.
	 * This method is called *before* the server begins actually listening to the socket.
	 * Any functionality which requires sending a jsonrequest to the client
	 * should NOT be performed in this method, and should instead be performed
	 * in clientAdded instead.
	 */
	public Runnable addClient(SocketLauncher<RSPWTPClient> launcher) {
		this.launchers.add(launcher);
		RSPWTPClient client = launcher.getRemoteProxy();
		this.clients.add(client);
		LOG.info("addClient: now {} active launchers, {} clients", this.launchers.size(), this.clients.size());
		return () -> this.removeClient(launcher);
	}

	public void clientAdded(SocketLauncher<RSPWTPClient> launcher) {
		this.managementModel.clientAdded(launcher.getRemoteProxy());
		this.remoteEventManager.initClientWithServerStates(launcher.getRemoteProxy());
	}
	
	protected void removeClient(SocketLauncher<RSPWTPClient> launcher) {
		this.launchers.remove(launcher);
		this.managementModel.clientRemoved(launcher.getRemoteProxy());
		this.clients.remove(launcher.getRemoteProxy());
		IWorkspaceInitializationService initializationService = getWorkspaceInitializationService();
		if (initializationService != null) {
			initializationService.releaseInitialization(toClientId(launcher.getRemoteProxy()));
		}
		LOG.info("removeClient: now {} active launchers, {} clients", this.launchers.size(), this.clients.size());
	}
	
	public List<SocketLauncher<RSPWTPClient>> getActiveLaunchers() {
		return new ArrayList<>(launchers);
	}
	
	public IServerManagementModel getModel() {
		return managementModel;
	}

	/**
	 * Returns existing discovery paths.
	 */
	@Override
	public CompletableFuture<List<DiscoveryPath>> getDiscoveryPaths() {
		return CompletableFuture.completedFuture(managementModel.getDiscoveryPathModel().getPaths());
	}

	/**
	 * Adds a path to our list of discovery paths
	 */
	@Override
	public CompletableFuture<Status> addDiscoveryPath(DiscoveryPath path) {
		return createCompletableFuture(() -> addDiscoveryPathSync(path));
	}
	
	private Status addDiscoveryPathSync(DiscoveryPath path) {
		if( isEmptyDiscoveryPath(path)) 
			return invalidParameterStatus();
		String fp = path.getFilepath();
		IPath ipath = new Path(fp);
		if( !ipath.isAbsolute()) {
			return invalidParameterStatus();
		}
		boolean ret = managementModel.getDiscoveryPathModel().addPath(path);
		return booleanToStatus(ret, "Discovery path not added: " + path.getFilepath());
	}

	@Override
	public CompletableFuture<Status> removeDiscoveryPath(DiscoveryPath path) {
		return createCompletableFuture(() -> removeDiscoveryPathSync(path));
	}
	
	public Status removeDiscoveryPathSync(DiscoveryPath path) {
		if( isEmptyDiscoveryPath(path)) 
			return invalidParameterStatus();
		String fp = path.getFilepath();
		IPath ipath = new Path(fp);
		if( !ipath.isAbsolute()) {
			return invalidParameterStatus();
		}
		boolean ret = managementModel.getDiscoveryPathModel().removePath(path);
		return booleanToStatus(ret, "Discovery path not removed: " + path.getFilepath());
	}

	private boolean isEmptyDiscoveryPath(DiscoveryPath path) {
		return path == null || isEmpty(path.getFilepath());
	}

	@Override
	public CompletableFuture<List<ServerBean>> findServerBeans(DiscoveryPath path) {
		return createCompletableFuture(() -> findServerBeansSync(path));
	}

	private List<ServerBean> findServerBeansSync(DiscoveryPath path) {
		List<ServerBean> ret = new ArrayList<>();
		if( path == null || isEmpty(path.getFilepath())) {
			return ret;
		}
		
		String fp = path.getFilepath();
		IPath ipath = new Path(fp);
		if( !ipath.isAbsolute()) {
			return ret;
		}

		ServerBeanLoader loader = new ServerBeanLoader(new File(path.getFilepath()), managementModel);
		ServerBean bean = loader.getServerBean();
		if( bean != null )
			ret.add(bean);
		return ret;	
	}

	@Override
	public void shutdown() {
		final RSPWTPClient rspc = ClientThreadLocal.getActiveClient();
		new Thread("Shutdown") {
			@Override
			public void run() {
				ClientThreadLocal.setActiveClient(rspc);
				shutdownSync();
				ClientThreadLocal.setActiveClient(null);
			}
		}.start();
	}

	@Override
	public void disconnectClient() {
		final RSPWTPClient rspc = ClientThreadLocal.getActiveClient();
		new Thread("Shutdown") {
			@Override
			public void run() {
				ClientThreadLocal.setActiveClient(rspc);
				try {
					Thread.sleep(200);
				} catch(InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				launcher.closeConnection(rspc);
				ClientThreadLocal.setActiveClient(null);
			}
		}.start();
	}

	private void shutdownSync() {
		managementModel.dispose();
		launcher.shutdown();
	}
	
	@Override
	public CompletableFuture<List<ServerHandle>> getServerHandles() {
		return createCompletableFuture(() -> getServerHandlesSync());
	}
	
	private List<ServerHandle> getServerHandlesSync() {
		ServerHandle[] all = managementModel.getServerModel().getServerHandles();
		return Arrays.asList(all);
	}

	@Override
	public CompletableFuture<Status> deleteServer(ServerHandle handle) {
		return createCompletableFuture(() -> deleteServerSync(handle));
	}
	
	private Status deleteServerSync(ServerHandle handle) {
		Status validate = verifyServerAndDelegate(handle);
		if( validate != null && !validate.isOK()) 
			return validate;
		
		IServer server = managementModel.getServerModel().getServer(handle.getId());
		if( server.getDelegate().getServerRunState() != ServerManagementAPIConstants.STATE_STOPPED) {
			new Thread("Stopping server: " + server.getName()) {
				public void run() {
					IServerDelegate del = server.getDelegate();
					del.stop(false);
				}
			}.start();
		}
		boolean b = managementModel.getServerModel().removeServer(server);
		return booleanToStatus(b, "Server not removed: " + handle.getId());
	}

	@Override
	public CompletableFuture<Attributes> getRequiredAttributes(ServerType type) {
		return createCompletableFuture(() -> getRequiredAttributesSync(type));
	}
	
	private Attributes getRequiredAttributesSync(ServerType type) {
		if( type == null || isEmpty(type.getId())) {
			return null;
		}
		IServerType serverType = managementModel.getServerModel().getIServerType(type.getId());
		Attributes rspa = managementModel.getServerModel().getRequiredAttributes(serverType);
		return rspa;
	}

	@Override
	public CompletableFuture<Attributes> getOptionalAttributes(ServerType type) {
		return createCompletableFuture(() -> getOptionalAttributesSync(type));
	}

	private Attributes getOptionalAttributesSync(ServerType type) {
		if( type == null || isEmpty(type.getId())) {
			return null;
		}
		IServerType serverType = managementModel.getServerModel().getIServerType(type.getId());
		return managementModel.getServerModel().getOptionalAttributes(serverType);
	}
	
	@Override
	public CompletableFuture<List<ServerLaunchMode>> getLaunchModes(ServerType type) {
		return createCompletableFuture(() -> getLaunchModesSync(type));
	}

	private List<ServerLaunchMode> getLaunchModesSync(ServerType type) {
		if( type == null || isEmpty(type.getId()) ) {
			return null;
		}
		IServerType serverType = managementModel.getServerModel().getIServerType(type.getId());
		List<ServerLaunchMode> l = managementModel.getServerModel()
				.getLaunchModes(serverType);
		return l;
	}
	
	@Override
	public CompletableFuture<Attributes> getRequiredLaunchAttributes(LaunchAttributesRequest req) {
		return createCompletableFuture(() -> getRequiredLaunchAttributesSync(req));
	}
	private Attributes getRequiredLaunchAttributesSync(LaunchAttributesRequest req) {
		if( req == null || isEmpty(req.getServerTypeId()) || isEmpty(req.getMode())) {
			return null;
		}
		IServerType serverType = managementModel.getServerModel().getIServerType(req.getServerTypeId());
		Attributes rspa = managementModel.getServerModel().getRequiredLaunchAttributes(serverType);
		return rspa;
	}

	@Override
	public CompletableFuture<Attributes> getOptionalLaunchAttributes(LaunchAttributesRequest req) {
		return createCompletableFuture(() -> getOptionalLaunchAttributesSync(req));
	}

	private Attributes getOptionalLaunchAttributesSync(LaunchAttributesRequest req) {
		if( req == null || isEmpty(req.getServerTypeId()) || isEmpty(req.getMode())) {
			return null;
		}
		IServerType serverType = managementModel.getServerModel().getIServerType(req.getServerTypeId());
		Attributes rspa = managementModel.getServerModel().getOptionalLaunchAttributes(serverType);
		return rspa;
	}
	
	@Override
	public CompletableFuture<CreateServerResponse> createServer(ServerAttributes attr) {
		return createCompletableFuture(() -> createServerSync(attr));
	}

	private CreateServerResponse createServerSync(ServerAttributes attr) {
		if( attr == null || isEmpty(attr.getId()) || isEmpty(attr.getServerType())) {
			Status s = invalidParameterStatus();
			return new CreateServerResponse(s, null);
		}
		
		String serverType = attr.getServerType();
		String id = attr.getId();
		Map<String, Object> attributes = attr.getAttributes();
		
		return managementModel.getServerModel().createServer(serverType, id, attributes);
	}

	
	@Override
	public CompletableFuture<GetServerJsonResponse> getServerAsJson(ServerHandle sh) {
		return createCompletableFuture(() -> getServerAsJsonSync(sh));
	}

	private GetServerJsonResponse getServerAsJsonSync(ServerHandle sh) {
		Status valid = verifyServerAndDelegate(sh);
		if( valid != null && !valid.isOK()) {
			GetServerJsonResponse ret = new GetServerJsonResponse();
			ret.setStatus(valid);
			return ret;
		}
		IServer server = managementModel.getServerModel().getServer(sh.getId());
		GetServerJsonResponse ret = new GetServerJsonResponse();
		ret.setServerHandle(sh);
		try {
			String json = server.asJson(new NullProgressMonitor());
			ret.setServerJson(json);
			Status stat = StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS);
			ret.setStatus(stat);
		} catch(CoreException ce) {
			ret.setStatus(StatusConverter.convert(ce.getStatus()));
		}
		return ret;
	}
	
	@Override
	public CompletableFuture<UpdateServerResponse> updateServer(UpdateServerRequest req) {
		return createCompletableFuture(() -> updateServerSync(req));
	}

	private UpdateServerResponse updateServerSync(UpdateServerRequest req) {
		try {
			UpdateServerResponse resp = managementModel.getServerModel().updateServer(req);
			if (resp == null) {
				resp = new UpdateServerResponse();
				resp.getValidation().setStatus(errorStatus("Update server failed: server model returned null response"));
				return resp;
			}
			if (req == null) {
				return resp;
			}
			resp.setServerJson(getServerAsJsonSync(req.getHandle()));
			return resp;
		} catch (Throwable t) {
			String serverId = req != null && req.getHandle() != null ? req.getHandle().getId() : "<unknown>";
			LOG.error("Unexpected failure in updateServerSync for {}", serverId, t);
			UpdateServerResponse resp = new UpdateServerResponse();
			String message = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
			resp.getValidation().setStatus(errorStatus("Update server failed: " + message, t));
			return resp;
		}
	}

	@Override
	public CompletableFuture<List<ServerType>> getServerTypes() {
		return createCompletableFuture(() -> getServerTypesSync());
	}

	private List<ServerType> getServerTypesSync() {
		ServerType[] types = managementModel.getServerModel().getAccessibleServerTypes();
		Comparator<ServerType> c = (h1,h2) -> new AlphanumComparator().compare(h1.getVisibleName(), h2.getVisibleName()); 
		return Arrays.asList(types).stream().sorted(c).collect(Collectors.toList());
	}

	@Override
	public CompletableFuture<StartServerResponse> startServerAsync(LaunchParameters attr) {
		return createCompletableFuture(() -> startServerImpl(attr));
	}

	private StartServerResponse startServerImpl(LaunchParameters attr) {
		if( attr == null || isEmpty(attr.getMode()) || isEmpty(attr.getParams().getId())) {
			Status is = errorStatus("Invalid Parameter", null);
			return (new StartServerResponse(is, null));
		}

		Status valid = verifyServerAndDelegate(attr.getParams().getId());
		if( valid != null && !valid.isOK()) {
			return (new StartServerResponse(valid, null));
		}
		String id = attr.getParams().getId();
		IServer server = managementModel.getServerModel().getServer(id);
		IServerDelegate del = server.getDelegate();
		try {
			return del.start(attr.getMode());
		} catch( Exception e ) {
			Status is = errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
			return new StartServerResponse(is, null);
		}
	}
	
	@Override
	public CompletableFuture<Status> stopServerAsync(StopServerAttributes attr) {
		return createCompletableFuture(() -> stopServerImpl(attr));
	}

	private Status stopServerImpl(StopServerAttributes attr) {
		if( attr == null || isEmpty(attr.getId())) {
			return invalidParameterStatus();
		}

		IServer server = managementModel.getServerModel().getServer(attr.getId());
		if( server == null ) {
			String msg = NLS.bind(ServerStringConstants.SERVER_DNE, attr.getId());
			return errorStatus(msg);
		}
		IServerDelegate del = server.getDelegate();
		if( del == null ) {
			return errorStatus("An unexpected error occurred: Server " + attr.getId() + " has no delegate.");
		}
		
		if(del.getServerRunState() == IServerDelegate.STATE_STOPPED && !attr.isForce()) {
			return errorStatus(
					"The server is already marked as stopped. If you wish to force a stop request, please set the force flag to true.");
		}
		
		try {
			return StatusConverter.convert(del.stop(attr.isForce()));
		} catch( Exception e ) {
			return errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
		}

	}

	@Override
	public CompletableFuture<Status> startModule(ServerDeployableReference attr) {
		return createCompletableFuture(() -> startModuleImpl(attr));
	}

	private Status startModuleImpl(ServerDeployableReference attr) {
		if( attr == null || attr.getServer() == null || attr.getDeployableReference() == null) {
			return invalidParameterStatus();
		}
		IServer server = managementModel.getServerModel().getServer(attr.getServer().getId());
		IServerDelegate del = server.getDelegate();
		if( del == null ) {
			return errorStatus("An unexpected error occurred: Server " + attr.getServer().getId() + " has no delegate.");
		}
		if(del.getServerRunState() == IServerDelegate.STATE_STOPPED) {
			return errorStatus(
				"Cannot start module when server is stopped.");
			}
		try {
			return StatusConverter.convert(del.startModule(attr.getDeployableReference()));
		} catch( Exception e ) {
			return errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
		}
	}

	@Override
	public CompletableFuture<Status> stopModule(ServerDeployableReference attr) {
		return createCompletableFuture(() -> stopModuleImpl(attr));
	}
		
	private Status stopModuleImpl(ServerDeployableReference attr) {
		if( attr == null) return invalidParameterStatus();
		
		IServer server = managementModel.getServerModel().getServer(attr.getServer().getId());
		IServerDelegate del = server.getDelegate();
		if( del == null ) {
			return errorStatus("An unexpected error occurred: Server " + attr.getServer().getId() + " has no delegate.");
		}
		if(del.getServerRunState() == IServerDelegate.STATE_STOPPED) {
			return errorStatus(
					"Cannot stop module when server is stopped.");
		}
		try {
			return StatusConverter.convert(del.stopModule(attr.getDeployableReference()));
		} catch( Exception e ) {
			return errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
		}
	}

	@Override
	public CompletableFuture<List<ModuleState>> getModuleStates(ServerHandle handle) {
		return createCompletableFuture(() -> getModuleStatesSync(handle));
	}

	private List<ModuleState> getModuleStatesSync(ServerHandle handle) {
		List<ModuleState> empty = new ArrayList<>();
		if( handle == null || isEmpty(handle.getId())) {
			return empty;
		}
		IServer server = managementModel.getServerModel().getServer(handle.getId());
		if( server == null ) {
			return empty;
		}
		IServerDelegate del = server.getDelegate();
		if( del instanceof IModuleStateProvider ) {
			List<ModuleState> states = ((IModuleStateProvider) del).getModuleStates();
			return states == null ? empty : states;
		}
		return empty;
	}

	@Override
	public CompletableFuture<CommandLineDetails> getLaunchCommand(LaunchParameters req) {
		return createCompletableFuture(() -> getLaunchCommandSync(req));
	}

	private CommandLineDetails getLaunchCommandSync(LaunchParameters req) {
		boolean empty = req == null || isEmpty(req.getMode()) || isEmpty(req.getParams().getId()); 
		if( !empty ) {
			String id = req.getParams().getId();
			IServer server = managementModel.getServerModel().getServer(id);
			if( server != null ) {
				IServerDelegate del = server.getDelegate();
				if( del != null ) {
					try {
						return del.getStartLaunchCommand(req.getMode(), req.getParams());
					} catch( Exception e ) {
						// Ignore
					}
				}
			}
		}
		return null;
	}
	
	@Override
	public CompletableFuture<ServerState> getServerState(ServerHandle handle) {
		return createCompletableFuture(() -> getServerStateSync(handle));
	}
	public ServerState getServerStateSync(ServerHandle handle) {
		IServer is = managementModel.getServerModel().getServer(handle.getId());
		return is.getDelegate().getServerState();
	}
	
	@Override
	public CompletableFuture<Status> serverStartingByClient(ServerStartingAttributes attr) {
		return createCompletableFuture(() -> serverStartingByClientSync(attr));
	}

	private Status serverStartingByClientSync(ServerStartingAttributes attr) {
		if( attr == null || attr.getRequest() == null || isEmpty(attr.getRequest().getMode())
				|| isEmpty(attr.getRequest().getParams().getId())) {
			return invalidParameterStatus();
		}
		String id = attr.getRequest().getParams().getId();
		IServer server = managementModel.getServerModel().getServer(id);
		if( server == null ) {
			String msg = NLS.bind(ServerStringConstants.SERVER_DNE, id);
			return errorStatus(msg);
		}
		IServerDelegate del = server.getDelegate();
		if( del == null ) {
			return errorStatus(NLS.bind(ServerStringConstants.UNEXPECTED_ERROR_DELEGATE, id));
		}
		try {
			return StatusConverter.convert(del.clientSetServerStarting(attr));
		} catch( Exception e ) {
			return errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
		}
	}
	
	@Override
	public CompletableFuture<Status> serverStartedByClient(LaunchParameters attr) {
		return createCompletableFuture(() -> serverStartedByClientSync(attr));
	}

	private Status serverStartedByClientSync(LaunchParameters attr) {
		if( attr == null || attr.getParams() == null || isEmpty(attr.getParams().getId())) {
			return invalidParameterStatus();
		}

		String id = attr.getParams().getId();
		IServer server = managementModel.getServerModel().getServer(id);
		if( server == null ) {
			String msg = NLS.bind(ServerStringConstants.SERVER_DNE, id);
			return errorStatus(msg);
		}
		IServerDelegate del = server.getDelegate();
		if( del == null ) {
			return errorStatus("Server error: Server " + id + " does not have a delegate.");
		}

		try {
			return StatusConverter.convert(del.clientSetServerStarted(attr));
		} catch( Exception e ) {
			return errorStatus(ServerStringConstants.UNEXPECTED_ERROR, e);
		}
	}

	@Override
	public CompletableFuture<ServerCapabilitiesResponse> registerClientCapabilities(ClientCapabilitiesRequest request) {
		RSPWTPClient rspc = ClientThreadLocal.getActiveClient();
		IStatus s = managementModel.getCapabilityManagement().registerClientCapabilities(rspc, request);
		Status st = StatusConverter.convert(s);
		Map<String,String> resp2 = managementModel.getCapabilityManagement().getServerCapabilities();
		ServerCapabilitiesResponse resp = new ServerCapabilitiesResponse(st, resp2);
		return CompletableFuture.completedFuture(resp);
	}

	@Override
	public CompletableFuture<ListDeployableResourcesResponse> getDeployableResources(ServerHandle server) {
		return createCompletableFuture(() -> getDeployableResourcesSync(server));
	}

	@Override
	public CompletableFuture<ListWorkspaceProjectsResponse> listWorkspaceProjects() {
		return createCompletableFuture(() -> listWorkspaceProjectsSync());
	}

	@Override
	public CompletableFuture<Status> refreshWorkspaceProjects() {
		return createCompletableFuture(() -> refreshWorkspaceProjectsSync());
	}

	@Override
	public CompletableFuture<ListWorkspaceProjectsResponse> listEarProjects() {
		return createCompletableFuture(() -> listEarProjectsSync());
	}

	@Override
	public CompletableFuture<Status> exportEar(ExportEarRequest request) {
		return createCompletableFuture(() -> exportEarSync(request));
	}

	@Override
	public CompletableFuture<ListWorkspaceProjectsResponse> listDeploymentAssemblyProjects(DeploymentAssemblyRequest request) {
		return createCompletableFuture(() -> listDeploymentAssemblyProjectsSync(request));
	}

	private ListDeployableResourcesResponse getDeployableResourcesSync(ServerHandle server) {
		ListDeployableResourcesResponse resp = new ListDeployableResourcesResponse();
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			resp.setStatus(errorStatus("Projects manager unavailable"));
			return resp;
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			resp.setStatus(errorStatus("WTP service unavailable"));
			return resp;
		}
		List<DeployableArtifact> resources = wtpService.listDeployableResources(server);
		List<DeployableReference> refs = new ArrayList<>();
		if (resources != null) {
			refs = resources.stream()
					.filter(r -> r != null && r.getProjectName() != null)
					.map(r -> {
						DeployableReference ref = new DeployableReference(r.getId(), r.getLabel(),
								r.getDeployPath() == null ? null : r.getDeployPath().toString(),
								r.getModuleTypeId());
						return ref;
					})
					.distinct()
					.collect(Collectors.toList());
		}
		resp.setResources(refs);
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		return resp;
	}

	private ListWorkspaceProjectsResponse listWorkspaceProjectsSync() {
		ListWorkspaceProjectsResponse resp = new ListWorkspaceProjectsResponse();
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			resp.setStatus(errorStatus("Projects manager unavailable"));
			return resp;
		}
		List<com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject> projects = projectsManager.listWorkspaceProjects();
		List<WorkspaceProject> mapped = new ArrayList<>();
		if (projects != null) {
			mapped = projects.stream()
					.filter(p -> p != null && p.getName() != null)
					.map(p -> new WorkspaceProject(p.getName(),
							p.getLocation() == null ? null : p.getLocation().toString(),
							p.isOpen()))
					.collect(Collectors.toList());
		}
		resp.setProjects(mapped);
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		return resp;
	}

	private Status refreshWorkspaceProjectsSync() {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return errorStatus("Projects manager unavailable");
		}

		List<IStatus> failures = new ArrayList<>();
		IStatus importStatus = projectsManager.importAllWorkspaceProjects();
		if (importStatus != null && !importStatus.isOK()) {
			failures.add(importStatus);
		}

		List<com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject> projects = projectsManager.listWorkspaceProjects();
		if (projects != null) {
			for (com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject project : projects) {
				if (project == null || project.getName() == null || project.getName().isEmpty()) {
					continue;
				}
				IStatus refreshStatus = projectsManager.refreshProject(project.getName());
				if (refreshStatus != null && !refreshStatus.isOK()) {
					failures.add(refreshStatus);
				}
			}
		}

		if (failures.isEmpty()) {
			return StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS);
		}
		if (failures.size() == 1) {
			return StatusConverter.convert(failures.get(0));
		}
		MultiStatus status = new MultiStatus(ServerCoreActivator.BUNDLE_ID, IStatus.ERROR,
				failures.toArray(new IStatus[0]), "One or more workspace projects failed to refresh", null);
		return StatusConverter.convert(status);
	}

	private ListWorkspaceProjectsResponse listEarProjectsSync() {
		ListWorkspaceProjectsResponse resp = new ListWorkspaceProjectsResponse();
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			resp.setStatus(errorStatus("Projects manager unavailable"));
			return resp;
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			resp.setStatus(errorStatus("WTP service unavailable"));
			return resp;
		}
		List<com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject> projects = wtpService.listEarProjects();
		List<WorkspaceProject> mapped = new ArrayList<>();
		if (projects != null) {
			mapped = projects.stream()
					.filter(p -> p != null && p.getName() != null)
					.map(p -> new WorkspaceProject(p.getName(),
							p.getLocation() == null ? null : p.getLocation().toString(),
							p.isOpen()))
					.collect(Collectors.toList());
		}
		resp.setProjects(mapped);
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		return resp;
	}

	private Status exportEarSync(ExportEarRequest request) {
		if (request == null) {
			return invalidParameterStatus();
		}
		String pathString = request.getPath();
		String projectName = request.getProjectName();
		String destinationString = request.getDestinationPath();
		if ((pathString == null || pathString.isEmpty()) && (projectName == null || projectName.isEmpty())) {
			return invalidParameterStatus();
		}
		if (destinationString == null || destinationString.isBlank()) {
			return invalidParameterStatus();
		}
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return errorStatus("Projects manager unavailable");
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			return errorStatus("WTP service unavailable");
		}
		java.nio.file.Path projectPath = null;
		if (pathString != null && !pathString.isEmpty()) {
			try {
				projectPath = Paths.get(pathString).toAbsolutePath().normalize();
			} catch (InvalidPathException ipe) {
				return errorStatus("Invalid project path: " + pathString, ipe);
			}
		}
		java.nio.file.Path destinationPath;
		try {
			destinationPath = Paths.get(destinationString).toAbsolutePath().normalize();
		} catch (InvalidPathException ipe) {
			return errorStatus("Invalid destination path: " + destinationString, ipe);
		}
		IStatus status = wtpService.exportEar(projectPath, projectName, destinationPath, request.isExportSource());
		return StatusConverter.convert(status);
	}

	private ListWorkspaceProjectsResponse listDeploymentAssemblyProjectsSync(DeploymentAssemblyRequest request) {
		ListWorkspaceProjectsResponse resp = new ListWorkspaceProjectsResponse();
		if (request == null) {
			resp.setStatus(invalidParameterStatus());
			return resp;
		}
		String pathString = request.getPath();
		String projectName = request.getProjectName();
		if ((pathString == null || pathString.isEmpty()) && (projectName == null || projectName.isEmpty())) {
			resp.setStatus(invalidParameterStatus());
			return resp;
		}
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			resp.setStatus(errorStatus("Projects manager unavailable"));
			return resp;
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			resp.setStatus(errorStatus("WTP service unavailable"));
			return resp;
		}
		java.nio.file.Path projectPath = null;
		if (pathString != null && !pathString.isEmpty()) {
			try {
				projectPath = Paths.get(pathString);
			} catch (InvalidPathException e) {
				resp.setStatus(errorStatus("Invalid project path: " + pathString, e));
				return resp;
			}
		}
		List<com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject> projects =
				wtpService.listDeploymentAssemblyProjects(projectPath, projectName);
		List<WorkspaceProject> mapped = new ArrayList<>();
		if (projects != null) {
			mapped = projects.stream()
					.filter(p -> p != null && p.getName() != null)
					.map(p -> new WorkspaceProject(p.getName(),
							p.getLocation() == null ? null : p.getLocation().toString(),
							p.isOpen()))
					.collect(Collectors.toList());
		}
		resp.setProjects(mapped);
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		return resp;
	}

	@Override
	public CompletableFuture<DeploymentAssemblyResponse> getDeploymentAssembly(DeploymentAssemblyRequest request) {
		return createCompletableFuture(() -> getDeploymentAssemblySync(request));
	}

	@Override
	public CompletableFuture<Status> addDeploymentAssemblyEntry(DeploymentAssemblyUpdateRequest request) {
		return createCompletableFuture(() -> updateDeploymentAssemblyEntry(request, true));
	}

	@Override
	public CompletableFuture<Status> removeDeploymentAssemblyEntry(DeploymentAssemblyUpdateRequest request) {
		return createCompletableFuture(() -> updateDeploymentAssemblyEntry(request, false));
	}

	private DeploymentAssemblyResponse getDeploymentAssemblySync(DeploymentAssemblyRequest request) {
		DeploymentAssemblyResponse resp = new DeploymentAssemblyResponse();
		if (request == null) {
			resp.setStatus(invalidParameterStatus());
			return resp;
		}
		String pathString = request.getPath();
		String projectName = request.getProjectName();
		if ((pathString == null || pathString.isEmpty()) && (projectName == null || projectName.isEmpty())) {
			resp.setStatus(invalidParameterStatus());
			return resp;
		}
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			resp.setStatus(errorStatus("Projects manager unavailable"));
			return resp;
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			resp.setStatus(errorStatus("WTP service unavailable"));
			return resp;
		}
		java.nio.file.Path projectPath = null;
		if (pathString != null && !pathString.isEmpty()) {
			try {
				projectPath = Paths.get(pathString);
			} catch (InvalidPathException e) {
				resp.setStatus(errorStatus("Invalid project path: " + pathString, e));
				return resp;
			}
		}
		List<DeploymentAssemblyEntry> entries = wtpService.getDeploymentAssembly(projectPath, projectName);
		if (entries == null) {
			resp.setEntries(new ArrayList<>());
			resp.setStatus(errorStatus("Deployment assembly unavailable for project"));
			return resp;
		}
		List<com.github.cabutchei.rsp.api.dao.DeploymentAssemblyEntry> mapped = entries.stream()
				.filter(entry -> entry != null)
				.map(entry -> new com.github.cabutchei.rsp.api.dao.DeploymentAssemblyEntry(
						entry.getSourcePath(),
						entry.getDeployPath(),
						entry.getSourceKind(),
						entry.getDeployKind()))
				.collect(Collectors.toList());
		resp.setEntries(mapped);
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		return resp;
	}

	private Status updateDeploymentAssemblyEntry(DeploymentAssemblyUpdateRequest request, boolean add) {
		if (request == null) {
			return invalidParameterStatus();
		}
		String pathString = request.getPath();
		String projectName = request.getProjectName();
		if ((pathString == null || pathString.isEmpty()) && (projectName == null || projectName.isEmpty())) {
			return invalidParameterStatus();
		}
		if (request.getEntry() == null) {
			return invalidParameterStatus();
		}
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return errorStatus("Projects manager unavailable");
		}
		IWTPService wtpService = getWTPService(projectsManager);
		if (wtpService == null) {
			return errorStatus("WTP service unavailable");
		}
		java.nio.file.Path projectPath = null;
		if (pathString != null && !pathString.isEmpty()) {
			try {
				projectPath = Paths.get(pathString);
			} catch (InvalidPathException e) {
				return errorStatus("Invalid project path: " + pathString, e);
			}
		}
		DeploymentAssemblyEntry entry = new DeploymentAssemblyEntry(
				request.getEntry().getSourcePath(),
				request.getEntry().getDeployPath(),
				request.getEntry().getSourceKind(),
				request.getEntry().getDeployKind());
		IStatus status = add
				? wtpService.addDeploymentAssemblyEntry(projectPath, projectName, entry)
				: wtpService.removeDeploymentAssemblyEntry(projectPath, projectName, entry);
		return StatusConverter.convert(status == null
				? new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Operation failed")
				: status);
	}

	private IWTPService getWTPService(IProjectsManager projectsManager) {
		return projectsManager == null ? null : projectsManager.getWTPService();
	}

	private void invalidateDeployableResourceCache(ServerHandle server) {
		IWTPService wtpService = getWTPService(getProjectsManager());
		if (wtpService != null) {
			wtpService.invalidateDeployableResourceCache(server);
		}
	}

	private IProjectsManager getProjectsManager() {
		IWorkspaceModelCapability capability = getWorkspaceModelCapability();
		return capability == null ? null : capability.getProjectsManager();
	}

	private IWorkspaceModelCapability getWorkspaceModelCapability() {
		return managementModel instanceof IWorkspaceModelCapability
				? (IWorkspaceModelCapability) managementModel
				: null;
	}

	private IWorkspaceInitializationService getWorkspaceInitializationService() {
		IWorkspaceModelCapability capability = getWorkspaceModelCapability();
		return capability == null ? null : capability.getWorkspaceInitializationService();
	}

	private String toClientId(RSPWTPClient client) {
		if (client == null) {
			return null;
		}
		return "client-" + Integer.toHexString(System.identityHashCode(client));
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		return createCompletableFuture(() -> initializeSync(params));
	}

	private InitializeResult initializeSync(InitializeParams params) {
		InitializeResult result = initHandler.initialize(params);
		notifyJdtlsJreContainers();
		notifyJdtlsClasspathContainers();
		return result;
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		WorkspaceFolderChangeHandler handler = new WorkspaceFolderChangeHandler(getProjectsManager());
		handler.update(params);
		notifyJdtlsJreContainers();
		notifyJdtlsClasspathContainers();
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		workspaceEventsHandler.didChangeWatchedFiles(params);
		if (containsClasspathChange(params)) {
			notifyJdtlsJreContainers();
			notifyJdtlsClasspathContainers();
		}
	}

	private boolean containsClasspathChange(DidChangeWatchedFilesParams params) {
		if (params == null || params.getChanges() == null) {
			return false;
		}
		for (com.github.cabutchei.rsp.api.dao.FileEvent event : params.getChanges()) {
			if (event == null) {
				continue;
			}
			String uri = event.getUri();
			if (uri != null && uri.endsWith("/.classpath")) {
				return true;
			}
		}
		return false;
	}

	private void notifyJdtlsJreContainers() {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return;
		}
		List<com.github.cabutchei.rsp.server.spi.workspace.JreContainerMapping> mappings = projectsManager
				.listNonStandardJreContainers();
		if (mappings == null || mappings.isEmpty()) {
			return;
		}
		RSPWTPClient client = ClientThreadLocal.getActiveClient();
		if (client == null) {
			return;
		}
		List<com.github.cabutchei.rsp.api.dao.JreContainerMapping> apiMappings = new ArrayList<>();
		for (com.github.cabutchei.rsp.server.spi.workspace.JreContainerMapping mapping : mappings) {
			if (mapping == null) {
				continue;
			}
			String javaHome = mapping.getJavaHome() == null ? null : mapping.getJavaHome().toString();
			apiMappings.add(new com.github.cabutchei.rsp.api.dao.JreContainerMapping(
					mapping.getProjectName(),
					mapping.getProjectUri(),
					mapping.getContainerPath(),
					mapping.getVmName(),
					javaHome));
		}
		if (apiMappings.isEmpty()) {
			return;
		}
		client.jdtlsJreContainersDetected(new JreContainerMappings(apiMappings));
	}

	private void notifyJdtlsClasspathContainers() {
		IProjectsManager projectsManager = getProjectsManager();
		if (projectsManager == null) {
			return;
		}
		List<com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerMapping> mappings = projectsManager
				.listClasspathContainers();
		if (mappings == null || mappings.isEmpty()) {
			return;
		}
		RSPWTPClient client = ClientThreadLocal.getActiveClient();
		if (client == null) {
			return;
		}
		List<ClasspathContainerMapping> apiMappings = new ArrayList<>();
		for (com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerMapping mapping : mappings) {
			if (mapping == null) {
				continue;
			}
			List<ClasspathContainerEntry> entries = new ArrayList<>();
			List<com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerEntry> sourceEntries = mapping.getEntries();
			if (sourceEntries != null) {
				if (sourceEntries.size() == 0) {
					continue;
				}
				for (com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerEntry entry : sourceEntries) {
					if (entry == null) {
						continue;
					}
					entries.add(new ClasspathContainerEntry(
							entry.getEntryKind(),
							entry.getPath(),
							entry.getSourcePath(),
							entry.getSourceRootPath(),
							entry.getJavadocLocation(),
							entry.isExported()));
				}
			}
			apiMappings.add(new ClasspathContainerMapping(
					mapping.getProjectName(),
					mapping.getProjectUri(),
					mapping.getContainerPath(),
					mapping.getDescription(),
					entries));
		}
		if (apiMappings.isEmpty()) {
			return;
		}
		client.jdtlsClasspathContainersDetected(new ClasspathContainerMappings(apiMappings));
	}

	/*
	 * Utility methods below
	 */	
	private Status booleanToStatus(boolean b, String message) {
		IStatus s = null;
		if( b ) {
			s = com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS;
		} else {
			s = new com.github.cabutchei.rsp.eclipse.core.runtime.Status(
					IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, message);
		}
		return StatusConverter.convert(s);
	}

	private boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	private Status invalidParameterStatus() {
		IStatus s = new com.github.cabutchei.rsp.eclipse.core.runtime.Status(
				IStatus.ERROR, ServerCoreActivator.BUNDLE_ID, "Parameter is invalid. It may be null, missing required fields, or unacceptable values.");
		return StatusConverter.convert(s);
	}

	@Override
	public CompletableFuture<ListDeployablesResponse> getDeployables(ServerHandle handle) {
		return createCompletableFuture(() -> getDeployablesSync(handle));
	}

	// This API has no way to return an error. Should be changed
	public ListDeployablesResponse getDeployablesSync(ServerHandle handle) {
		String handleIdOrNull = handle == null ? null : handle.getId();
		String handleIdOrNullString = handleIdOrNull == null ? "null" : handleIdOrNull;
		
		if( handleIdOrNull == null ) {
			ListDeployablesResponse resp = new ListDeployablesResponse(
					null, errorStatus("Unable to locate server with null id."));
			return resp;
		}
		IServer server = managementModel.getServerModel().getServer(handle.getId());
		if( server == null ) {
			ListDeployablesResponse resp = new ListDeployablesResponse(
					null, errorStatus(NLS.bind("Unable to locate server {0}", 
							handleIdOrNullString)));
			return resp;
		}
		ListDeployablesResponse resp = new ListDeployablesResponse();
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		resp.setStates(managementModel.getServerModel().getDeployables(server));
		return resp;
	}
	
	public CompletableFuture<ListDeploymentOptionsResponse> listDeploymentOptions(ServerHandle handle) {
		return createCompletableFuture(() -> listDeploymentOptionsSync(handle));
	}
	
	// This API has no way to return an error. Should be changed
	public ListDeploymentOptionsResponse listDeploymentOptionsSync(ServerHandle handle) {
		ListDeploymentOptionsResponse resp = new ListDeploymentOptionsResponse();
		if( handle == null ) {
			resp.setStatus(errorStatus("Unable to locate server with null id"));
			resp.setAttributes(new CreateServerAttributesUtility().toPojo());
			return resp;
		}
		IServer server = managementModel.getServerModel().getServer(handle.getId());
		if( server == null || server.getDelegate() == null) {
			resp.setStatus(errorStatus(NLS.bind("Server {0} not found.", handle.getId())));
			resp.setAttributes(new CreateServerAttributesUtility().toPojo());
			return resp;
		}
		
		resp.setStatus(StatusConverter.convert(com.github.cabutchei.rsp.eclipse.core.runtime.Status.OK_STATUS));
		resp.setAttributes(server.getDelegate().listDeploymentOptions());
		return resp;
	}
	
	public CompletableFuture<Status> addDeployable(ServerDeployableReference request) {
		return createCompletableFuture(() -> addDeployableSync(request));
	}

	public Status addDeployableSync(ServerDeployableReference req) {
		if( req == null || req.getServer() == null || req.getDeployableReference() == null) {
			return errorStatus("Invalid request; Expected fields not present.", null);
		}
		String serverId = req.getServer().getId();
		IServer server = managementModel.getServerModel().getServer(serverId);
		if( server == null ) {
			return errorStatus( "Server " + serverId + " not found.");
		}
		IStatus stat = managementModel.getServerModel().addDeployable(server, req.getDeployableReference());
		if (stat != null && stat.isOK()) {
			invalidateDeployableResourceCache(req.getServer());
		}
		return StatusConverter.convert(stat);
	}
	
	public CompletableFuture<Status> removeDeployable(ServerDeployableReference request) {
		return createCompletableFuture(() -> removeDeployableSync(request));
	}

	public Status removeDeployableSync(ServerDeployableReference reference) {
		if( reference == null || reference.getServer() == null || reference.getDeployableReference() == null) {
			return errorStatus("Invalid request; Expected fields not present.", null);
		}
		String serverId = reference.getServer().getId();
		IServer server = managementModel.getServerModel().getServer(serverId);
		if( server == null ) {
			return errorStatus( "Server " + serverId + " not found.");
		}

		IStatus stat = managementModel.getServerModel().removeDeployable(server, reference.getDeployableReference());
		if (stat != null && stat.isOK()) {
			invalidateDeployableResourceCache(reference.getServer());
		}
		return StatusConverter.convert(stat);
	}

	@Override
	public CompletableFuture<Status> publish(PublishServerRequest request) {
		return createCompletableFuture(() -> publishSync(request));
	}

	private Status checkPublishRequestError(PublishServerRequest request) {
		if( request == null || request.getServer() == null 
				|| request.getServer().getId() == null) {
			return errorStatus("Invalid request; Expected fields not present.", null);
		}
		IServer server = managementModel.getServerModel().getServer(request.getServer().getId());
		if( server == null ) {
			return errorStatus("Server not found: " + request.getServer().getId(), null);
		}
		return null;
	}
	private Status publishSync(PublishServerRequest request) {
		Status stat = checkPublishRequestError(request);
		if( stat != null )
			return stat;
		try {
			IServer server = managementModel.getServerModel().getServer(request.getServer().getId());
			IStatus stat2 = managementModel.getServerModel().publish(server, request.getKind());
			return StatusConverter.convert(stat2);
		} catch(CoreException ce) {
			return StatusConverter.convert(ce.getStatus());
		}
	}

	@Override
	public CompletableFuture<Status> publishAsync(PublishServerRequest request) {
		return createCompletableFuture(() -> publishAsyncInternal(request));
	}

	private Status publishAsyncInternal(PublishServerRequest request) {
		Status stat = checkPublishRequestError(request);
		if( stat != null )
			return stat;
		try {
			IServer server = managementModel.getServerModel().getServer(request.getServer().getId());
			IStatus stat2 = managementModel.getServerModel().publishAsync(server, request.getKind());
			return StatusConverter.convert(stat2);
		} catch(CoreException ce) {
			return StatusConverter.convert(ce.getStatus());
		}
	}

	@Override
	public CompletableFuture<ListDownloadRuntimeResponse> listDownloadableRuntimes() {
		return createCompletableFuture(() -> listDownloadableRuntimesInternal());
	}

	private ListDownloadRuntimeResponse listDownloadableRuntimesInternal() {
		Map<String, DownloadRuntime> map = managementModel.getDownloadRuntimeModel().getOrLoadDownloadRuntimes(new NullProgressMonitor());
		AlphanumComparator comp = new AlphanumComparator();
		Comparator<DownloadRuntimeDescription> alphanumComp = (drd1,drd2) -> comp.compare(drd1.getName(), drd2.getName());
		List<DownloadRuntimeDescription> list = map.values().stream()
				.sorted(alphanumComp)
				.map(dlrt -> dlrt.toDao())
				.collect(Collectors.toList());
		ListDownloadRuntimeResponse resp = new ListDownloadRuntimeResponse();
		resp.setRuntimes(list);
		return resp;
	}

	@Override
	public CompletableFuture<WorkflowResponse> downloadRuntime(DownloadSingleRuntimeRequest req) {
		return createCompletableFuture(() -> downloadRuntimeInternal(req));
	}

	private WorkflowResponse downloadRuntimeInternal(DownloadSingleRuntimeRequest req) {
		if (req == null) {
			WorkflowResponse resp = errorWorkflowResponse(errorStatus("Invalid Request: Request cannot be null."));
			resp.setItems(new ArrayList<>());
			return resp;
		}
		String id = req.getDownloadRuntimeId();
		IDownloadRuntimesProvider provider = managementModel.getDownloadRuntimeModel().findProviderForRuntime(id);
		if( provider != null ) {
			DownloadRuntime dlrt = managementModel.getDownloadRuntimeModel().findDownloadRuntime(id, new NullProgressMonitor());
			IDownloadRuntimeRunner executor = provider.getDownloadRunner(dlrt);
			if( executor != null ) {
				WorkflowResponse response = executor.execute(req);
				return response;
			}
		}
		WorkflowResponse error = new WorkflowResponse();
		Status s = errorStatus("Unable to find an executor for the given download runtime", null);
		error.setStatus(s);
		error.setItems(new ArrayList<>());
		return error;
	}
	

	@Override
	public CompletableFuture<WorkflowResponse> createServerWorkflow(CreateServerWorkflowRequest req) {
		return createCompletableFuture(() -> createServerWorkflowInternal(req));
	}

	private WorkflowResponse createServerWorkflowInternal(CreateServerWorkflowRequest req) {
		if (req == null) {
			WorkflowResponse resp = errorWorkflowResponse(errorStatus("Invalid Request: Request cannot be null."));
			resp.setItems(new ArrayList<>());
			return resp;
		}
		String serverTypeId = req.getServerTypeId();
		if (serverTypeId == null) {
			WorkflowResponse resp = errorWorkflowResponse(errorStatus("Invalid Request: serverTypeId cannot be null."));
			resp.setItems(new ArrayList<>());
			return resp;
		}
		IServerType type = managementModel.getServerModel().getIServerType(serverTypeId);
		try {
			return type.createServerWorkflow(this, req);
		} catch(RuntimeException re) {
			Status status = errorStatus("Error executing actions: " + re.getMessage(), re);
			return errorWorkflowResponse(status, req.getRequestId());
		}
	}

	@Override
	public CompletableFuture<List<JobProgress>> getJobs() {
		return createCompletableFuture(() -> getJobsSync());
	}
	
	protected List<JobProgress> getJobsSync() {
		List<IJob> jobs = managementModel.getJobManager().getJobs();
		List<JobProgress> ret = new ArrayList<>();
		JobProgress jp = null;
		for( IJob i : jobs ) {
			jp = new JobProgress(new JobHandle(i.getName(), i.getId()), i.getProgress());
			ret.add(jp);
		}
		return ret;
	}

	@Override
	public CompletableFuture<Status> cancelJob(JobHandle job) {
		return createCompletableFuture(() -> cancelJobSync(job));
	}
	
	protected Status cancelJobSync(JobHandle job) {
		if (job == null) {
			return errorStatus("Job handle cannot be null");
		}
		IStatus s =  managementModel.getJobManager().cancelJob(job);
		return StatusConverter.convert(s);
	}

	
	/*
	 * Server actions
	 * 
	 * (non-Javadoc)
	 * @see com.github.cabutchei.rsp.api.RSPServer#listServerActions()
	 */
	@Override
	public CompletableFuture<ListServerActionResponse> listServerActions(ServerHandle handle) {
		return createCompletableFuture(() -> listServerActionsSync(handle));
	}
	private ListServerActionResponse listServerActionsSync(ServerHandle handle) {
		ListServerActionResponse resp = new ListServerActionResponse();
		Status s = verifyServerAndDelegate(handle);
		if( s != null && !s.isOK()) {
			resp.setStatus(s);
			return resp;
		}
		IServer server = managementModel.getServerModel().getServer(handle.getId());
		try {
			return server.getDelegate().listServerActions();
		} catch(RuntimeException re) {
			Status err = errorStatus("Error loading actions: " + re.getMessage(), re);
			resp.setStatus(err);
			return resp;
		}
	}

	@Override
	public CompletableFuture<WorkflowResponse> executeServerAction(ServerActionRequest req) {
		return createCompletableFuture(() -> executeServerActionSync(req));
	}
	
	private WorkflowResponse executeServerActionSync(ServerActionRequest req) {
		if (req == null) {
			return errorWorkflowResponse(errorStatus("Invalid Request: Request cannot be null."));
		}
		String serverId = req.getServerId();
		Status s = verifyServerAndDelegate(serverId);
		if( s != null && !s.isOK()) {
			return errorWorkflowResponse(s);
		}
		IServer server = managementModel.getServerModel().getServer(serverId);
		IServerDelegate del = server.getDelegate();
		try {
			return del.executeServerAction(req);
		} catch(RuntimeException re) {
			Status status = errorStatus("Error executing actions: " + re.getMessage(), re);
			return errorWorkflowResponse(status, req.getRequestId());
		}
	}
	
	private WorkflowResponse errorWorkflowResponse(Status s) {
		return errorWorkflowResponse(s, 0);
	}
	
	private WorkflowResponse errorWorkflowResponse(Status s, long requestId) {
		WorkflowResponse err = new WorkflowResponse();
		err.setRequestId(requestId);
		err.setStatus(s);
		return err;
	}

	private Status verifyServerAndDelegate(ServerHandle handle) {
		if( handle == null ) { 
			return errorStatus("Invalid Request: Request must include server handle.");
		}
		if( handle.getType() == null ) { 
			return errorStatus("Invalid Request: Request must include server type.");
		}
		if( handle.getType().getId() == null ) { 
			return errorStatus("Invalid Request: Request must include server type id.");
		}
		if( managementModel.getServerModel().getIServerType(
				handle.getType().getId()) == null ) {
			return errorStatus("Invalid Request: Server type not found.");
		}
		return verifyServerAndDelegate(handle.getId());
	}
	
	private Status verifyServerAndDelegate(String id) {
		if( id == null ) { 
			return errorStatus("Invalid Request: Request must include server id.");
		}
		IServer server = managementModel.getServerModel().getServer(id);
		if( server == null ) {
			return errorStatus(NLS.bind(ServerStringConstants.SERVER_DNE, id), null);
		}
		if( server.getDelegate() == null ) {
			return errorStatus(NLS.bind(ServerStringConstants.UNEXPECTED_ERROR_DELEGATE, id), null);
		}
		return null;
	}
	private Status errorStatus(String msg) {
		return errorStatus(msg, null);
	}
	private Status errorStatus(String msg, Throwable t) {
		IStatus is = new com.github.cabutchei.rsp.eclipse.core.runtime.Status(IStatus.ERROR, 
				ServerCoreActivator.BUNDLE_ID, 
				msg, t);
		return StatusConverter.convert(is);
	}

	private static final Executor OSGI_EXECUTOR = command -> getOsgiExecutor().execute(() -> {
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		ClassLoader osgi = OsgiClassLoaderHolder.get();
		if (osgi != null) {
			thread.setContextClassLoader(osgi);
		}
		try {
			command.run();
		} finally {
			thread.setContextClassLoader(previous);
		}
	});

	private static ExecutorService getOsgiExecutor() {
		ExecutorService existing = osgiExecutor;
		if (existing != null && !existing.isShutdown()) {
			return existing;
		}
		synchronized (ASYNC_EXECUTOR_LOCK) {
			existing = osgiExecutor;
			if (existing == null || existing.isShutdown()) {
				ThreadFactory threadFactory = new ThreadFactory() {
					private final AtomicInteger nextId = new AtomicInteger(1);

					@Override
					public Thread newThread(Runnable runnable) {
						Thread thread = new Thread(runnable, "rsp-server-async-" + nextId.getAndIncrement());
						thread.setDaemon(true);
						return thread;
					}
				};
				osgiExecutor = new ThreadPoolExecutor(
						ASYNC_THREAD_COUNT,
						ASYNC_THREAD_COUNT,
						30L,
						TimeUnit.SECONDS,
						new LinkedBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
						threadFactory,
						new ThreadPoolExecutor.CallerRunsPolicy());
			}
			return osgiExecutor;
		}
	}

	static void shutdownAsyncExecutor() {
		ExecutorService toShutdown;
		synchronized (ASYNC_EXECUTOR_LOCK) {
			toShutdown = osgiExecutor;
			osgiExecutor = null;
		}
		if (toShutdown != null) {
			toShutdown.shutdown();
			try {
				if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
					toShutdown.shutdownNow();
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				toShutdown.shutdownNow();
			}
		}
	}

	private static <T> CompletableFuture<T> createCompletableFuture(Supplier<T> supplier) {
		final RSPWTPClient rspc = ClientThreadLocal.getActiveClient();
		try {
			return CompletableFuture.supplyAsync(() -> {
				ClientThreadLocal.setActiveClient(rspc);
				try {
					return supplier.get();
				} finally {
					ClientThreadLocal.setActiveClient(null);
				}
			}, OSGI_EXECUTOR);
		} catch (RejectedExecutionException ree) {
			CompletableFuture<T> failed = new CompletableFuture<>();
			failed.completeExceptionally(ree);
			return failed;
		}
	}

}
