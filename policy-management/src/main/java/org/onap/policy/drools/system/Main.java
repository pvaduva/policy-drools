/*-
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.system;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatic entry point to the management layer
 */
public class Main {

	/**
	 * logback configuration file system property
	 */
	public static final String LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY = "logback.configurationFile";
	
	/**
	 * logback configuration file system property
	 */
	public static final String LOGBACK_CONFIGURATION_FILE_DEFAULT = "config/logback.xml";
	
	/**
	 * constructor (hides public default one)
	 */
	private Main() {}	
	
	/**
	 * main
	 * 
	 * @param args program arguments
	 */
	public static void main(String args[]) {
		
		/* logging defaults */
		
		if (System.getProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY) == null)
			System.setProperty(LOGBACK_CONFIGURATION_FILE_SYSTEM_PROPERTY, LOGBACK_CONFIGURATION_FILE_DEFAULT);
		
		/* 0. boot */
		
		PolicyEngine.manager.boot(args);
		
		Logger logger = LoggerFactory.getLogger(Main.class);
		
		File configDir = new File(SystemPersistence.CONFIG_DIR_NAME);
		
		if (!configDir.isDirectory()) {
			throw new IllegalArgumentException
						("config directory: " + configDir.getAbsolutePath() + 
						 " not found");
		}
		
		/* 1. Configure the Engine */

		try {
			Path policyEnginePath = Paths.get(configDir.toPath().toString(), SystemPersistence.PROPERTIES_FILE_ENGINE);
			Properties properties = PropertyUtil.getProperties(policyEnginePath.toFile());
			PolicyEngine.manager.configure(properties);
		} catch (Exception e) {
			logger.warn("Main: cannot initialize {} because of {}", PolicyEngine.manager, e.getMessage(), e);
		}
		
		/* 2. Start the Engine with the basic services only (no Policy Controllers) */
		
		try {
			boolean success = PolicyEngine.manager.start();
			if (!success) {
				logger.warn("Main: {} has been partially started", PolicyEngine.manager);		
			}
		} catch (IllegalStateException e) {
			logger.warn("Main: cannot start {} (bad state) because of {}", PolicyEngine.manager, e.getMessage(), e);
		} catch (Exception e) {
			logger.warn("Main: cannot start {} because of {}", PolicyEngine.manager, e.getMessage(), e);
			System.exit(1);
		}
		
		/* 3. Create and start the controllers */
		
		File[] controllerFiles = configDir.listFiles();
		for (File config : controllerFiles) {

			if (config.getName().endsWith(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX)) {
				int idxSuffix = 
						config.getName().indexOf(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				int lastIdxSuffix = 
						config.getName().lastIndexOf(SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				if (idxSuffix != lastIdxSuffix) {
					throw new IllegalArgumentException
								("Improper naming of controller properties file: " +
				                 "Expected <controller-name>" + 
				                 SystemPersistence.PROPERTIES_FILE_CONTROLLER_SUFFIX);
				}

				String name = 
						config.getName().substring(0, lastIdxSuffix);
				try {
					Properties properties = PropertyUtil.getProperties(config);
					PolicyController controller = PolicyEngine.manager.createPolicyController(name, properties);
					controller.start();
				} catch (Exception e) {
					logger.error("Main: cannot instantiate policy-controller {} because of {}", name, e.getMessage(), e);
				} catch (LinkageError e) {
					logger.warn("Main: cannot instantiate policy-controller {} (linkage) because of {}", 
							    name, e.getMessage(), e);
				}
			}
		}
	}
}