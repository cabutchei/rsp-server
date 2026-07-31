package com.github.cabutchei.rsp.eclipse.workspace;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cabutchei.rsp.api.dao.DeployableReference;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.MultiStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.spi.filewatcher.FileWatcherEvent;
import com.github.cabutchei.rsp.server.spi.filewatcher.IFileWatcherService;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectImporter;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceService;
import com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject;

public class ProjectsManager implements IProjectsManager {
	private static final Logger LOG = LoggerFactory.getLogger(ProjectsManager.class);
	private static final String BUNDLE_ID = "com.github.cabutchei.rsp.workspace.eclipse";
	private static final String PROJECT_FILE = ".project";
	private static final String WEB_APP_LIBRARIES_CONTAINER_ID = "org.eclipse.jst.j2ee.internal.web.container";
	private static final String WTP_MODULE_CONTAINER_ID = "org.eclipse.jst.j2ee.internal.module.container";
	private static final String WTP_VALIDATION_BUILDER_ID = "org.eclipse.wst.validation.validationbuilder";
	private static final int FILE_CHANGE_CREATED = 1;
	private static final int FILE_CHANGE_CHANGED = 2;
	private static final int FILE_CHANGE_DELETED = 3;
	private static final int RELEVANT_CHANGE_FLAGS =
			IResourceDelta.CONTENT | IResourceDelta.REPLACED | IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO;
	private static final List<String> DEFAULT_WATCH_PATTERNS = Collections.emptyList();

	private final IWorkspaceService workspaceService;
	private final IWTPService wtpService;
	private final IFileWatcherService fileWatcherService;
	private final List<IProjectImporter> projectImporters;
	private final Set<String> dynamicWatchPatterns = new LinkedHashSet<>();
	private final Set<Path> workspaceRoots = new LinkedHashSet<>();
	private final IResourceChangeListener workspaceChangeListener;
	private boolean initialized;

	public ProjectsManager(IWorkspaceService workspaceService, List<IProjectImporter> projectImporters) {
		this(workspaceService, null, null, projectImporters);
	}

	public ProjectsManager(IWorkspaceService workspaceService, IWTPService wtpService, List<IProjectImporter> projectImporters) {
		this(workspaceService, wtpService, null, projectImporters);
	}

	public ProjectsManager(IWorkspaceService workspaceService, IWTPService wtpService,
			IFileWatcherService fileWatcherService, List<IProjectImporter> projectImporters) {
		this.workspaceService = workspaceService;
		this.wtpService = wtpService;
		this.fileWatcherService = fileWatcherService;
		this.projectImporters = projectImporters == null ? Collections.emptyList() : new ArrayList<>(projectImporters);
		this.workspaceChangeListener = this::handleWorkspaceResourceChangeEvent;
		registerWorkspaceChangeListener();
	}

