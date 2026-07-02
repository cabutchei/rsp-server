/*******************************************************************************
 * Copyright (c) 2018 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.github.cabutchei.rsp.server;

import com.github.cabutchei.rsp.logging.LoggingConstants;
import com.github.cabutchei.rsp.server.persistence.DataLocationCore;

/**
 * Collect all flags people may want to know about here
 */
public class RSPFlags {
	public static final String SYSPROP_DATA_LOCATION = DataLocationCore.SYSPROP_DATA_LOCATION;
	public static final String SYSPROP_DATA_DEFAULT_LOCATION = DataLocationCore.DATA_LOCATION_LEGACY_DEFAULT;
	public static final String LOG_LEVEL_FLAG = LoggingConstants.SYSPROP_LOG_LEVEL_FLAG;
	public static final String SYSPROP_SERVER_PORT = "rsp.server.port";
	public static final String SYSPROP_SERVER_AUTOSTART = "rsp.server.autostart";
	public static final int DEFAULT_PORT = 27511;
	
	public static int getServerPort() {
		return getIntSysprop(SYSPROP_SERVER_PORT, DEFAULT_PORT);
	}

	public static boolean isServerAutostartEnabled() {
		String value = System.getProperty(SYSPROP_SERVER_AUTOSTART);
		return value == null ? true : Boolean.parseBoolean(value);
	}

	public static int getIntSysprop(String key, int def) {
		int logLevel = def;
		String logLevelTmp = System.getProperty(key);
		if( logLevelTmp != null ) {
			try {
				logLevel = Integer.parseInt(logLevelTmp);
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		return logLevel;
	}
}
