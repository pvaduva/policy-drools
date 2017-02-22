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

package org.openecomp.policy.drools.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;
import org.openecomp.policy.drools.controller.DroolsController;
import org.openecomp.policy.drools.core.FeatureAPI;
import org.openecomp.policy.drools.core.jmx.PdpJmxListener;
import org.openecomp.policy.drools.event.comm.Topic;
import org.openecomp.policy.drools.event.comm.Topic.CommInfrastructure;
import org.openecomp.policy.drools.event.comm.TopicEndpoint;
import org.openecomp.policy.drools.event.comm.TopicListener;
import org.openecomp.policy.drools.event.comm.TopicSink;
import org.openecomp.policy.drools.event.comm.TopicSource;
import org.openecomp.policy.drools.http.server.HttpServletServer;
import org.openecomp.policy.drools.persistence.SystemPersistence;
import org.openecomp.policy.drools.properties.Lockable;
import org.openecomp.policy.drools.properties.PolicyProperties;
import org.openecomp.policy.drools.properties.Startable;
import org.openecomp.policy.drools.protocol.coders.EventProtocolCoder;
import org.openecomp.policy.drools.protocol.configuration.ControllerConfiguration;
import org.openecomp.policy.drools.protocol.configuration.PdpdConfiguration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Policy Engine, the top abstraction for the Drools PDP Policy Engine.
 * It abstracts away a Drools PDP Engine from management purposes.
 * This is the best place to looking at the code from a top down approach. 
 * Other managed entities can be obtained from the PolicyEngine, hierarchically. 
 * <br>
 * PolicyEngine 1 --- * PolicyController 1 --- 1 DroolsController 1 --- 1 PolicyContainer 1 --- * PolicySession
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 UebTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 DmaapTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 DmaapTopicWriter
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1 RestTopicReader
 * <br>
 * PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1 RestTopicWriter
 * <br>
 * PolicyEngine 1 --- 1 ManagementServer
 */
public interface PolicyEngine extends Startable, Lockable, TopicListener {
	
	/**
	 * Default Config Server Port
	 */
	public static final int CONFIG_SERVER_DEFAULT_PORT = 9696;
	
	/**
	 * Default Config Server Hostname
	 */
	public static final String CONFIG_SERVER_DEFAULT_HOST = "localhost";
	
	/**
	 * configure the policy engine according to the given properties
	 * 
	 * @param properties Policy Engine properties
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 */
	public void configure(Properties properties)  throws IllegalArgumentException;