	private IProject getProject(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return null;
		}
		IWorkspaceRoot root = getWorkspaceRoot();
		return root == null ? null : root.getProject(projectName);
	}

	@Override
	public IStatus importProject(Path projectRoot) {
		if (projectRoot == null) {
			return errorStatus("Project root cannot be null", null);
		}
		IWorkspace workspace = getWorkspace();
		if (workspace == null) {
			return errorStatus("Workspace is not available", null);
		}
		IPath projectDescriptionPath = new org.eclipse.core.runtime.Path(projectRoot.resolve(PROJECT_FILE).toString());
		try {
			IProjectDescription description = workspace.loadProjectDescription(projectDescriptionPath);
			IProject project = workspace.getRoot().getProject(description.getName());
			NullProgressMonitor monitor = new NullProgressMonitor();
			if (!project.exists()) {
				project.create(description, monitor);
			}
			if (!project.isOpen()) {
				project.open(monitor);
			}
			return Status.OK_STATUS;
		} catch (CoreException ce) {
			return errorStatus("Failed to import project at " + projectRoot, ce);
		}
	}

	@Override
	public IStatus importAllWorkspaceProjects() {
		return importProjects(discoverProjectsUnderRoots(getWorkspaceRootsSnapshot()));
	}

	@Override
	public IStatus importProjects(List<Path> projectRoots) {
		if (projectRoots == null) {
			return errorStatus("Project roots cannot be null", null);
		}
		if (projectRoots.isEmpty()) {
			return Status.OK_STATUS;
		}
		List<IStatus> failures = new ArrayList<>();
		for (Path projectRoot : projectRoots) {
			if (projectRoot == null) {
				failures.add(errorStatus("Project root cannot be null", null));
				continue;
			}
			IStatus status = importProject(projectRoot);
			if (!status.isOK()) {
				failures.add(status);
			}
		}
		return aggregateImportResults(failures);
	}

	@Override
	public IStatus importProjects(Path[] projectRoots) {
		if (projectRoots == null) {
			return errorStatus("Project roots cannot be null", null);
		}
		return importProjects(Arrays.asList(projectRoots));
	}

	@Override
	public IStatus refreshProject(String projectName) {
		if (projectName == null || projectName.isEmpty()) {
			return errorStatus("Project name cannot be null or empty", null);
		}
		IProject project = getProject(projectName);
		if (project == null || !project.exists()) {
			return errorStatus("Project " + projectName + " does not exist", null);
		}
		try {
			NullProgressMonitor monitor = new NullProgressMonitor();
			if (!project.isOpen()) {
				project.open(monitor);
			}
			project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
			return Status.OK_STATUS;
		} catch (CoreException ce) {
			return errorStatus("Failed to refresh project " + projectName, ce);
		}
	}

	@Override
	public IStatus setAutoBuilding(boolean enabled) {
		IWorkspace workspace = getWorkspace();
		if (workspace == null) {
			return Status.OK_STATUS;
		}
		try {
			IWorkspaceDescription description = workspace.getDescription();
			boolean changed = description.isAutoBuilding() != enabled;
			if (changed) {
				description.setAutoBuilding(enabled);
				workspace.setDescription(description);
			}
			return Status.OK_STATUS;
		} catch (CoreException ce) {
			return errorStatus("Failed to set workspace auto-building", ce);
		}
	}

	@Override
	public void initializeProjects(Collection<Path> workspaceRoots) {
		Collection<Path> normalizedRoots = normalizeRoots(workspaceRoots);
		Collection<Path> previousRoots = getWorkspaceRootsSnapshot();
		synchronized (this.workspaceRoots) {
			this.workspaceRoots.clear();
			this.workspaceRoots.addAll(normalizedRoots);
		}
		if (wtpService != null) {
			wtpService.setWorkspaceRoots(normalizedRoots);
		}
		removeProjects(diff(previousRoots, normalizedRoots));
		initialized = true;
		IStatus importStatus = importProjects(discoverProjectsUnderRoots(normalizedRoots));
		if (!importStatus.isOK()) {
			LOG.warn("Workspace import reported issues during initialization: {}", importStatus.getMessage());
		}
		sanitizeWorkspaceProjects();
		notifyImportersInit(normalizedRoots);
	}

	@Override
	public void updateWorkspaceFolders(Collection<Path> added, Collection<Path> removed) {
		Collection<Path> addedRoots = normalizeRoots(added);
		Collection<Path> removedRoots = normalizeRoots(removed);
		synchronized (workspaceRoots) {
			workspaceRoots.removeAll(removedRoots);
			workspaceRoots.addAll(addedRoots);
		}
		if (wtpService != null) {
			wtpService.setWorkspaceRoots(getWorkspaceRootsSnapshot());
		}
		removeProjects(removedRoots);
		IStatus importStatus = importProjects(discoverProjectsUnderRoots(addedRoots));
		if (!importStatus.isOK()) {
			LOG.warn("Workspace import reported issues during workspace-folder update: {}", importStatus.getMessage());
		}
		sanitizeWorkspaceProjects();
		notifyImportersUpdate(addedRoots, removedRoots);
	}

	@Override
	public List<WorkspaceProject> listWorkspaceProjects() {
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null) {
			return Collections.emptyList();
		}
		IProject[] projects = root.getProjects();
		List<WorkspaceProject> result = new ArrayList<>(projects.length);
		for (IProject project : projects) {
			IPath location = project.getLocation();
			Path path = location == null ? null : location.toFile().toPath();
			result.add(new WorkspaceProject(project.getName(), path, project.isOpen()));
		}
		return Collections.unmodifiableList(result);
	}

	@Override
	public List<String> getWatchPatterns() {
		List<String> watchPatterns = new ArrayList<>(DEFAULT_WATCH_PATTERNS);
		synchronized (dynamicWatchPatterns) {
			watchPatterns.addAll(dynamicWatchPatterns);
		}
		return Collections.unmodifiableList(watchPatterns);
	}

	@Override
	public void syncDeployableWatchPatterns(Collection<DeployableReference> deployables) {
		LinkedHashSet<String> nextPatterns = new LinkedHashSet<>();
		if (deployables != null) {
			for (DeployableReference deployable : deployables) {
				if (deployable == null || deployable.getPath() == null || deployable.getPath().isBlank()) {
					continue;
				}
				Path deployablePath = Path.of(deployable.getPath()).toAbsolutePath().normalize();
				Set<Path> watchRoots = wtpService == null
						? Collections.singleton(deployablePath)
						: wtpService.getDeploymentWatchPaths(deployablePath, null);
				for (Path watchRoot : watchRoots) {
					if (isJdtManagedOutputPath(watchRoot)) {
						continue;
					}
					String pattern = toWatchPattern(watchRoot);
					if (pattern != null && !pattern.isBlank()) {
						nextPatterns.add(pattern);
					}
				}
			}
		}
		synchronized (dynamicWatchPatterns) {
			dynamicWatchPatterns.clear();
			dynamicWatchPatterns.addAll(nextPatterns);
		}
	}

	@Override
	public IStatus fileChanged(Path path, int changeType) {
		if (path == null) {
			return errorStatus("Changed path cannot be null", null);
		}
		if (wtpService != null) {
			wtpService.invalidateDeployableResourceCache();
		}
		Path normalized = path.toAbsolutePath().normalize();
		if (PROJECT_FILE.equals(normalized.getFileName() == null ? null : normalized.getFileName().toString())
				&& (changeType == FILE_CHANGE_CREATED || changeType == FILE_CHANGE_CHANGED)) {
			return importAllWorkspaceProjects();
		}
		IStatus refreshStatus = refreshAffectedResource(normalized);
		if (refreshStatus != null) {
			return refreshStatus;
		}
		return Status.OK_STATUS;
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public void dispose() {
		IWorkspace workspace = getWorkspace();
		if (workspace != null) {
			workspace.removeResourceChangeListener(workspaceChangeListener);
		}
	}

	@Override
	public IWTPService getWTPService() {
		return wtpService;
	}

	private IWorkspace getWorkspace() {
		IWorkspace workspace = workspaceService == null ? null : workspaceService.getWorkspace();
		return workspace == null ? ResourcesPlugin.getWorkspace() : workspace;
	}

	private IWorkspaceRoot getWorkspaceRoot() {
		IWorkspace workspace = getWorkspace();
		return workspace == null ? null : workspace.getRoot();
	}

	private Collection<Path> getWorkspaceRootsSnapshot() {
		synchronized (workspaceRoots) {
			return new ArrayList<>(workspaceRoots);
		}
	}

	private void registerWorkspaceChangeListener() {
		IWorkspace workspace = getWorkspace();
		if (workspace != null) {
			workspace.addResourceChangeListener(workspaceChangeListener, IResourceChangeEvent.POST_CHANGE);
		}
	}

	private void handleWorkspaceResourceChangeEvent(IResourceChangeEvent event) {
		if (event == null || event.getDelta() == null) {
			return;
		}
		try {
			event.getDelta().accept(delta -> {
				handleWorkspaceResourceDelta(delta);
				return true;
			});
		} catch (CoreException ce) {
			LOG.warn("Failed to process workspace resource change event", ce);
		}
	}

	private void handleWorkspaceResourceDelta(IResourceDelta delta) {
		if (delta == null) {
			return;
		}
		int changeType = toWorkspaceChangeType(delta);
		if (changeType == 0) {
			return;
		}
		Path changedPath = toFilesystemPath(delta.getResource());
		if (changedPath == null) {
			return;
		}
		Path normalized = changedPath.toAbsolutePath().normalize();
		if (!isContainedInAny(normalized, getWorkspaceRootsSnapshot())) {
			return;
		}
		handleWorkspaceManagedPathChange(normalized, changeType);
		fireWorkspaceFileWatcherEvent(normalized, changeType);
	}

	private Collection<Path> normalizeRoots(Collection<Path> roots) {
		if (roots == null || roots.isEmpty()) {
			return Collections.emptyList();
		}
		List<Path> normalized = new ArrayList<>();
		for (Path root : roots) {
			if (root == null) {
				continue;
			}
			normalized.add(root.toAbsolutePath().normalize());
		}
		return normalized;
	}

	private void notifyImportersInit(Collection<Path> roots) {
		if (roots.isEmpty() || projectImporters.isEmpty()) {
			return;
		}
		for (IProjectImporter importer : projectImporters) {
			if (importer != null) {
				importer.initializeProjects(roots);
			}
		}
	}

	private void notifyImportersUpdate(Collection<Path> added, Collection<Path> removed) {
		if ((added == null || added.isEmpty()) && (removed == null || removed.isEmpty())) {
			return;
		}
		if (projectImporters.isEmpty()) {
			return;
		}
		for (IProjectImporter importer : projectImporters) {
			if (importer != null) {
				importer.updateWorkspaceFolders(added, removed);
			}
		}
	}

	private List<Path> discoverProjectsUnderRoots(Collection<Path> roots) {
		if (roots == null || roots.isEmpty()) {
			return Collections.emptyList();
		}
		List<Path> results = new ArrayList<>();
		for (Path root : roots) {
			results.addAll(findProjectsInRoot(root));
		}
		return results;
	}

	private List<Path> findProjectsInRoot(Path root) {
		if (root == null || !Files.isDirectory(root)) {
			return Collections.emptyList();
		}
		List<Path> results = new ArrayList<>();
		Path projectFile = root.resolve(PROJECT_FILE);
		if (Files.isRegularFile(projectFile)) {
			results.add(root);
			return results;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
			for (Path child : stream) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				if (Files.isRegularFile(child.resolve(PROJECT_FILE))) {
					results.add(child);
				}
			}
		} catch (IOException ioe) {
			LOG.warn("Failed to scan workspace root {}", root, ioe);
		}
		return results;
	}

	private void removeProjects(Collection<Path> removedRoots) {
		if (removedRoots == null || removedRoots.isEmpty()) {
			return;
		}
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null) {
			return;
		}
		IProject[] projects = root.getProjects();
		NullProgressMonitor monitor = new NullProgressMonitor();
		for (IProject project : projects) {
			IPath location = project.getLocation();
			Path projectPath = location == null ? null : location.toFile().toPath();
			if (projectPath == null) {
				continue;
			}
			Path normalized = projectPath.toAbsolutePath().normalize();
			if (!isContainedInAny(normalized, removedRoots)) {
				continue;
			}
			try {
				project.delete(false, true, monitor);
			} catch (CoreException ce) {
				LOG.warn("Failed to remove project {} from workspace", project.getName(), ce);
			}
		}
	}

	private boolean isContainedInAny(Path candidate, Collection<Path> roots) {
		for (Path root : roots) {
			if (root != null && candidate.startsWith(root)) {
				return true;
			}
		}
		return false;
	}

	private Collection<Path> diff(Collection<Path> source, Collection<Path> target) {
		if (source == null || source.isEmpty()) {
			return Collections.emptyList();
		}
		Set<Path> targetSet = target == null ? Collections.emptySet() : new HashSet<>(target);
		List<Path> diff = new ArrayList<>();
		for (Path sourcePath : source) {
			if (sourcePath != null && !targetSet.contains(sourcePath)) {
				diff.add(sourcePath);
			}
		}
		return diff;
	}

	private IStatus refreshAffectedResource(Path normalizedPath) {
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null || normalizedPath == null) {
			return Status.OK_STATUS;
		}
		List<IResource> matchingResources = findResourcesForPath(root, normalizedPath);
		if (!matchingResources.isEmpty()) {
			return refreshResources(matchingResources, IResource.DEPTH_ZERO, normalizedPath);
		}
		IContainer container = findNearestExistingContainer(root, normalizedPath);
		if (container == null) {
			return null;
		}
		return refreshResource(container, IResource.DEPTH_ONE, normalizedPath);
	}

	private List<IResource> findResourcesForPath(IWorkspaceRoot root, Path normalizedPath) {
		if (root == null || normalizedPath == null) {
			return Collections.emptyList();
		}
		Set<IResource> resources = new LinkedHashSet<>();
		org.eclipse.core.runtime.Path eclipsePath = new org.eclipse.core.runtime.Path(normalizedPath.toString());
		IFile file = root.getFileForLocation(eclipsePath);
		if (file != null) {
			resources.add(file);
		}
		IContainer container = root.getContainerForLocation(eclipsePath);
		if (container != null) {
			resources.add(container);
		}
		IFile[] files = root.findFilesForLocationURI(normalizedPath.toUri());
		if (files != null) {
			resources.addAll(Arrays.asList(files));
		}
		IContainer[] containers = root.findContainersForLocationURI(normalizedPath.toUri());
		if (containers != null) {
			resources.addAll(Arrays.asList(containers));
		}
		return new ArrayList<>(resources);
	}

	private IContainer findNearestExistingContainer(IWorkspaceRoot root, Path normalizedPath) {
		Path current = normalizedPath.getParent();
		while (current != null) {
			List<IResource> resources = findResourcesForPath(root, current);
			for (IResource resource : resources) {
				if (resource instanceof IContainer) {
					return (IContainer) resource;
				}
			}
			current = current.getParent();
		}
		return null;
	}

	private IStatus refreshResources(Collection<? extends IResource> resources, int depth, Path changedPath) {
		if (resources == null || resources.isEmpty()) {
			return Status.OK_STATUS;
		}
		List<IStatus> failures = new ArrayList<>();
		for (IResource resource : resources) {
			IStatus status = refreshResource(resource, depth, changedPath);
			if (status != null && !status.isOK()) {
				failures.add(status);
			}
		}
		return aggregateImportResults(failures);
	}

	private IStatus refreshResource(IResource resource, int depth, Path changedPath) {
		if (resource == null) {
			return Status.OK_STATUS;
		}
		try {
			resource.refreshLocal(depth, new NullProgressMonitor());
			return Status.OK_STATUS;
		} catch (CoreException ce) {
			return errorStatus("Failed to refresh workspace resource for " + changedPath, ce);
		}
	}

	private IStatus errorStatus(String message, Throwable t) {
		return new Status(IStatus.ERROR, BUNDLE_ID, message, t);
	}

	private IStatus aggregateImportResults(List<IStatus> failures) {
		if (failures.isEmpty()) {
			return Status.OK_STATUS;
		}
		return new MultiStatus(BUNDLE_ID, IStatus.ERROR,
				failures.toArray(new IStatus[0]), "One or more projects failed to import", null);
	}

	private void handleWorkspaceManagedPathChange(Path normalizedPath, int changeType) {
		if (wtpService != null) {
			wtpService.invalidateDeployableResourceCache();
		}
		if (PROJECT_FILE.equals(normalizedPath.getFileName() == null ? null : normalizedPath.getFileName().toString())
				&& (changeType == FILE_CHANGE_CREATED || changeType == FILE_CHANGE_CHANGED)) {
			IStatus importStatus = importAllWorkspaceProjects();
			if (!importStatus.isOK()) {
				LOG.warn("Workspace import reported issues after resource change: {}", importStatus.getMessage());
			}
		}
	}

	private void fireWorkspaceFileWatcherEvent(Path path, int changeType) {
		if (fileWatcherService == null || path == null) {
			return;
		}
		WatchEvent.Kind<?> kind = toWatchEventKind(changeType);
		if (kind != null) {
			fileWatcherService.fireFileWatcherEvent(new FileWatcherEvent(path, kind));
		}
	}

	private int toWorkspaceChangeType(IResourceDelta delta) {
		if (delta == null) {
			return 0;
		}
		switch (delta.getKind()) {
		case IResourceDelta.ADDED:
			return FILE_CHANGE_CREATED;
		case IResourceDelta.REMOVED:
			return FILE_CHANGE_DELETED;
		case IResourceDelta.CHANGED:
			return (delta.getFlags() & RELEVANT_CHANGE_FLAGS) == 0 ? 0 : FILE_CHANGE_CHANGED;
		default:
			return 0;
		}
	}

	private WatchEvent.Kind<?> toWatchEventKind(int changeType) {
		switch (changeType) {
		case FILE_CHANGE_CREATED:
			return StandardWatchEventKinds.ENTRY_CREATE;
		case FILE_CHANGE_DELETED:
			return StandardWatchEventKinds.ENTRY_DELETE;
		case FILE_CHANGE_CHANGED:
			return StandardWatchEventKinds.ENTRY_MODIFY;
		default:
			return null;
		}
	}

	private Path toFilesystemPath(IResource resource) {
		if (resource == null) {
			return null;
		}
		URI locationUri = resource.getRawLocationURI();
		if (locationUri == null) {
			locationUri = resource.getLocationURI();
		}
		if (locationUri != null) {
			try {
				return Path.of(locationUri).toAbsolutePath().normalize();
			} catch (Exception e) {
				return null;
			}
		}
		IPath location = resource.getLocation();
		return location == null ? null : location.toFile().toPath().toAbsolutePath().normalize();
	}

	private boolean isJdtManagedOutputPath(Path path) {
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null || path == null) {
			return false;
		}
		Path normalized = path.toAbsolutePath().normalize();
		for (IProject project : root.getProjects()) {
			if (project == null || !project.exists() || !project.isAccessible()) {
				continue;
			}
			try {
				if (!project.hasNature(JavaCore.NATURE_ID)) {
					continue;
				}
				IJavaProject javaProject = JavaCore.create(project);
				if (javaProject == null || !javaProject.exists()) {
					continue;
				}
				if (isWithinWorkspacePath(normalized, javaProject.getOutputLocation())) {
					return true;
				}
				for (IClasspathEntry entry : javaProject.getRawClasspath()) {
					if (entry == null || entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
						continue;
					}
					if (isWithinWorkspacePath(normalized, entry.getOutputLocation())) {
						return true;
					}
				}
			} catch (CoreException e) {
				LOG.debug("Failed to inspect Java output paths for project {}", project.getName(), e);
			}
		}
		return false;
	}

	private boolean isWithinWorkspacePath(Path candidate, IPath workspacePath) {
		Path filesystemPath = toFilesystemPath(workspacePath);
		return filesystemPath != null && candidate.startsWith(filesystemPath);
	}

	private String toWatchPattern(Path path) {
		if (path == null) {
			return null;
		}
		Path normalized = path.toAbsolutePath().normalize();
		String unixPath = normalized.toString().replace('\\', '/');
		if (Files.exists(normalized)) {
			return Files.isDirectory(normalized) ? unixPath + "/**" : unixPath;
		}
		String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
		return fileName.contains(".") ? unixPath : unixPath + "/**";
	}

	private Path toFilesystemPath(IPath workspacePath) {
		if (workspacePath == null) {
			return null;
		}
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null) {
			return null;
		}
		IResource resource = root.findMember(workspacePath);
		if (resource != null) {
			return toFilesystemPath(resource);
		}
		if (workspacePath.segmentCount() == 0) {
			return null;
		}
		IProject project = root.getProject(workspacePath.segment(0));
		if (project == null || !project.exists()) {
			return null;
		}
		IPath projectLocation = project.getLocation();
		if (projectLocation == null) {
			return null;
		}
		Path resolved = projectLocation.toFile().toPath().toAbsolutePath().normalize();
		for (int i = 1; i < workspacePath.segmentCount(); i++) {
			resolved = resolved.resolve(workspacePath.segment(i));
		}
		return resolved.normalize();
	}

	private void disableWtpValidation(IProject project) throws CoreException {
		if (project == null || !project.exists()) {
			return;
		}
		IProjectDescription description = project.getDescription();
		IProjectDescription sanitized = withoutWtpValidationBuilder(description);
		if (sanitized != description) {
			project.setDescription(sanitized, new NullProgressMonitor());
			LOG.info("Disabled WTP validation builder for project {}", project.getName());
		}
	}

	private void sanitizeWorkspaceProjects() {
		IWorkspaceRoot root = getWorkspaceRoot();
		if (root == null) {
			return;
		}
		for (IProject project : root.getProjects()) {
			if (project == null || !project.exists()) {
				continue;
			}
			try {
				disableWtpValidation(project);
			} catch (CoreException e) {
				LOG.warn("Failed to disable WTP validation builder for project {}", project.getName(), e);
			}
		}
	}

	private IProjectDescription withoutWtpValidationBuilder(IProjectDescription description) {
		if (description == null) {
			return null;
		}
		ICommand[] buildSpec = description.getBuildSpec();
		if (buildSpec == null || buildSpec.length == 0) {
			return description;
		}
		List<ICommand> filtered = new ArrayList<>(buildSpec.length);
		boolean changed = false;
		for (ICommand command : buildSpec) {
			if (command != null && WTP_VALIDATION_BUILDER_ID.equals(command.getBuilderName())) {
				changed = true;
				continue;
			}
			filtered.add(command);
		}
		if (!changed) {
			return description;
		}
		description.setBuildSpec(filtered.toArray(new ICommand[0]));
		return description;
	}
}
