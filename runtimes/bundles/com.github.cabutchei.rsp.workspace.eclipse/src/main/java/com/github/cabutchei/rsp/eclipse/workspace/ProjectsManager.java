package com.github.cabutchei.rsp.eclipse.workspace;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.MultiStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;
import com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerEntry;
import com.github.cabutchei.rsp.server.spi.workspace.ClasspathContainerMapping;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectImporter;
import com.github.cabutchei.rsp.server.spi.workspace.IProjectsManager;
import com.github.cabutchei.rsp.server.spi.workspace.IWTPService;
import com.github.cabutchei.rsp.server.spi.workspace.IWorkspaceService;
import com.github.cabutchei.rsp.server.spi.workspace.JreContainerMapping;
import com.github.cabutchei.rsp.server.spi.workspace.WorkspaceProject;

public class ProjectsManager implements IProjectsManager {
	private static final Logger LOG = LoggerFactory.getLogger(ProjectsManager.class);
	private static final String BUNDLE_ID = "com.github.cabutchei.rsp.workspace.eclipse";
	private static final String PROJECT_FILE = ".project";
	private static final String WEB_APP_LIBRARIES_CONTAINER_ID = "org.eclipse.jst.j2ee.internal.web.container";
	private static final String WTP_MODULE_CONTAINER_ID = "org.eclipse.jst.j2ee.internal.module.container";
	private static final int FILE_CHANGE_CREATED = 1;
	private static final int FILE_CHANGE_CHANGED = 2;
	private static final int FILE_CHANGE_DELETED = 3;
	private static final List<String> DEFAULT_WATCH_PATTERNS = Collections.unmodifiableList(Arrays.asList(
			"**/.project",
			"**/.classpath",
			"**/.settings/org.eclipse.jdt.core.prefs",
			"**/.settings/org.eclipse.wst.common.component",
			"**/pom.xml",
			"**/META-INF/MANIFEST.MF",
			"**/build.properties",
			"**/*.java",
			"**/*.class"));

	private final IWorkspaceService workspaceService;
	private final IWTPService wtpService;
	private final List<IProjectImporter> projectImporters;
	private final List<String> watchPatterns;
	private final List<Runnable> classpathContainerChangeListeners = new CopyOnWriteArrayList<>();
	private final IElementChangedListener javaModelChangeListener = this::onJavaModelChanged;
	private final Set<Path> workspaceRoots = new LinkedHashSet<>();
	private volatile boolean javaModelListenerRegistered;
	private boolean initialized;

	public ProjectsManager(IWorkspaceService workspaceService, List<IProjectImporter> projectImporters) {
		this(workspaceService, null, projectImporters);
	}

	public ProjectsManager(IWorkspaceService workspaceService, IWTPService wtpService, List<IProjectImporter> projectImporters) {
		this.workspaceService = workspaceService;
		this.wtpService = wtpService;
		this.projectImporters = projectImporters == null ? Collections.emptyList() : new ArrayList<>(projectImporters);
		this.watchPatterns = new ArrayList<>(DEFAULT_WATCH_PATTERNS);
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
	public List<JreContainerMapping> listNonStandardJreContainers() {
		List<WorkspaceProject> projects = listWorkspaceProjects();
		if (projects.isEmpty()) {
			return Collections.emptyList();
		}
		Collection<Path> roots = getWorkspaceRootsSnapshot();
		List<JreContainerMapping> mappings = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (WorkspaceProject projectInfo : projects) {
			if (projectInfo == null || !projectInfo.isOpen()) {
				continue;
			}
			IProject project = getProject(projectInfo.getName());
			if (project == null || !project.exists() || !project.isOpen()) {
				continue;
			}
			IPath location = project.getLocation();
			Path projectPath = location == null ? null : location.toFile().toPath().toAbsolutePath().normalize();
			if (!roots.isEmpty() && (projectPath == null || !isContainedInAny(projectPath, roots))) {
				continue;
			}
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null) {
				continue;
			}
			IClasspathEntry[] entries;
			try {
				entries = javaProject.getRawClasspath();
			} catch (JavaModelException e) {
				continue;
			}
			if (entries == null) {
				continue;
			}
			for (IClasspathEntry entry : entries) {
				if (entry == null || entry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
					continue;
				}
				IPath containerPath = entry.getPath();
				if (!isNonStandardJreContainer(containerPath)) {
					continue;
				}
				IVMInstall vm = JavaRuntime.getVMInstall(containerPath);
				if (vm == null || vm.getInstallLocation() == null) {
					continue;
				}
				String projectUri = null;
				URI locationUri = project.getLocationURI();
				if (locationUri != null) {
					projectUri = locationUri.toString();
				}
				Path javaHome = vm.getInstallLocation().toPath();
				String key = (projectUri == null ? project.getName() : projectUri) + "|" + containerPath.toString();
				if (!seen.add(key)) {
					continue;
				}
				mappings.add(new JreContainerMapping(
						project.getName(),
						projectUri,
						containerPath.toString(),
						vm.getName(),
						javaHome));
			}
		}
		return mappings;
	}

