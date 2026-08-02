package com.github.cabutchei.rsp.distribution.smoke;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class InstalledProductSmokeTest {

	@Test
	public void materializedProductsContainPlatformLaunchersAndCoreBundles() throws IOException {
		Path distributionTarget = Paths.get(System.getProperty("distribution.target.dir"));
		Path productsRoot = distributionTarget.resolve("products").resolve("com.github.cabutchei.rsp.server.product");

		assertDirectory(productsRoot);

		Path macApp = productsRoot.resolve("macosx/cocoa/x86_64/rsp-wtp-server.app");
		assertDirectory(macApp);
		assertRegularFile(macApp.resolve("Contents/Info.plist"));
		assertRegularFile(macApp.resolve("Contents/MacOS/eclipse"));
		assertRegularFile(macApp.resolve("Contents/Eclipse/configuration/config.ini"));
		assertContainsGlob(macApp.resolve("Contents/Eclipse/plugins"), "com.github.cabutchei.rsp.server_*.jar");
		assertContainsGlob(macApp.resolve("Contents/Eclipse/plugins"), "com.github.cabutchei.rsp.server.eap_*.jar");
		assertContainsGlob(macApp.resolve("Contents/Eclipse/plugins"), "org.eclipse.equinox.launcher_*.jar");
		assertContainsGlob(macApp.resolve("Contents/Eclipse/plugins"),
				"org.eclipse.equinox.launcher.cocoa.macosx.x86_64_*");

		Path winRoot = productsRoot.resolve("win32/win32/x86_64/rsp-wtp-server");
		assertDirectory(winRoot);
		assertRegularFile(winRoot.resolve("eclipse.exe"));
		assertRegularFile(winRoot.resolve("configuration/config.ini"));
		assertContainsGlob(winRoot.resolve("plugins"), "com.github.cabutchei.rsp.server_*.jar");
		assertContainsGlob(winRoot.resolve("plugins"), "com.github.cabutchei.rsp.server.eap_*.jar");
		assertContainsGlob(winRoot.resolve("plugins"), "org.eclipse.equinox.launcher_*.jar");
		assertContainsGlob(winRoot.resolve("plugins"), "org.eclipse.equinox.launcher.win32.win32.x86_64_*");
	}

	private static void assertDirectory(Path path) {
		assertTrue("Expected directory to exist: " + path, Files.isDirectory(path));
	}

	private static void assertRegularFile(Path path) {
		assertTrue("Expected file to exist: " + path, Files.isRegularFile(path));
	}

	private static void assertContainsGlob(Path directory, String glob) throws IOException {
		assertDirectory(directory);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
			if (stream.iterator().hasNext()) {
				return;
			}
		}
		fail("Expected to find entry matching '" + glob + "' in " + directory);
	}
}
