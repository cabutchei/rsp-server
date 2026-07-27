/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

final class EmbeddedRuntimeLog {
	private static final Object LOCK = new Object();

	private static Writer writer;
	private static File logFile;

	private EmbeddedRuntimeLog() {
	}

	static void configure(String logFilePath) throws IOException {
		synchronized (LOCK) {
			closeWriter();
			logFile = null;
			if (logFilePath == null || logFilePath.trim().isEmpty()) {
				return;
			}
			File target = new File(logFilePath).getAbsoluteFile();
			File parent = target.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target, true), StandardCharsets.UTF_8));
			logFile = target;
			appendLocked("[embedded] log initialized at " + target.getAbsolutePath());
		}
	}

	static void append(String message) {
		if (message == null || message.isEmpty()) {
			return;
		}
		synchronized (LOCK) {
			appendLocked(message);
		}
	}

	static String getPath() {
		synchronized (LOCK) {
			return logFile == null ? null : logFile.getAbsolutePath();
		}
	}

	static void close() {
		synchronized (LOCK) {
			appendLocked("[embedded] log closed");
			closeWriter();
			logFile = null;
		}
	}

	private static void appendLocked(String message) {
		if (writer == null) {
			return;
		}
		try {
			writer.write(message);
			if (!message.endsWith(System.lineSeparator())) {
				writer.write(System.lineSeparator());
			}
			writer.flush();
		} catch (IOException ioe) {
			closeWriter();
		}
	}

	private static void closeWriter() {
		if (writer == null) {
			return;
		}
		try {
			writer.close();
		} catch (IOException ioe) {
			// ignore shutdown-time close failures
		} finally {
			writer = null;
		}
	}
}