	/**
	 * registers a new Policy Controller with the Policy Engine
	 * initialized per properties.
	 * 
	 * @param controller name
	 * @param properties properties to initialize the Policy Controller
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted.
	 * @return the newly instantiated Policy Controller
	 */
	public PolicyController createPolicyController(String name, Properties properties)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates the Policy Engine with the given configuration
	 * 
	 * @param configuration the configuration
	 * @return success or failure
	 * @throws IllegalArgumentException if invalid argument provided
	 * @throws IllegalStateException if the system is in an invalid state
	 */
	public boolean configure(PdpdConfiguration configuration)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates a set of Policy Controllers with configuration information
	 * 
	 * @param configuration
	 * @return
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configuration)
		throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * updates an already existing Policy Controller with configuration information
	 * 
	 * @param configuration configuration
	 * 
	 * @return the updated Policy Controller
	 * @throws IllegalArgumentException in the configuration is invalid
	 * @throws IllegalStateException if the controller is in a bad state
	 * @throws Exception any other reason
	 */
	public PolicyController updatePolicyController(ControllerConfiguration configuration)
		throws Exception;

	/**
	 * removes the Policy Controller identified by its name from the Policy Engine
	 * 
	 * @param name name of the Policy Controller
	 * @return the removed Policy Controller
	 */
	public void removePolicyController(String name);
	
	/**
	 * removes a Policy Controller from the Policy Engine
	 * @param controller the Policy Controller to remove from the Policy Engine
	 */
	public void removePolicyController(PolicyController controller);

	/**
	 * returns a list of the available Policy Controllers
	 * 
	 * @return list of Policy Controllers
	 */
	public List<PolicyController> getPolicyControllers();
	
	/**
	 * get unmanaged sources
	 * 
	 * @return unmanaged sources
	 */
	public List<TopicSource> getSources();
	
	/**
	 * get unmanaged sinks
	 * 
	 * @return unmanaged sinks
	 */
	public List<TopicSink> getSinks();
	
	/**
	 * get unmmanaged http servers list
	 * @return http servers
	 */
	public List<HttpServletServer> getHttpServers();
	
	/**
	 * get properties configuration
	 * 
	 * @return properties objects
	 */
	public Properties getProperties();
	
	/**
	 * Attempts the dispatching of an "event" object
	 * 
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 */
	public boolean deliver(String topic, Object event)
			throws IllegalArgumentException, IllegalStateException;
	
	/**
	 * Attempts the dispatching of an "event" object over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure identifier
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(String busType, String topic, Object event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Attempts the dispatching of an "event" object over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure enum
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(CommInfrastructure busType, String topic, Object event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Attempts delivering of an String over communication 
	 * infrastructure "busType"
	 * 
	 * @param eventBus Communication infrastructure identifier
	 * @param topic topic
	 * @param event the event object to send
	 * 
	 * @return true if successful, false if a failure has occurred.
	 * @throws IllegalArgumentException when invalid or insufficient 
	 *         properties are provided
	 * @throws IllegalStateException when the engine is in a state where
	 *         this operation is not permitted (ie. locked or stopped).
	 * @throws UnsupportedOperationException when the engine cannot deliver due
	 *         to the functionality missing (ie. communication infrastructure
	 *         not supported.
	 */
	public boolean deliver(CommInfrastructure busType, String topic, 
			               String event)
			throws IllegalArgumentException, IllegalStateException, 
			       UnsupportedOperationException;
	
	/**
	 * Invoked when the host goes into the active state.
	 */
	public void activate();

	/**
	 * Invoked when the host goes into the standby state.
	 */
	public void deactivate();

	/**
	 * get policy controller names
	 * 
	 * @return list of controller names
	 */
	public List<String> getControllers();
	
	/**
	 * Policy Engine Manager
	 */
	public final static PolicyEngine manager = new PolicyEngineManager();
}

/**
 * Policy Engine Manager Implementation
 */
class PolicyEngineManager implements PolicyEngine {
	/**
	 * logger
	 */
	private static Logger  logger = FlexLogger.getLogger(PolicyEngineManager.class);  	
	
	/**
	 * Is the Policy Engine running?
	 */
	protected boolean alive = false;
	
	/**
	 * Is the engine locked? 
	 */
	protected boolean locked = false;	
	
	/**
	 * Properties used to initialize the engine
	 */
	protected Properties properties;
	
	/**
	 * Policy Engine Sources
	 */
	protected List<? extends TopicSource> sources = new ArrayList<>();
	
	/**
	 * Policy Engine Sinks
	 */
	protected List<? extends TopicSink> sinks = new ArrayList<>();
	
	/**
	 * Policy Engine HTTP Servers
	 */
	protected List<HttpServletServer> httpServers = new ArrayList<HttpServletServer>();
	
	protected Gson decoder = new GsonBuilder().disableHtmlEscaping().create();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void configure(Properties properties) throws IllegalArgumentException {
		
		if (properties == null) {
			logger.warn("No properties provided");
			throw new IllegalArgumentException("No properties provided");
		}
		
		this.properties = properties;
		
		try {
			this.sources = TopicEndpoint.manager.addTopicSources(properties);
			for (TopicSource source: this.sources) {
				source.register(this);
			}
		} catch (Exception e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "configure");
		}
		