	@Override
	public List<String> getWatchPatterns() {
		return Collections.unmodifiableList(new ArrayList<>(watchPatterns));
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
	public List<ClasspathContainerMapping> listClasspathContainers() {
		List<WorkspaceProject> projects = listWorkspaceProjects();
		if (projects.isEmpty()) {
			return Collections.emptyList();
		}
		Collection<Path> roots = getWorkspaceRootsSnapshot();
		List<ClasspathContainerMapping> mappings = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (WorkspaceProject projectInfo : projects) {
			if (projectInfo == null || !projectInfo.isOpen()) {
				continue;
			}
			IProject project = getProject(projectInfo.getName());
			if (project == null || !project.exists() || !project.isOpen()) {
				continue;
			}
			IPath location = project.getLocation();
			Path projectPath = location == null ? null : location.toFile().toPath().toAbsolutePath().normalize();
			if (!roots.isEmpty() && (projectPath == null || !isContainedInAny(projectPath, roots))) {
				continue;
			}
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject == null) {
				continue;
			}
			IClasspathEntry[] rawEntries;
			try {
				rawEntries = javaProject.getRawClasspath();
			} catch (JavaModelException e) {
				continue;
			}
			if (rawEntries == null) {
				continue;
			}
			for (IClasspathEntry rawEntry : rawEntries) {
				if (rawEntry == null || rawEntry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
					continue;
				}
				IPath containerPath = rawEntry.getPath();
				if (isJreContainer(containerPath)) {
					continue;
				}
				IClasspathContainer container;
				try {
					container = JavaCore.getClasspathContainer(containerPath, javaProject);
				} catch (JavaModelException e) {
					continue;
				}
				if (container == null) {
					continue;
				}
				String projectUri = null;
				URI locationUri = project.getLocationURI();
				if (locationUri != null) {
					projectUri = locationUri.toString();
				}
				String key = (projectUri == null ? project.getName() : projectUri) + "|" + containerPath.toString();
				if (!seen.add(key)) {
					continue;
				}
				List<ClasspathContainerEntry> entryMappings = new ArrayList<>();
				IClasspathEntry[] containerEntries = container.getClasspathEntries();
				if (containerEntries != null) {
					for (IClasspathEntry containerEntry : containerEntries) {
						if (containerEntry == null) {
							continue;
						}
						String entryPath = resolveEntryPath(containerEntry);
						String sourcePath = resolveEntrySourcePath(containerEntry);
						String javadoc = extractJavadoc(containerEntry);
						entryMappings.add(new ClasspathContainerEntry(
								containerEntry.getEntryKind(),
								entryPath,
								sourcePath,
								stringValue(containerEntry.getSourceAttachmentRootPath()),
								javadoc,
								containerEntry.isExported()));
					}
				}
				mappings.add(new ClasspathContainerMapping(
						project.getName(),
						projectUri,
						containerPath.toString(),
						container.getDescription(),
						entryMappings));
			}
		}
		return mappings;
	}

	@Override
	public void addClasspathContainersChangedListener(Runnable listener) {
		if (listener == null) {
			return;
		}
		classpathContainerChangeListeners.add(listener);
		registerJavaModelChangeListener();
	}

	@Override
	public void removeClasspathContainersChangedListener(Runnable listener) {
		if (listener == null) {
			return;
		}
		classpathContainerChangeListeners.remove(listener);
		if (classpathContainerChangeListeners.isEmpty()) {
			unregisterJavaModelChangeListener();
		}
	}

