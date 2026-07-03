package com.github.cabutchei.rsp.server.websphere.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;

import com.github.cabutchei.rsp.eclipse.wst.api.IWstServerDelegateAccess;
import com.github.cabutchei.rsp.server.spi.servertype.IServerAttributes;
import com.ibm.ws.ast.st.v85.core.internal.WASServer;
import com.ibm.ws.ast.st.v85.core.internal.util.ServerXmlFileHandler;
import com.ibm.ws.ast.st.common.core.internal.AbstractWASServerBehaviour;
import com.ibm.ws.ast.st.core.internal.util.IMemento;
import com.ibm.ws.ast.st.core.internal.util.XMLMemento;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;


public final class WebSphereWstServerAccess implements IWstServerDelegateAccess<WASServer> {
	private static final String WS_PROFILE_RESOURCE =
			"com/ibm/ws/profile/resourcebundle/WSProfileResourceBundle.class";
	private static volatile ClassLoader websphereContextClassLoader;

	private WebSphereWstServerAccess() {
		// utility class
	}

	private static final AtomicLong XMI_COUNTER = new AtomicLong();
	public static final WebSphereWstServerAccess INSTANCE = new WebSphereWstServerAccess();

	@Override
	public Class<WASServer> getDelegateType() {
		return WASServer.class;
	}

	public static WASServer getWstDelegate(IServerAttributes server) throws CoreException {
		return server.getAdapter(WASServer.class);
	}