		try {
			this.sinks = TopicEndpoint.manager.addTopicSinks(properties);
		} catch (IllegalArgumentException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "configure");
		}
		
		try {
			this.httpServers = HttpServletServer.factory.build(properties);
		} catch (IllegalArgumentException e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "configure");
		}
		
		return;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController createPolicyController(String name, Properties properties) 
			throws IllegalArgumentException, IllegalStateException {
		
		// check if a PROPERTY_CONTROLLER_NAME property is present
		// if so, override the given name
		
		String propertyControllerName = properties.getProperty(PolicyProperties.PROPERTY_CONTROLLER_NAME);
		if (propertyControllerName != null && !propertyControllerName.isEmpty())  {
			if (!propertyControllerName.equals(name)) {
				throw new IllegalStateException("Proposed name (" + name + 
						                        ") and properties name (" + propertyControllerName + 
						                        ") don't match");
			}
			name = propertyControllerName;
		}
		
		// feature hook
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			feature.beforeCreateController(name, properties);
		}
		
		PolicyController controller = PolicyController.factory.build(name, properties);	
		if (this.isLocked())
			controller.lock();
		
		// feature hook
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			//  NOTE: this should change to the actual controller object
			feature.afterCreateController(name);
		}
		
		return controller;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean configure(PdpdConfiguration config) throws IllegalArgumentException, IllegalStateException {
	
		if (config == null)
			throw new IllegalArgumentException("No configuration provided");
		
		String entity = config.getEntity();
		
		switch (entity) {
		case PdpdConfiguration.CONFIG_ENTITY_CONTROLLER:
			/* only this one supported for now */
			List<ControllerConfiguration> configControllers = config.getControllers();
			if (configControllers == null || configControllers.isEmpty()) {
				if (logger.isInfoEnabled())
					logger.info("No controller configuration provided: " + config);
				return false;
			}
			List<PolicyController> policyControllers = this.updatePolicyControllers(config.getControllers());
			if (policyControllers == null || policyControllers.isEmpty())
				return false;
			else if (policyControllers.size() == configControllers.size())
				return true;
			
			return false;
		default:
			String msg = "Configuration Entity is not supported: " + entity;
			logger.warn(msg);
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configControllers)
			throws IllegalArgumentException, IllegalStateException {
		
		List<PolicyController> policyControllers = new ArrayList<PolicyController>();
		if (configControllers == null || configControllers.isEmpty()) {
			if (logger.isInfoEnabled())
				logger.info("No controller configuration provided: " + configControllers);
			return policyControllers;
		}
		
		for (ControllerConfiguration configController: configControllers) {
			try {
				PolicyController policyController = this.updatePolicyController(configController);
				policyControllers.add(policyController);
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "updatePolicyControllers");
			}
		}
		
		return policyControllers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PolicyController updatePolicyController(ControllerConfiguration configController) 
		   throws Exception {
		
		if (configController == null) 
			throw new IllegalArgumentException("No controller configuration has been provided");
		
		String controllerName = configController.getName();	
		if (controllerName == null || controllerName.isEmpty()) {
			logger.warn("controller-name  must be provided");
			throw new IllegalArgumentException("No controller configuration has been provided");
		}
		
		PolicyController policyController = null;
		try {		
			String operation = configController.getOperation();
			if (operation == null || operation.isEmpty()) {
				logger.warn("operation must be provided");
				throw new IllegalArgumentException("operation must be provided");
			}
			
			try {
				policyController = PolicyController.factory.get(controllerName);
			} catch (IllegalArgumentException e) {
				// not found
				logger.warn("Policy Controller " + controllerName + " not found");
			}
			
			if (policyController == null) {
				
				if (operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK) ||
					operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK)) {
					throw new IllegalArgumentException(controllerName + " is not available for operation " + operation);
				}
				
				/* Recovery case */
				
				logger.warn("controller " + controllerName + " does not exist.  " +
				            "Attempting recovery from disk");	
				
				Properties properties = 
						SystemPersistence.manager.getControllerProperties(controllerName);
				
				/* 
				 * returned properties cannot be null (per implementation) 
				 * assert (properties != null)
				 */
				
				if (properties == null) {
					throw new IllegalArgumentException(controllerName + " is invalid");
				}
				
				logger.warn("controller " + controllerName + " being recovered. " +
			                "Reset controller's bad maven coordinates to brainless");
				
				/* 
				 * try to bring up bad controller in brainless mode,
				 * after having it working, apply the new create/update operation.
				 */
				properties.setProperty(PolicyProperties.RULES_GROUPID, DroolsController.NO_GROUP_ID);
				properties.setProperty(PolicyProperties.RULES_ARTIFACTID, DroolsController.NO_ARTIFACT_ID);
				properties.setProperty(PolicyProperties.RULES_VERSION, DroolsController.NO_VERSION);
				
				policyController = PolicyEngine.manager.createPolicyController(controllerName, properties);
				
				/* fall through to do brain update operation*/
			}
			
			switch (operation) {
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE:
				PolicyController.factory.patch(policyController, configController.getDrools());
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE:
				policyController.unlock();
				PolicyController.factory.patch(policyController, configController.getDrools());
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK:
				policyController.lock();
				break;
			case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK:
				policyController.unlock();
				break;
			default:
				String msg = "Controller Operation Configuration is not supported: " + 
		                     operation + " for " + controllerName;
				logger.warn(msg);
				throw new IllegalArgumentException(msg);
			}
			
			return policyController;
		} catch (Exception e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "updatePolicyController " + e.getMessage());
			throw e;
		} catch (LinkageError e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine", "updatePolicyController " + e.getMessage());
			throw new IllegalStateException(e);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() throws IllegalStateException {
		
		if (this.locked) {
			throw new IllegalStateException("Engine is locked");
		}
		
		// Features hook
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			feature.beforeStartEngine();
		}
		
		synchronized(this) {
			this.alive = true;
		}
		
		boolean success = true;

		/* Start Policy Engine exclusively-owned (unmanaged) http servers */
		
		for (HttpServletServer httpServer: this.httpServers) {
			try {
				if (!httpServer.start())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, httpServer.toString(), this.toString());
			}
		}
		/* Start Policy Engine exclusively-owned (unmanaged) sources */
		
		for (TopicSource source: this.sources) {
			try {
				if (!source.start())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, source.toString(), this.toString());
			}
		}
		
		/* Start Policy Engine owned (unmanaged) sinks */
		
		for (TopicSink sink: this.sinks) {
			try {
				if (!sink.start())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, sink.toString(), this.toString());
			}
		}
		
		/* Start Policy Controllers */
		
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				if (!controller.start())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, controller.toString(), this.toString());
				success = false;
			}
		}
		
		/* Start managed Topic Endpoints */
		
		try {
			if (!TopicEndpoint.manager.start())
				success = false;			
		} catch (IllegalStateException e) {
			String msg = "Topic Endpoint Manager is in an invalid state: " + e.getMessage() + " : " + this;
			logger.warn(msg);			
		}
		
		
		// Start the JMX listener
		
		PdpJmxListener.start();
		
		// Features hook
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			feature.afterStartEngine();
		}

		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		
		/* stop regardless of the lock state */
		
		synchronized(this) {
			if (!this.alive)
				return true;
			
			this.alive = false;			
		}
		
		boolean success = true;
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				if (!controller.stop())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, controller.toString(), this.toString());
				success = false;
			}
		}
		
		/* Stop Policy Engine owned (unmanaged) sources */
		for (TopicSource source: this.sources) {
			try {
				if (!source.stop())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, source.toString(), this.toString());
			}
		}
		
		/* Stop Policy Engine owned (unmanaged) sinks */
		for (TopicSink sink: this.sinks) {
			try {
				if (!sink.stop())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, sink.toString(), this.toString());
			}
		}
		
		/* stop all managed topics sources and sinks */
		if (!TopicEndpoint.manager.stop())
			success = false;
		
		/* stop all unmanaged http servers */
		for (HttpServletServer httpServer: this.httpServers) {
			try {
				if (!httpServer.stop())
					success = false;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, httpServer.toString(), this.toString());
			}
		}		
		
		return success;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() throws IllegalStateException {

		synchronized(this) {
			this.alive = false;			
		}
		
		// feature hook reporting that the Policy Engine is being shut down		
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			feature.beforeShutdownEngine();
		}
		
		/* Shutdown Policy Engine owned (unmanaged) sources */
		for (TopicSource source: this.sources) {
			try {
				source.shutdown();
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, source.toString(), this.toString());
			}
		}
		
		/* Shutdown Policy Engine owned (unmanaged) sinks */
		for (TopicSink sink: this.sinks) {
			try {
				sink.shutdown();
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, sink.toString(), this.toString());
			}
		}
		
		/* Shutdown managed resources */
		PolicyController.factory.shutdown();
		TopicEndpoint.manager.shutdown();
		HttpServletServer.factory.destroy();
		
		// Stop the JMX listener
		
		PdpJmxListener.stop();
		
		// feature hook reporting that the Policy Engine has being shut down		
		for (FeatureAPI feature : FeatureAPI.impl.getList()) {
			feature.afterShutdownEngine();
		}
		
		new Thread(new Runnable() {
		    @Override
		    public void run() {
		    	try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					logger.warn("InterruptedException while shutting down management server: " +  this.toString());
				}		    	
				
				/* shutdown all unmanaged http servers */
				for (HttpServletServer httpServer: getHttpServers()) {
					try {
						httpServer.shutdown();
					} catch (Exception e) {
						logger.error(MessageCodes.EXCEPTION_ERROR, e, httpServer.toString(), this.toString());
					}
				} 
		    	
		    	try {
					Thread.sleep(5000L);
				} catch (InterruptedException e) {
					logger.warn("InterruptedException while shutting down management server: " +  this.toString());
				}
		    	
		    	System.exit(0);
		    }		    
		}).start();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAlive() {
		return this.alive;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean lock() {
		
		synchronized(this) {
			if (this.locked)
				return true;
			
			this.locked = true;			
		}
		
		boolean success = true;
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				success = controller.lock() && success;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, controller.toString(), this.toString());
				success = false;
			}
		}
		
		success = TopicEndpoint.manager.lock();		
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean unlock() {
		synchronized(this) {
			if (!this.locked)
				return true;
			
			this.locked = false;			
		}
		
		boolean success = true;
		List<PolicyController> controllers = PolicyController.factory.inventory();
		for (PolicyController controller : controllers) {
			try {
				success = controller.unlock() && success;
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, controller.toString(), this.toString());
				success = false;
			}
		}
		
		success = TopicEndpoint.manager.unlock();		
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isLocked() {
		return this.locked;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removePolicyController(String name) {
		PolicyController.factory.destroy(name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removePolicyController(PolicyController controller) {
		PolicyController.factory.destroy(controller);
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public List<PolicyController> getPolicyControllers() {
		return PolicyController.factory.inventory();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getControllers() {
		List<String> controllerNames = new ArrayList<String>();
		for (PolicyController controller: PolicyController.factory.inventory()) {
			controllerNames.add(controller.getName());
		}
		return controllerNames;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Properties getProperties() {
		return this.properties;
	}
	

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<TopicSource> getSources() {
		return (List<TopicSource>) this.sources;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<TopicSink> getSinks() {
		return (List<TopicSink>) this.sinks;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<HttpServletServer> getHttpServers() {
		return this.httpServers;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onTopicEvent(CommInfrastructure commType, String topic, String event) {
		/* configuration request */
		try {
			PdpdConfiguration configuration = this.decoder.fromJson(event, PdpdConfiguration.class);
			this.configure(configuration);
		} catch (Exception e) {
			logger.error(MessageCodes.EXCEPTION_ERROR, e, "CONFIGURATION ERROR IN PDP-D POLICY ENGINE: "+ event + ":" + e.getMessage() + ":" + this);
		}
		
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException {
		
		/*
		 * Note this entry point is usually from the DRL
		 */
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
			
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		List<? extends TopicSink> sinks = 
				TopicEndpoint.manager.getTopicSinks(topic);
		if (sinks == null || sinks.isEmpty() || sinks.size() > 1)
			throw new IllegalStateException
				("Cannot ensure correct delivery on topic " + topic + ": " + sinks);		

		return this.deliver(sinks.get(0).getTopicCommInfrastructure(), 
				            topic, event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(String busType, String topic, Object event) 
			throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		/*
		 * Note this entry point is usually from the DRL (one of the reasons
		 * busType is String.
		 */
		
		if (busType == null || busType.isEmpty())
			throw new IllegalArgumentException
				("Invalid Communication Infrastructure");
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
		
		boolean valid = false;
		for (Topic.CommInfrastructure comm: Topic.CommInfrastructure.values()) {
			if (comm.name().equals(busType)) {
				valid = true;
			}
		}
		
		if (!valid)
			throw new IllegalArgumentException
				("Invalid Communication Infrastructure: " + busType);
		
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		

		return this.deliver(Topic.CommInfrastructure.valueOf(busType), 
				            topic, event);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(Topic.CommInfrastructure busType, 
			               String topic, Object event) 
		throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null)
			throw new IllegalArgumentException("Invalid Event");
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		/* Try to send through the controller, this is the
		 * preferred way, since it may want to apply additional
		 * processing
		 */
		try {
			DroolsController droolsController = 
					EventProtocolCoder.manager.getDroolsController(topic, event);
			PolicyController controller = PolicyController.factory.get(droolsController);
			if (controller != null)
				return controller.deliver(busType, topic, event);
		} catch (Exception e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
					          busType + ":" + topic + " :" + event, this.toString());
			/* continue (try without routing through the controller) */
		}
		
		/*
		 * cannot route through the controller, send directly through
		 * the topic sink
		 */
		try {			
			String json = EventProtocolCoder.manager.encode(topic, event);
			return this.deliver(busType, topic, json);

		} catch (Exception e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			          busType + ":" + topic + " :" + event, this.toString());
			throw e;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean deliver(Topic.CommInfrastructure busType, 
			               String topic, String event) 
		throws IllegalArgumentException, IllegalStateException,
		       UnsupportedOperationException {
		
		if (topic == null || topic.isEmpty())
			throw new IllegalArgumentException("Invalid Topic");
		
		if (event == null || event.isEmpty())
			throw new IllegalArgumentException("Invalid Event");
		
		if (!this.isAlive())
			throw new IllegalStateException("Policy Engine is stopped");
		
		if (this.isLocked())
			throw new IllegalStateException("Policy Engine is locked");
		
		try {
			TopicSink sink = 
					TopicEndpoint.manager.getTopicSink
						(busType, topic);
			
			if (sink == null)
				throw new IllegalStateException("Inconsistent State: " + this);
			
			return sink.send(event);

		} catch (Exception e) {
			logger.warn(MessageCodes.EXCEPTION_ERROR, e, 
			          busType + ":" + topic + " :" + event, this.toString());
			throw e;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void activate() {

		// activate 'policy-management'
		for (PolicyController policyController : getPolicyControllers()) {
			try {
				policyController.unlock();
				policyController.start();
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine.activate: cannot start " + 
		                     policyController + " because of " + e.getMessage());
			} catch (LinkageError e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine.activate: cannot start " + 
			                 policyController + " because of " + e.getMessage());
			}
		}
		
		this.unlock();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void deactivate() {
		
		this.lock();
		
		for (PolicyController policyController : getPolicyControllers()) {
			try { 
				policyController.stop();
			} catch (Exception e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine.deactivate: cannot stop " + 
		                     policyController + " because of " + e.getMessage());
			} catch (LinkageError e) {
				logger.error(MessageCodes.EXCEPTION_ERROR, e, "PolicyEngine.deactivate: cannot start " + 
			                 policyController + " because of " + e.getMessage());
			}
		}	  
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("PolicyEngineManager [alive=").append(alive).append(", locked=").append(locked).append("]");
		return builder.toString();
	}
	
}