	@Override
	public boolean isInitialized() {
		return initialized;
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

	private boolean isNonStandardJreContainer(IPath containerPath) {
		if (containerPath == null || containerPath.segmentCount() < 1) {
			return false;
		}
		String jreContainerId = String.valueOf(JavaRuntime.JRE_CONTAINER);
		if (!jreContainerId.equals(containerPath.segment(0))) {
			return false;
		}
		if (containerPath.segmentCount() < 2) {
			return false;
		}
		String execEnv = JavaRuntime.getExecutionEnvironmentId(containerPath);
		return execEnv == null || execEnv.trim().isEmpty();
	}

	private boolean isJreContainer(IPath containerPath) {
		if (containerPath == null || containerPath.segmentCount() < 1) {
			return false;
		}
		String jreContainerId = String.valueOf(JavaRuntime.JRE_CONTAINER);
		return jreContainerId.equals(containerPath.segment(0));
	}

	private boolean isWtpContainer(IPath containerPath) {
		if (containerPath == null || containerPath.segmentCount() < 1) {
			return false;
		}
		String containerId = containerPath.segment(0);
		return WEB_APP_LIBRARIES_CONTAINER_ID.equals(containerId) || WTP_MODULE_CONTAINER_ID.equals(containerId);
	}

	private void registerJavaModelChangeListener() {
		if (javaModelListenerRegistered) {
			return;
		}
		synchronized (classpathContainerChangeListeners) {
			if (javaModelListenerRegistered) {
				return;
			}
			JavaCore.addElementChangedListener(javaModelChangeListener, ElementChangedEvent.POST_CHANGE);
			javaModelListenerRegistered = true;
		}
	}

	private void unregisterJavaModelChangeListener() {
		if (!javaModelListenerRegistered) {
			return;
		}
		synchronized (classpathContainerChangeListeners) {
			if (!javaModelListenerRegistered || !classpathContainerChangeListeners.isEmpty()) {
				return;
			}
			JavaCore.removeElementChangedListener(javaModelChangeListener);
			javaModelListenerRegistered = false;
		}
	}

	private void onJavaModelChanged(ElementChangedEvent event) {
		if (event == null || classpathContainerChangeListeners.isEmpty()) {
			return;
		}
		if (!containsWtpClasspathContainerChange(event.getDelta())) {
			return;
		}
		notifyClasspathContainerChangeListeners();
	}

	private boolean containsWtpClasspathContainerChange(IJavaElementDelta delta) {
		if (delta == null) {
			return false;
		}
		if (isWtpClasspathContainerChange(delta)) {
			return true;
		}
		IJavaElementDelta[] affectedChildren = delta.getAffectedChildren();
		if (affectedChildren == null) {
			return false;
		}
		for (IJavaElementDelta child : affectedChildren) {
			if (containsWtpClasspathContainerChange(child)) {
				return true;
			}
		}
		return false;
	}

	private boolean isWtpClasspathContainerChange(IJavaElementDelta delta) {
		IJavaElement element = delta.getElement();
		int flags = delta.getFlags();
		if (element instanceof IJavaProject) {
			if ((flags & (IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED)) == 0) {
				return false;
			}
			return hasWtpClasspathContainer((IJavaProject) element);
		}
		if (!(element instanceof IPackageFragmentRoot)) {
			return false;
		}
		if ((flags & (IJavaElementDelta.F_ADDED_TO_CLASSPATH
				| IJavaElementDelta.F_REMOVED_FROM_CLASSPATH
				| IJavaElementDelta.F_CLASSPATH_REORDER
				| IJavaElementDelta.F_REORDER)) == 0) {
			return false;
		}
		return isWtpPackageFragmentRoot((IPackageFragmentRoot) element);
	}

	private boolean hasWtpClasspathContainer(IJavaProject javaProject) {
		if (javaProject == null || !javaProject.exists()) {
			return false;
		}
		IClasspathEntry[] entries;
		try {
			entries = javaProject.getRawClasspath();
		} catch (JavaModelException e) {
			LOG.debug("Failed to inspect raw classpath for {}", javaProject.getElementName(), e);
			return false;
		}
		if (entries == null) {
			return false;
		}
		for (IClasspathEntry entry : entries) {
			if (entry != null && entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && isWtpContainer(entry.getPath())) {
				return true;
			}
		}
		return false;
	}

	private boolean isWtpPackageFragmentRoot(IPackageFragmentRoot packageFragmentRoot) {
		if (packageFragmentRoot == null) {
			return false;
		}
		try {
			IClasspathEntry rawClasspathEntry = packageFragmentRoot.getRawClasspathEntry();
			return rawClasspathEntry != null && isWtpContainer(rawClasspathEntry.getPath());
		} catch (JavaModelException e) {
			LOG.debug("Failed to inspect classpath root {}", packageFragmentRoot.getElementName(), e);
			return false;
		}
	}

	private void notifyClasspathContainerChangeListeners() {
		for (Runnable listener : classpathContainerChangeListeners) {
			try {
				listener.run();
			} catch (RuntimeException e) {
				LOG.warn("Classpath container change listener failed", e);
			}
		}
	}

	private String extractJavadoc(IClasspathEntry entry) {
		IClasspathAttribute[] attrs = entry.getExtraAttributes();
		if (attrs == null) {
			return null;
		}
		for (IClasspathAttribute attr : attrs) {
			if (attr != null && IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME.equals(attr.getName())) {
				String value = attr.getValue();
				return value == null || value.isBlank() ? null : value;
			}
		}
		return null;
	}

	private String stringValue(IPath path) {
		return path == null ? null : path.toString();
	}

	private String resolveEntryPath(IClasspathEntry entry) {
		return resolveEntryPath(entry, entry == null ? null : entry.getPath());
	}

	private String resolveEntrySourcePath(IClasspathEntry entry) {
		return resolveEntryPath(entry, entry == null ? null : entry.getSourceAttachmentPath());
	}

	private String resolveEntryPath(IClasspathEntry entry, IPath path) {
		if (path == null || entry == null) {
			return null;
		}
		if (entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY) {
			return path.toString();
		}
		IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		if (resource != null && resource.getLocation() != null) {
			return resource.getLocation().toString();
		}
		return path.toString();
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
}