	public static IStatus validateWebSphereProfileExists(IServerAttributes server) throws CoreException {
		WASServer wstDelegate = getWstDelegate(server);
		if (wstDelegate == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
						"WST WASServer delegate not found"));
		}
		return withWebSphereContextClassLoader(() -> {
			List<String> profiles = Arrays.asList(wstDelegate.getProfileNames());
			if (profiles == null || profiles.isEmpty() || !profiles.contains(wstDelegate.getProfileName())) {
				return new Status(IStatus.ERROR, Activator.BUNDLE_ID, "WebSphere profile '"
						+ wstDelegate.getProfileName() + "' does not exist in the specified WebSphere installation.");
			}
			return Status.OK_STATUS;
		});
	}

	public static ServerXmlFileHandler createServerXmlFileHandler(IServerAttributes server) throws IOException, CoreException  {
		WASServer wasServer;
		wasServer = getWstDelegate(server);
		return createServerXmlFileHandler(wasServer.getWebSphereInstallPath(), wasServer.getProfileName(), wasServer.getBaseServerName());
	}

	public static ServerXmlFileHandler createServerXmlFileHandler(String curWASInstallRoot, String profileName, String serverName) throws IOException {
		return withWebSphereContextClassLoader(() -> ServerXmlFileHandler.create(curWASInstallRoot, profileName, serverName));
	}

	private static <T, E extends Exception> T withWebSphereContextClassLoader(ThrowingSupplier<T, E> supplier) throws E {
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		ClassLoader websphereLoader = getWebSphereContextClassLoader();
		if (websphereLoader == null || websphereLoader == original) {
			return supplier.get();
		}
		Thread.currentThread().setContextClassLoader(websphereLoader);
		try {
			return supplier.get();
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private static ClassLoader getWebSphereContextClassLoader() {
		ClassLoader cached = websphereContextClassLoader;
		if (hasWebSphereProfileResource(cached)) {
			return cached;
		}
		ClassLoader resolved = resolveWebSphereContextClassLoader();
		if (hasWebSphereProfileResource(resolved)) {
			websphereContextClassLoader = resolved;
			return resolved;
		}
		return null;
	}

	private static ClassLoader resolveWebSphereContextClassLoader() {
		BundleContext context = getBundleContext();
		if (context == null) {
			return null;
		}
		for (Bundle bundle : context.getBundles()) {
			URL resource = bundle.getResource(WS_PROFILE_RESOURCE);
			if (resource == null) {
				continue;
			}
			BundleWiring wiring = bundle.adapt(BundleWiring.class);
			if (wiring == null) {
				continue;
			}
			ClassLoader loader = wiring.getClassLoader();
			if (hasWebSphereProfileResource(loader)) {
				return loader;
			}
		}
		return null;
	}

	private static BundleContext getBundleContext() {
		Bundle bundle = FrameworkUtil.getBundle(WebSphereWstServerAccess.class);
		return bundle == null ? null : bundle.getBundleContext();
	}

	private static boolean hasWebSphereProfileResource(ClassLoader loader) {
		return loader != null && loader.getResource(WS_PROFILE_RESOURCE) != null;
	}

	// public static int getDebugPortNum(IServerAttributes server) throws CoreException {
	// 	WASServer wasServer = getWstDelegate(server);
	// 	return wasServer.getDebugPortNum();
	// }

	public static int getDebugPort(IServerAttributes server) {
		try {
			return createServerXmlFileHandler(server).getDebugPortNum();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return 0;
	}

	public static String getWebSphereInstallPath(IServerAttributes server) throws CoreException {
		return getWstDelegate(server).getWebSphereInstallPath();
	}

	public static String getProfileName(IServerAttributes server) throws CoreException {
		return getWstDelegate(server).getProfileName();
	}

	public static String getBaseServerName(IServerAttributes server) throws CoreException {
		return getWstDelegate(server).getBaseServerName();
	}

	public static String getServerAdminHostName(IServerAttributes server) throws CoreException {
		return getWstDelegate(server).getServerAdminHostName();
	}

	public static int getServerAdminPortNum(IServerAttributes server) throws CoreException {
		return getWstDelegate(server).getServerAdminPortNum();
	}

	public static int getAdminConsolePortNum(IServerAttributes server) throws CoreException {
		return server.getAdapter(AbstractWASServerBehaviour.class).getAdminConsolePortNum();
	}

	public static String getServerXmlFilePath(IServerAttributes server) throws IOException, CoreException {
		return createServerXmlFileHandler(server).getServerXMLFilePath();
	}

	public static void setSystemProperties(IServerAttributes server, Map<String, String> systemProperties) throws CoreException {
		try {
			ServerXmlFileHandler handler = createServerXmlFileHandler(server);
			IMemento jvmEntry = handler.getJavaVirtualMachine();
			if (jvmEntry == null) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
						"Unable to resolve JVM entry in server.xml"));
			}

			Map<String, IMemento> existing = new HashMap<>();
			IMemento[] children = jvmEntry.getChildren("systemProperties");
			if (children != null) {
				for (IMemento child : children) {
					String name = child.getString("name");
					if (name != null && !name.isEmpty()) {
						existing.put(name, child);
					}
				}
			}

			Map<String, String> desired = systemProperties == null ? new HashMap<>() : systemProperties;
			for (Map.Entry<String, String> entry : desired.entrySet()) {
				String name = entry.getKey();
				if (name == null || name.trim().isEmpty()) {
					continue;
				}
				String value = entry.getValue() == null ? "" : entry.getValue();
				IMemento target = existing.remove(name);
				if (target == null) {
					target = jvmEntry.createChild("systemProperties");
					target.putString("name", name);
					target.putBoolean("required", false);
					target.putString("xmi:id", generatePropertyXmiId());
				}
				target.putString("value", value);
			}

			if (!existing.isEmpty() && jvmEntry instanceof XMLMemento) {
				XMLMemento xml = (XMLMemento) jvmEntry;
				for (String name : existing.keySet()) {
					xml.removeCustomProperty(name);
				}
			}
			handler.save();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Unable to update WebSphere server.xml", e));
		}
	}

	public static Map<String, String> getSystemProperties(IServerAttributes server) throws CoreException {
		try {
			IMemento jvmEntry = createServerXmlFileHandler(server).getJavaVirtualMachine();
			Map<String, String> systemProperties = new HashMap<>();
			if (jvmEntry != null) {
				IMemento[] children = jvmEntry.getChildren("systemProperties");
				if (children != null) {
					for (IMemento child : children) {
						String name = child.getString("name");
						if (name == null || name.isEmpty()) {
							continue;
						}
						String value = child.getString("value");
						systemProperties.put(name, value == null ? "" : value);
					}
				}
			}
			return systemProperties;
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.BUNDLE_ID,
					"Unable to read WebSphere server.xml", e));
		}
	}

	private static String generatePropertyXmiId() {
		long now = System.currentTimeMillis();
		long seq = XMI_COUNTER.incrementAndGet();
		return "CustomProperty_" + now + "_" + seq;
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T, E extends Exception> {
		T get() throws E;
	}
}
