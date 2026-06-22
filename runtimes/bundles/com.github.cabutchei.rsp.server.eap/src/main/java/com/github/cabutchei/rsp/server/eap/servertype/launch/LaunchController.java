package com.github.cabutchei.rsp.server.eap.servertype.launch;


import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerAttributes;
import org.jboss.tools.as.core.server.controllable.subsystems.internal.LocalJBossLaunchController;
import org.jboss.ide.eclipse.as.wtp.core.debug.RemoteDebugUtils;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.ControllableServerBehavior;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.IControllableServerBehavior;
import org.jboss.ide.eclipse.as.wtp.core.server.behavior.IServerShutdownController;

import java.util.ArrayList;
import java.util.List;


/**
    This custom controller allows us inject jdwp args
 */
public class LaunchController extends LocalJBossLaunchController {

    private static final String DEBUG_PORT_KEY = "com.github.cabutchei.rsp.server.eap.debugPort";
    private static final String JBOSS_MODULES_JAR = "jboss-modules.jar";

    @Override
    public void setupLaunchConfiguration(ILaunchConfigurationWorkingCopy workingCopy, IProgressMonitor monitor)
            throws CoreException {
        super.setupLaunchConfiguration(workingCopy, monitor);
        cleanupStaleModulesClasspathEntries(workingCopy);

        int port = getDebugPort(getServer());

        String vmArgs = workingCopy.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "");
        vmArgs = stripExistingDebugArgs(vmArgs);

        String jdwp = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:" + port;
        String updated = vmArgs.isEmpty() ? jdwp : (vmArgs + " " + jdwp);

        workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, updated);
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
        boolean isDebug = ILaunchManager.DEBUG_MODE.equals(mode);
        // flipping from 'debug' to 'run' bypasses StandardVMDebugger and prevents port randomization
        String effectiveMode = ILaunchManager.DEBUG_MODE.equals(mode) ? ILaunchManager.RUN_MODE : mode;
        super.launch(configuration, effectiveMode, launch, monitor);
        if (isDebug) {
            // we have flip back in order to make the server appear as debug mode
            IControllableServerBehavior beh = getControllableBehavior();
            if (beh instanceof ControllableServerBehavior) {
                ((ControllableServerBehavior) beh).setRunMode(ILaunchManager.DEBUG_MODE);
            }
        }

    }

    private int getDebugPort(IServerAttributes server) {
        String raw;
        int port;
        try {
            raw = server.getAttribute(RemoteDebugUtils.DEBUG_PORT, (String)null);
            port = Integer.parseInt(raw);
        } catch (NumberFormatException e) {}
        try {
            port = (Integer) this.getControllableBehavior().getSharedData(DEBUG_PORT_KEY);
        } catch (NumberFormatException e) {
            return 0;
        }
        return port;
    }

    private static String stripExistingDebugArgs(String vmArgs) {
        if (vmArgs == null || vmArgs.isEmpty()) return "";
        String out = vmArgs;
        out = out.replaceAll("(^|\\s)-agentlib:jdwp=\\S+", " ");
        out = out.replaceAll("(^|\\s)-Xrunjdwp:\\S+", " ");
        out = out.replaceAll("(^|\\s)-Xdebug(\\s|$)", " ");
        out = out.replaceAll("(^|\\s)-Xnoagent(\\s|$)", " ");
        out = out.replaceAll("\\s+", " ").trim();
        return out;
    }

    /**
     * fix bug where updating the runtime's location causes server start to hang. This happens when, for some reason, a new runtime gets created.
     * Jboss' classpath logic updates the classpath property, so stale locations remain in the array. This means that the classpath may reference
     * jboss-modules.jar in a possibly non-existing path (along with current one).
     * @param workingCopy
     * @throws CoreException
     */
    private void cleanupStaleModulesClasspathEntries(ILaunchConfigurationWorkingCopy workingCopy) throws CoreException {
        List<String> classpath = workingCopy.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH,
                new ArrayList<>());
        if (classpath.isEmpty()) {
            return;
        }

        IServer server = getServer();
        if (server == null || server.getRuntime() == null || server.getRuntime().getLocation() == null) {
            return;
        }

        IPath currentModulesJar = server.getRuntime().getLocation().append(JBOSS_MODULES_JAR);
        String currentModulesJarPath = normalizePath(currentModulesJar.toOSString());

        List<String> cleaned = new ArrayList<>(classpath.size());
        boolean currentModulesEntrySeen = false;
        String firstModulesEntry = null;
        for (String entryMemento : classpath) {
            String entryLocation = normalizePath(getRuntimeClasspathLocation(entryMemento));
            if (entryLocation == null || !entryLocation.endsWith(JBOSS_MODULES_JAR)) {
                if (!cleaned.contains(entryMemento)) {
                    cleaned.add(entryMemento);
                }
                continue;
            }
            if (firstModulesEntry == null) {
                firstModulesEntry = entryMemento;
            }
            if (!currentModulesEntrySeen && pathsEqual(currentModulesJarPath, entryLocation)) {
                cleaned.add(entryMemento);
                currentModulesEntrySeen = true;
            }
        }

        // Never strip every modules entry; keep one fallback if path normalization failed to match.
        if (!currentModulesEntrySeen && firstModulesEntry != null && !cleaned.contains(firstModulesEntry)) {
            cleaned.add(firstModulesEntry);
        }

        if (!cleaned.equals(classpath)) {
            workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, cleaned);
        }
    }

    private String getRuntimeClasspathLocation(String entryMemento) {
        try {
            IRuntimeClasspathEntry entry = JavaRuntime.newRuntimeClasspathEntry(entryMemento);
            return entry.getLocation();
        } catch (CoreException e) {
            return null;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return new File(path).getAbsolutePath();
        }
    }

    private boolean pathsEqual(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || left.equalsIgnoreCase(right);
    }

    @Override
    public IServerShutdownController getShutdownController() {
        System.out.println();
        return super.getShutdownController();
    }
}
