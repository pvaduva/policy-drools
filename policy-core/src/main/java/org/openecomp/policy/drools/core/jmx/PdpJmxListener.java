/*-
 * ============LICENSE_START=======================================================
 * policy-core
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

package org.openecomp.policy.drools.core.jmx;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.common.logging.eelf.MessageCodes;

public class PdpJmxListener {

	public static final Logger logger = FlexLogger.getLogger(PdpJmxListener.class);
	
	public static void stop() {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		try {
			server.unregisterMBean(new ObjectName("PolicyEngine:type=PdpJmx"));
		} catch (MBeanRegistrationException | InstanceNotFoundException
				| MalformedObjectNameException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PdpJmxListener.stop()", "Could not unregister PolicyEngine:type=PdpJmx MBean with the MBean server");
		}
		
	}

	
	public static void start() {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		try {
			server.registerMBean(PdpJmx.getInstance(), new ObjectName("PolicyEngine:type=PdpJmx"));
		} catch (InstanceAlreadyExistsException | MBeanRegistrationException
				| NotCompliantMBeanException | MalformedObjectNameException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PdpJmxListener.start()", "Could not register PolicyEngine:type=PdpJmx MBean with the MBean server");
		}
		
	}

}