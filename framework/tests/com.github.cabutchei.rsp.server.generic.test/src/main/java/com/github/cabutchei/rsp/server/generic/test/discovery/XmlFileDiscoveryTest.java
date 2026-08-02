/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server.generic.test.discovery;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.github.cabutchei.rsp.api.dao.ServerBean;
import com.github.cabutchei.rsp.server.generic.discovery.XmlFileDiscovery;
import org.junit.Test;

public class XmlFileDiscoveryTest {

	private String getXmlContents(String name, String fullVersion, String productDisplay, String productId,
			String productVersion) {
		return "<websphere name=\"" + name + "\" version=\"ignored\">" +
				"<product id=\"" + productId + "\" display=\"" + productDisplay + "\" version=\"" + productVersion + "\"/>" +
				"<fullVersion>" + fullVersion + "</fullVersion>" +
				"</websphere>";
	}

	private File writeXml(File root, String fileName, String contents) throws IOException {
		File xmlFile = new File(root, fileName);
		Files.write(xmlFile.toPath(), contents.getBytes());
		return xmlFile;
	}

	@Test
	public void testSingleFileDiscovery() throws IOException {
		File root = Files.createTempDirectory(getClass().getName()).toFile();
		writeXml(root, "descriptor.xml", getXmlContents("IBM WebSphere Application Server", "8.5.5.20",
				"IBM WebSphere Application Server", "BASE", "8.5.5.20"));

		XmlFileDiscovery discovery = new XmlFileDiscovery(
				"test.id", "TestName", "server.type.id",
				"descriptor.xml", false, "@name", "IBM WebSphere",
				"descriptor.xml", false, "fullVersion", "8.5.");
		assertTrue(discovery.isServerRoot(root));
		ServerBean sb = discovery.createServerBean(root);
		assertNotNull(sb);
	}

	@Test
	public void testSingleFileDiscoveryWrongName() throws IOException {
		File root = Files.createTempDirectory(getClass().getName()).toFile();
		writeXml(root, "descriptor.xml", getXmlContents("Other Server", "8.5.5.20",
				"IBM WebSphere Application Server", "BASE", "8.5.5.20"));

		XmlFileDiscovery discovery = new XmlFileDiscovery(
				"test.id", "TestName", "server.type.id",
				"descriptor.xml", false, "@name", "IBM WebSphere",
				"descriptor.xml", false, "fullVersion", "8.5.");
		assertFalse(discovery.isServerRoot(root));
	}

	@Test
	public void testSingleFileDiscoveryWrongVersion() throws IOException {
		File root = Files.createTempDirectory(getClass().getName()).toFile();
		writeXml(root, "descriptor.xml", getXmlContents("IBM WebSphere Application Server", "9.0.0.0",
				"IBM WebSphere Application Server", "BASE", "9.0.0.0"));

		XmlFileDiscovery discovery = new XmlFileDiscovery(
				"test.id", "TestName", "server.type.id",
				"descriptor.xml", false, "@name", "IBM WebSphere",
				"descriptor.xml", false, "fullVersion", "8.5.");
		assertFalse(discovery.isServerRoot(root));
	}

	@Test
	public void testAttributeKeyDiscoveryWithGlob() throws IOException {
		File root = Files.createTempDirectory(getClass().getName()).toFile();
		writeXml(root, "descriptor.xml", getXmlContents("IBM WebSphere Application Server", "8.5.5.20",
				"IBM WebSphere Application Server", "BASE", "8.5.5.20"));

		XmlFileDiscovery discovery = new XmlFileDiscovery(
				"test.id", "TestName", "server.type.id",
				"*.xml", true, "product@display", "IBM WebSphere",
				"*.xml", true, "product@version", "8.5.");
		assertTrue(discovery.isServerRoot(root));
		ServerBean sb = discovery.createServerBean(root);
		assertNotNull(sb);
	}
}
