/*
 * ============LICENSE_START=======================================================
 * policy-management
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.onap.policy.common.capabilities.Lockable;
import org.onap.policy.common.capabilities.Startable;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.Topic.CommInfrastructure;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactory;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.common.endpoints.properties.PolicyEndPointProperties;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.common.gson.annotation.GsonJsonProperty;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.core.PolicyContainer;
import org.onap.policy.drools.core.jmx.PdpJmxListener;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.protocol.coders.EventProtocolCoder;
import org.onap.policy.drools.protocol.configuration.ControllerConfiguration;
import org.onap.policy.drools.protocol.configuration.PdpdConfiguration;
import org.onap.policy.drools.server.restful.RestManager;
import org.onap.policy.drools.server.restful.aaf.AafTelemetryAuthFilter;
import org.onap.policy.drools.utils.PropertyUtil;
import org.onap.policy.drools.utils.logging.LoggerUtil;
import org.onap.policy.drools.utils.logging.MDCTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Policy Engine, the top abstraction for the Drools PDP Policy Engine. It abstracts away a Drools
 * PDP Engine from management purposes. This is the best place to looking at the code from a top
 * down approach. Other managed entities can be obtained from the PolicyEngine, hierarchically. <br>
 * PolicyEngine 1 --- * PolicyController 1 --- 1 DroolsController 1 --- 1 PolicyContainer 1 --- *
 * PolicySession <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * UebTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * DmaapTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1
 * DmaapTopicWriter <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicReader 1 --- 1
 * RestTopicReader <br> PolicyEngine 1 --- 1 TopicEndpointManager 1 -- * TopicWriter 1 --- 1
 * RestTopicWriter <br> PolicyEngine 1 --- 1 ManagementServer
 */
public interface PolicyEngine extends Startable, Lockable, TopicListener {
    /**
     * Policy Engine Manager.
     */
    PolicyEngine manager = new PolicyEngineManager();

    /**
     * Default Telemetry Server Port.
     */
    int TELEMETRY_SERVER_DEFAULT_PORT = 9696;

    /**
     * Default Telemetry Server Hostname.
     */
    String TELEMETRY_SERVER_DEFAULT_HOST = "localhost";

    /**
     * Default Telemetry Server Name.
     */
    String TELEMETRY_SERVER_DEFAULT_NAME = "TELEMETRY";

    /**
     * Boot the engine.
     *
     * @param cliArgs command line arguments
     */
    void boot(String[] cliArgs);

    /**
     * configure the policy engine according to the given properties.
     *
     * @param properties Policy Engine properties
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     */
    void configure(Properties properties);

    /**
     * updates the Policy Engine with the given configuration.
     *
     * @param configuration the configuration
     * @return success or failure
     * @throws IllegalArgumentException if invalid argument provided
     * @throws IllegalStateException    if the system is in an invalid state
     */
    boolean configure(PdpdConfiguration configuration);

    /**
     * configure the engine's environment. General lab installation configuration is made available
     * to the Engine. Typically, custom lab installation that may be needed by arbitrary drools
     * applications are made available, for example network component and database host addresses.
     * Multiple environments can be passed in and tracked by the engine.
     *
     * @param properties an environment properties
     */
    void setEnvironment(Properties properties);

    /**
     * gets the engine's environment.
     *
     * @return properties object
     */
    Properties getEnvironment();

    /**
     * gets an environment's value, by 1) first from the engine's environment, and 2) from the OS
     * environment.
     *
     * @param key environment key
     * @return environment value or null if absent
     */
    String getEnvironmentProperty(String key);

    /**
     * sets an engine's environment property.
     *
     * @param key key
     * @param value value
     * @return property string
     */
    String setEnvironmentProperty(String key, String value);

    /**
     * registers a new Policy Controller with the Policy Engine initialized per properties.
     *
     * @param properties properties to initialize the Policy Controller
     * @return the newly instantiated Policy Controller
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException    when the engine is in a state where this operation is not
     *                                  permitted.
     */
    PolicyController createPolicyController(String name, Properties properties);

    /**
     * updates a set of Policy Controllers with configuration information.
     *
     * @param configuration list of configurations
     * @return list of controllers
     * @throws IllegalArgumentException exception
     * @throws IllegalStateException exception
     */
    List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configuration);

    /**
     * updates an already existing Policy Controller with configuration information.
     *
     * @param configuration configuration
     * @return the updated Policy Controller
     * @throws IllegalArgumentException in the configuration is invalid
     * @throws IllegalStateException    if the controller is in a bad state
     * @throws Exception                any other reason
     */
    PolicyController updatePolicyController(ControllerConfiguration configuration);

    /**
     * removes the Policy Controller identified by its name from the Policy Engine.
     *
     * @param name name of the Policy Controller
     */
    void removePolicyController(String name);

    /**
     * removes a Policy Controller from the Policy Engine.
     *
     * @param controller the Policy Controller to remove from the Policy Engine
     */
    void removePolicyController(PolicyController controller);

    /**
     * returns a list of the available Policy Controllers.
     *
     * @return list of Policy Controllers
     */
    List<PolicyController> getPolicyControllers();


    /**
     * get policy controller names.
     *
     * @return list of controller names
     */
    List<String> getPolicyControllerIds();

    /**
     * get unmanaged sources.
     *
     * @return unmanaged sources
     */
    List<TopicSource> getSources();

    /**
     * get unmanaged sinks.
     *
     * @return unmanaged sinks
     */
    List<TopicSink> getSinks();

    /**
     * get unmmanaged http servers list.
     *
     * @return http servers
     */
    List<HttpServletServer> getHttpServers();

    /**
     * get properties configuration.
     *
     * @return properties objects
     */
    Properties getProperties();

    /**
     * get features attached to the Policy Engine.
     *
     * @return list of features
     */
    List<PolicyEngineFeatureAPI> getFeatureProviders();

    /**
     * get named feature attached to the Policy Engine.
     *
     * @return the feature
     */
    PolicyEngineFeatureAPI getFeatureProvider(String featureName);

    /**
     * get features attached to the Policy Engine.
     *
     * @return list of features
     */
    List<String> getFeatures();

    /**
     * Attempts the dispatching of an "event" object.
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException when invalid or insufficient properties are provided
     * @throws IllegalStateException    when the engine is in a state where this operation is not
     *                                  permitted (ie. locked or stopped).
     */
    boolean deliver(String topic, Object event);

    /**
     * Attempts the dispatching of an "event" object over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(String busType, String topic, Object event);

    /**
     * Attempts the dispatching of an "event" object over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(CommInfrastructure busType, String topic, Object event);

    /**
     * Attempts delivering of an String over communication infrastructure "busType".
     *
     * @param topic topic
     * @param event the event object to send
     * @return true if successful, false if a failure has occurred.
     * @throws IllegalArgumentException      when invalid or insufficient properties are provided
     * @throws IllegalStateException         when the engine is in a state where this operation is not
     *                                       permitted (ie. locked or stopped).
     * @throws UnsupportedOperationException when the engine cannot deliver due to the functionality
     *                                       missing (ie. communication infrastructure not supported.
     */
    boolean deliver(CommInfrastructure busType, String topic, String event);

    /**
     * Invoked when the host goes into the active state.
     */
    void activate();

    /**
     * Invoked when the host goes into the standby state.
     */
    void deactivate();

    /**
     * produces a default telemetry configuration.
     *
     * @return policy engine configuration
     */
    Properties defaultTelemetryConfig();
}


/**
 * Policy Engine Manager Implementation.
 */
class PolicyEngineManager implements PolicyEngine {

    /**
     * String literals.
     */
    private static final String INVALID_TOPIC_MSG = "Invalid Topic";
    private static final String INVALID_EVENT_MSG = "Invalid Event";

    private static final String ENGINE_STOPPED_MSG = "Policy Engine is stopped";
    private static final String ENGINE_LOCKED_MSG = "Policy Engine is locked";

    /**
     * logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(PolicyEngineManager.class);

    /**
     * Is the Policy Engine running.
     */
    private volatile boolean alive = false;

    /**
     * Is the engine locked.
     */
    private volatile boolean locked = false;

    /**
     * Properties used to initialize the engine.
     */
    private Properties properties;

    /**
     * Environment Properties.
     */
    private final Properties environment = new Properties();

    /**
     * Policy Engine Sources.
     */
    private List<? extends TopicSource> sources = new ArrayList<>();

    /**
     * Policy Engine Sinks.
     */
    private List<? extends TopicSink> sinks = new ArrayList<>();

    /**
     * Policy Engine HTTP Servers.
     */
    private List<HttpServletServer> httpServers = new ArrayList<>();

    /**
     * gson parser to decode configuration requests.
     */
    private final Gson decoder = new GsonBuilder().disableHtmlEscaping().create();


    @Override
    public synchronized void boot(String[] cliArgs) {

        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeBoot(this, cliArgs)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-boot failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        try {
            globalInitContainer(cliArgs);
        } catch (final Exception e) {
            logger.error("{}: cannot init policy-container because of {}", this, e.getMessage(), e);
        }

        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterBoot(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-boot failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    @Override
    public synchronized void setEnvironment(Properties properties) {
        this.environment.putAll(PropertyUtil.getInterpolatedProperties(properties));
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public synchronized Properties getEnvironment() {
        return this.environment;
    }

    @Override
    public synchronized String getEnvironmentProperty(String envKey) {
        String value = this.environment.getProperty(envKey);
        if (value == null) {
            value = System.getProperty(envKey);
            if (value == null) {
                value = System.getenv(envKey);
            }
        }
        return value;
    }

    @Override
    public synchronized String setEnvironmentProperty(String envKey, String envValue) {
        return (String) this.environment.setProperty(envKey, envValue);
    }

    @Override
    public final Properties defaultTelemetryConfig() {
        final Properties defaultConfig = new Properties();

        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES, "TELEMETRY");
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_HTTP_HOST_SUFFIX, TELEMETRY_SERVER_DEFAULT_HOST);
        defaultConfig.put(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_PORT_SUFFIX,
                "" + Integer.toString(TELEMETRY_SERVER_DEFAULT_PORT));
        defaultConfig.put(
                PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                        + PolicyEndPointProperties.PROPERTY_HTTP_REST_PACKAGES_SUFFIX,
                RestManager.class.getPackage().getName());
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_HTTP_SWAGGER_SUFFIX, "" + Boolean.TRUE);
        defaultConfig.put(PolicyEndPointProperties.PROPERTY_HTTP_SERVER_SERVICES + "." + TELEMETRY_SERVER_DEFAULT_NAME
                + PolicyEndPointProperties.PROPERTY_MANAGED_SUFFIX, "" + Boolean.FALSE);

        return defaultConfig;
    }

    @Override
    public synchronized void configure(Properties properties) {

        if (properties == null) {
            logger.warn("No properties provided");
            throw new IllegalArgumentException("No properties provided");
        }

        /* policy-engine dispatch pre configure hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeConfigure(this, properties)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-configure failure because of {}", this,
                        feature.getClass().getName(), e.getMessage(), e);
            }
        }

        this.properties = properties;

        try {
            this.sources = getTopicEndpointManager().addTopicSources(properties);
            for (final TopicSource source : this.sources) {
                source.register(this);
            }
        } catch (final Exception e) {
            logger.error("{}: add-sources failed", this, e);
        }

        try {
            this.sinks = getTopicEndpointManager().addTopicSinks(properties);
        } catch (final IllegalArgumentException e) {
            logger.error("{}: add-sinks failed", this, e);
        }

        try {
            this.httpServers = getServletFactory().build(properties);
            for (HttpServletServer server : this.httpServers) {
                if (server.isAaf()) {
                    server.addFilterClass(null, AafTelemetryAuthFilter.class.getName());
                }
            }
        } catch (final IllegalArgumentException e) {
            logger.error("{}: add-http-servers failed", this, e);
        }

        /* policy-engine dispatch post configure hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterConfigure(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-configure failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean configure(PdpdConfiguration config) {

        if (config == null) {
            throw new IllegalArgumentException("No configuration provided");
        }

        final String entity = config.getEntity();

        MDCTransaction mdcTrans = MDCTransaction.newTransaction(config.getRequestID(), "brmsgw");
        if (this.getSources().size() == 1) {
            Topic topic = this.getSources().get(0);
            mdcTrans.setServiceName(topic.getTopic()).setRemoteHost(topic.getServers().toString())
                    .setTargetEntity(config.getEntity());
        }

        switch (entity) {
            case PdpdConfiguration.CONFIG_ENTITY_CONTROLLER:
                boolean success = controllerConfig(config);
                mdcTrans.resetSubTransaction().setStatusCode(success).transaction();
                return success;
            default:
                final String msg = "Configuration Entity is not supported: " + entity;
                mdcTrans.resetSubTransaction().setStatusCode(false).setResponseDescription(msg).flush();
                logger.warn(LoggerUtil.TRANSACTION_LOG_MARKER_NAME, msg);
                throw new IllegalArgumentException(msg);
        }
    }

    @Override
    public synchronized PolicyController createPolicyController(String name, Properties properties) {

        String tempName = name;
        // check if a PROPERTY_CONTROLLER_NAME property is present
        // if so, override the given name

        final String propertyControllerName = properties.getProperty(DroolsProperties.PROPERTY_CONTROLLER_NAME);
        if (propertyControllerName != null && !propertyControllerName.isEmpty()) {
            if (!propertyControllerName.equals(tempName)) {
                throw new IllegalStateException("Proposed name (" + tempName + ") and properties name ("
                        + propertyControllerName + ") don't match");
            }
            tempName = propertyControllerName;
        }

        PolicyController controller;
        for (final PolicyControllerFeatureAPI controllerFeature : getControllerProviders()) {
            try {
                controller = controllerFeature.beforeCreate(tempName, properties);
                if (controller != null) {
                    return controller;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-controller-create failure because of {}", this,
                        controllerFeature.getClass().getName(), e.getMessage(), e);
            }
        }

        controller = getControllerFactory().build(tempName, properties);
        if (this.isLocked()) {
            controller.lock();
        }

        // feature hook
        for (final PolicyControllerFeatureAPI controllerFeature : getControllerProviders()) {
            try {
                if (controllerFeature.afterCreate(controller)) {
                    return controller;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-controller-create failure because of {}", this,
                        controllerFeature.getClass().getName(), e.getMessage(), e);
            }
        }

        return controller;
    }


    @Override
    public List<PolicyController> updatePolicyControllers(List<ControllerConfiguration> configControllers) {

        final List<PolicyController> policyControllers = new ArrayList<>();
        if (configControllers == null || configControllers.isEmpty()) {
            logger.info("No controller configuration provided: {}", configControllers);
            return policyControllers;
        }

        for (final ControllerConfiguration configController : configControllers) {
            MDCTransaction mdcTrans = MDCTransaction.newSubTransaction(null).setTargetEntity(configController.getName())
                    .setTargetServiceName(configController.getOperation())
                    .setTargetVirtualEntity("" + configController.getDrools());
            try {
                final PolicyController policyController = this.updatePolicyController(configController);
                policyControllers.add(policyController);
                mdcTrans.setStatusCode(true).transaction();
            } catch (final Exception e) {
                mdcTrans.setStatusCode(false).setResponseCode(e.getClass().getName())
                        .setResponseDescription(e.getMessage()).flush();
                logger.error(LoggerUtil.TRANSACTION_LOG_MARKER_NAME,
                        "{}: cannot update-policy-controllers because of {}", this, e.getMessage(), e);
            }
        }

        return policyControllers;
    }

    @Override
    public synchronized PolicyController updatePolicyController(ControllerConfiguration configController) {

        if (configController == null) {
            throw new IllegalArgumentException("No controller configuration has been provided");
        }

        final String controllerName = configController.getName();
        if (controllerName == null || controllerName.isEmpty()) {
            logger.warn("controller-name  must be provided");
            throw new IllegalArgumentException("No controller configuration has been provided");
        }

        PolicyController policyController = null;
        try {
            final String operation = configController.getOperation();
            if (operation == null || operation.isEmpty()) {
                logger.warn("operation must be provided");
                throw new IllegalArgumentException("operation must be provided");
            }

            try {
                policyController = getControllerFactory().get(controllerName);
            } catch (final IllegalArgumentException e) {
                // not found
                logger.warn("Policy Controller " + controllerName + " not found", e);
            }

            if (policyController == null) {

                if (operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK)
                        || operation.equalsIgnoreCase(ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK)) {
                    throw new IllegalArgumentException(controllerName + " is not available for operation " + operation);
                }

                /* Recovery case */

                logger.warn("controller {} does not exist. Attempting recovery from disk", controllerName);

                final Properties controllerProperties =
                        getPersistenceManager().getControllerProperties(controllerName);

                /*
                 * returned properties cannot be null (per implementation) assert (properties !=
                 * null)
                 */

                if (controllerProperties == null) {
                    throw new IllegalArgumentException(controllerName + " is invalid");
                }

                logger.warn("controller being recovered. {} Reset controller's bad maven coordinates to brainless",
                        controllerName);

                /*
                 * try to bring up bad controller in brainless mode, after having it working, apply
                 * the new create/update operation.
                 */
                controllerProperties.setProperty(DroolsProperties.RULES_GROUPID, DroolsController.NO_GROUP_ID);
                controllerProperties.setProperty(DroolsProperties.RULES_ARTIFACTID, DroolsController.NO_ARTIFACT_ID);
                controllerProperties.setProperty(DroolsProperties.RULES_VERSION, DroolsController.NO_VERSION);

                policyController = getPolicyEngine().createPolicyController(controllerName, controllerProperties);

                /* fall through to do brain update operation */
            }

            switch (operation) {
                case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_CREATE:
                    getControllerFactory().patch(policyController, configController.getDrools());
                    break;
                case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UPDATE:
                    policyController.unlock();
                    getControllerFactory().patch(policyController, configController.getDrools());
                    break;
                case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_LOCK:
                    policyController.lock();
                    break;
                case ControllerConfiguration.CONFIG_CONTROLLER_OPERATION_UNLOCK:
                    policyController.unlock();
                    break;
                default:
                    final String msg = "Controller Operation Configuration is not supported: " + operation + " for "
                            + controllerName;
                    logger.warn(msg);
                    throw new IllegalArgumentException(msg);
            }

            return policyController;
        } catch (final Exception e) {
            logger.error("{}: cannot update-policy-controller because of {}", this, e.getMessage(), e);
            throw e;
        } catch (final LinkageError e) {
            logger.error("{}: cannot update-policy-controllers (rules) because of {}", this, e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized boolean start() {

        /* policy-engine dispatch pre start hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeStart(this)) {
                    return true;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-start failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        boolean success = true;
        if (this.locked) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        this.alive = true;

        /* Start Policy Engine exclusively-owned (unmanaged) http servers */

        for (final HttpServletServer httpServer : this.httpServers) {
            try {
                if (!httpServer.waitedStart(10 * 1000L)) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start http-server {} because of {}", this, httpServer, e.getMessage(), e);
            }
        }

        /* Start Policy Engine exclusively-owned (unmanaged) sources */

        for (final TopicSource source : this.sources) {
            try {
                if (!source.start()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start topic-source {} because of {}", this, source, e.getMessage(), e);
            }
        }

        /* Start Policy Engine owned (unmanaged) sinks */

        for (final TopicSink sink : this.sinks) {
            try {
                if (!sink.start()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start topic-sink {} because of {}", this, sink, e.getMessage(), e);
            }
        }

        /* Start Policy Controllers */

        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                if (!controller.start()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start policy-controller {} because of {}", this, controller, e.getMessage(),
                        e);
                success = false;
            }
        }

        /* Start managed Topic Endpoints */

        try {
            if (!getTopicEndpointManager().start()) {
                success = false;
            }
        } catch (final IllegalStateException e) {
            logger.warn("{}: Topic Endpoint Manager is in an invalid state because of {}", this, e.getMessage(), e);
        }


        // Start the JMX listener

        startPdpJmxListener();

        /* policy-engine dispatch after start hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterStart(this)) {
                    return success;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-start failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    @Override
    public synchronized boolean stop() {

        /* policy-engine dispatch pre stop hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeStop(this)) {
                    return true;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-stop failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        /* stop regardless of the lock state */

        boolean success = true;
        if (!this.alive) {
            return true;
        }

        this.alive = false;

        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                if (!controller.stop()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot stop policy-controller {} because of {}", this, controller, e.getMessage(), e);
                success = false;
            }
        }

        /* Stop Policy Engine owned (unmanaged) sources */
        for (final TopicSource source : this.sources) {
            try {
                if (!source.stop()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start topic-source {} because of {}", this, source, e.getMessage(), e);
            }
        }

        /* Stop Policy Engine owned (unmanaged) sinks */
        for (final TopicSink sink : this.sinks) {
            try {
                if (!sink.stop()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start topic-sink {} because of {}", this, sink, e.getMessage(), e);
            }
        }

        /* stop all managed topics sources and sinks */
        if (!getTopicEndpointManager().stop()) {
            success = false;
        }

        /* stop all unmanaged http servers */
        for (final HttpServletServer httpServer : this.httpServers) {
            try {
                if (!httpServer.stop()) {
                    success = false;
                }
            } catch (final Exception e) {
                logger.error("{}: cannot start http-server {} because of {}", this, httpServer, e.getMessage(), e);
            }
        }

        // stop JMX?

        /* policy-engine dispatch pre stop hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterStop(this)) {
                    return success;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-stop failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    @Override
    public synchronized void shutdown() {

        /*
         * shutdown activity even when underlying subcomponents (features, controllers, topics, etc
         * ..) are stuck
         */

        Thread exitThread = makeShutdownThread();
        exitThread.start();

        /* policy-engine dispatch pre shutdown hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeShutdown(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-shutdown failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        this.alive = false;

        /* Shutdown Policy Engine owned (unmanaged) sources */
        for (final TopicSource source : this.sources) {
            try {
                source.shutdown();
            } catch (final Exception e) {
                logger.error("{}: cannot shutdown topic-source {} because of {}", this, source, e.getMessage(), e);
            }
        }

        /* Shutdown Policy Engine owned (unmanaged) sinks */
        for (final TopicSink sink : this.sinks) {
            try {
                sink.shutdown();
            } catch (final Exception e) {
                logger.error("{}: cannot shutdown topic-sink {} because of {}", this, sink, e.getMessage(), e);
            }
        }

        /* Shutdown managed resources */
        getControllerFactory().shutdown();
        getTopicEndpointManager().shutdown();
        getServletFactory().destroy();

        // Stop the JMX listener

        stopPdpJmxListener();

        /* policy-engine dispatch post shutdown hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterShutdown(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-shutdown failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        exitThread.interrupt();
        logger.info("{}: normal termination", this);
    }

    /**
     * Thread that shuts down http servers.
     */
    protected class ShutdownThread extends Thread {
        private static final long SHUTDOWN_MAX_GRACE_TIME = 30000L;

        @Override
        public void run() {
            try {
                doSleep(SHUTDOWN_MAX_GRACE_TIME);
                logger.warn("{}: abnormal termination - shutdown graceful time period expiration",
                        PolicyEngineManager.this);
            } catch (final InterruptedException e) {
                /* courtesy to shutdown() to allow it to return */
                synchronized (PolicyEngineManager.this) {
                }
                logger.info("{}: finishing a graceful shutdown ", PolicyEngineManager.this, e);
            } finally {
                /*
                 * shut down the Policy Engine owned http servers as the very last thing
                 */
                for (final HttpServletServer httpServer : PolicyEngineManager.this.getHttpServers()) {
                    try {
                        httpServer.shutdown();
                    } catch (final Exception e) {
                        logger.error("{}: cannot shutdown http-server {} because of {}", PolicyEngineManager.this,
                                httpServer, e.getMessage(), e);
                    }
                }

                logger.info("{}: exit", PolicyEngineManager.this);
                doExit(0);
            }
        }

        // these may be overridden by junit tests

        protected void doSleep(long sleepMs) throws InterruptedException {
            Thread.sleep(sleepMs);
        }

        protected void doExit(int code) {
            System.exit(code);
        }
    }

    @Override
    public boolean isAlive() {
        return this.alive;
    }

    @Override
    public synchronized boolean lock() {

        /* policy-engine dispatch pre lock hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeLock(this)) {
                    return true;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-lock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        if (this.locked) {
            return true;
        }

        this.locked = true;

        boolean success = true;
        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                success = controller.lock() && success;
            } catch (final Exception e) {
                logger.error("{}: cannot lock policy-controller {} because of {}", this, controller, e.getMessage(), e);
                success = false;
            }
        }

        success = getTopicEndpointManager().lock() && success;

        /* policy-engine dispatch post lock hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterLock(this)) {
                    return success;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-lock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    @Override
    public synchronized boolean unlock() {

        /* policy-engine dispatch pre unlock hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeUnlock(this)) {
                    return true;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-unlock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        if (!this.locked) {
            return true;
        }

        this.locked = false;

        boolean success = true;
        final List<PolicyController> controllers = getControllerFactory().inventory();
        for (final PolicyController controller : controllers) {
            try {
                success = controller.unlock() && success;
            } catch (final Exception e) {
                logger.error("{}: cannot unlock policy-controller {} because of {}", this, controller, e.getMessage(),
                        e);
                success = false;
            }
        }

        success = getTopicEndpointManager().unlock() && success;

        /* policy-engine dispatch after unlock hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterUnlock(this)) {
                    return success;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-unlock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    @Override
    public void removePolicyController(String name) {
        getControllerFactory().destroy(name);
    }

    @Override
    public void removePolicyController(PolicyController controller) {
        getControllerFactory().destroy(controller);
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public List<PolicyController> getPolicyControllers() {
        return getControllerFactory().inventory();
    }

    @JsonProperty("controllers")
    @GsonJsonProperty("controllers")
    @Override
    public List<String> getPolicyControllerIds() {
        final List<String> controllerNames = new ArrayList<>();
        for (final PolicyController controller : getControllerFactory().inventory()) {
            controllerNames.add(controller.getName());
        }
        return controllerNames;
    }

    @Override
    @JsonIgnore
    @GsonJsonIgnore
    public Properties getProperties() {
        return this.properties;
    }


    @SuppressWarnings("unchecked")
    @Override
    public List<TopicSource> getSources() {
        return (List<TopicSource>) this.sources;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<TopicSink> getSinks() {
        return (List<TopicSink>) this.sinks;
    }

    @Override
    public List<HttpServletServer> getHttpServers() {
        return this.httpServers;
    }

    @Override
    public List<String> getFeatures() {
        final List<String> features = new ArrayList<>();
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            features.add(feature.getName());
        }
        return features;
    }

    @JsonIgnore
    @GsonJsonIgnore
    @Override
    public List<PolicyEngineFeatureAPI> getFeatureProviders() {
        return getEngineProviders();
    }

    @Override
    public PolicyEngineFeatureAPI getFeatureProvider(String featureName) {
        if (featureName == null || featureName.isEmpty()) {
            throw new IllegalArgumentException("A feature name must be provided");
        }

        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            if (feature.getName().equals(featureName)) {
                return feature;
            }
        }

        throw new IllegalArgumentException("Invalid Feature Name: " + featureName);
    }

    @Override
    public void onTopicEvent(CommInfrastructure commType, String topic, String event) {
        /* policy-engine pre topic event hook */
        for (final PolicyEngineFeatureAPI feature : getFeatureProviders()) {
            try {
                if (feature.beforeOnTopicEvent(this, commType, topic, event)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} beforeOnTopicEvent failure on event {} because of {}", this,
                        feature.getClass().getName(), event, e.getMessage(), e);
            }
        }

        /* configuration request */
        PdpdConfiguration configuration = null;
        try {
            configuration = this.decoder.fromJson(event, PdpdConfiguration.class);
            this.configure(configuration);
        } catch (final Exception e) {
            logger.error("{}: configuration-error due to {} because of {}", this, event, e.getMessage(), e);
        }

        /* policy-engine after topic event hook */
        for (final PolicyEngineFeatureAPI feature : getFeatureProviders()) {
            try {
                if (feature.afterOnTopicEvent(this, configuration, commType, topic, event)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} afterOnTopicEvent failure on event {} because of {}", this,
                        feature.getClass().getName(), event, e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean deliver(String topic, Object event) {

        /*
         * Note this entry point is usually from the DRL
         */

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        final List<? extends TopicSink> topicSinks = getTopicEndpointManager().getTopicSinks(topic);
        if (topicSinks == null || topicSinks.size() != 1) {
            throw new IllegalStateException("Cannot ensure correct delivery on topic " + topic + ": " + topicSinks);
        }

        return this.deliver(topicSinks.get(0).getTopicCommInfrastructure(), topic, event);
    }

    @Override
    public boolean deliver(String busType, String topic, Object event) {

        /*
         * Note this entry point is usually from the DRL (one of the reasons busType is String.
         */

        if (busType == null || busType.isEmpty()) {
            throw new IllegalArgumentException("Invalid Communication Infrastructure");
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        boolean valid = false;
        for (final Topic.CommInfrastructure comm : Topic.CommInfrastructure.values()) {
            if (comm.name().equals(busType)) {
                valid = true;
            }
        }

        if (!valid) {
            throw new IllegalArgumentException("Invalid Communication Infrastructure: " + busType);
        }


        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }


        return this.deliver(Topic.CommInfrastructure.valueOf(busType), topic, event);
    }

    @Override
    public boolean deliver(Topic.CommInfrastructure busType, String topic, Object event) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        /*
         * Try to send through the controller, this is the preferred way, since it may want to apply
         * additional processing
         */
        try {
            final DroolsController droolsController = getProtocolCoder().getDroolsController(topic, event);
            final PolicyController controller = getControllerFactory().get(droolsController);
            if (controller != null) {
                return controller.deliver(busType, topic, event);
            }
        } catch (final Exception e) {
            logger.warn("{}: cannot find policy-controller to deliver {} over {}:{} because of {}", this, event,
                    busType, topic, e.getMessage(), e);

            /* continue (try without routing through the controller) */
        }

        /*
         * cannot route through the controller, send directly through the topic sink
         */
        try {
            final String json = getProtocolCoder().encode(topic, event);
            return this.deliver(busType, topic, json);

        } catch (final Exception e) {
            logger.warn("{}: cannot deliver {} over {}:{} because of {}", this, event, busType, topic, e.getMessage(),
                    e);
            throw e;
        }
    }

    @Override
    public boolean deliver(Topic.CommInfrastructure busType, String topic, String event) {

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException(INVALID_TOPIC_MSG);
        }

        if (event == null || event.isEmpty()) {
            throw new IllegalArgumentException(INVALID_EVENT_MSG);
        }

        if (!this.isAlive()) {
            throw new IllegalStateException(ENGINE_STOPPED_MSG);
        }

        if (this.isLocked()) {
            throw new IllegalStateException(ENGINE_LOCKED_MSG);
        }

        try {
            final TopicSink sink = getTopicEndpointManager().getTopicSink(busType, topic);

            if (sink == null) {
                throw new IllegalStateException("Inconsistent State: " + this);
            }

            return sink.send(event);

        } catch (final Exception e) {
            logger.warn("{}: cannot deliver {} over {}:{} because of {}", this, event, busType, topic, e.getMessage(),
                    e);
            throw e;
        }
    }

    @Override
    public synchronized void activate() {

        /* policy-engine dispatch pre activate hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeActivate(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-activate failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        // activate 'policy-management'
        for (final PolicyController policyController : this.getPolicyControllers()) {
            try {
                policyController.unlock();
                policyController.start();
            } catch (final Exception e) {
                logger.error("{}: cannot activate of policy-controller {} because of {}", this, policyController,
                        e.getMessage(), e);
            } catch (final LinkageError e) {
                logger.error("{}: cannot activate (rules compilation) of policy-controller {} because of {}", this,
                        policyController, e.getMessage(), e);
            }
        }

        this.unlock();

        /* policy-engine dispatch post activate hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterActivate(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-activate failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    @Override
    public synchronized void deactivate() {

        /* policy-engine dispatch pre deactivate hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.beforeDeactivate(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} before-deactivate failure because of {}", this,
                        feature.getClass().getName(), e.getMessage(), e);
            }
        }

        this.lock();

        for (final PolicyController policyController : this.getPolicyControllers()) {
            try {
                policyController.stop();
            } catch (final Exception | LinkageError e) {
                logger.error("{}: cannot deactivate (stop) policy-controller {} because of {}", this, policyController,
                        e.getMessage(), e);
            }
        }

        /* policy-engine dispatch post deactivate hook */
        for (final PolicyEngineFeatureAPI feature : getEngineProviders()) {
            try {
                if (feature.afterDeactivate(this)) {
                    return;
                }
            } catch (final Exception e) {
                logger.error("{}: feature {} after-deactivate failure because of {}", this,
                        feature.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    private boolean controllerConfig(PdpdConfiguration config) {
        /* only this one supported for now */
        final List<ControllerConfiguration> configControllers = config.getControllers();
        if (configControllers == null || configControllers.isEmpty()) {
            logger.info("No controller configuration provided: {}", config);
            return false;
        }

        final List<PolicyController> policyControllers = this.updatePolicyControllers(config.getControllers());
        boolean success = false;
        if (!(policyControllers == null || policyControllers.isEmpty())
                && (policyControllers.size() == configControllers.size())) {
            success = true;
        }
        return success;
    }

    @Override
    public String toString() {
        return "PolicyEngineManager [alive=" + this.alive + ", locked=" + this.locked + "]";
    }

    // these methods may be overridden by junit tests

    protected List<PolicyEngineFeatureAPI> getEngineProviders() {
        return PolicyEngineFeatureAPI.providers.getList();
    }

    protected List<PolicyControllerFeatureAPI> getControllerProviders() {
        return PolicyControllerFeatureAPI.providers.getList();
    }

    protected void globalInitContainer(String[] cliArgs) {
        PolicyContainer.globalInit(cliArgs);
    }

    protected TopicEndpoint getTopicEndpointManager() {
        return TopicEndpointManager.getManager();
    }

    protected HttpServletServerFactory getServletFactory() {
        return HttpServletServerFactoryInstance.getServerFactory();
    }

    protected PolicyControllerFactory getControllerFactory() {
        return PolicyController.factory;
    }

    protected void startPdpJmxListener() {
        PdpJmxListener.start();
    }

    protected void stopPdpJmxListener() {
        PdpJmxListener.stop();
    }

    protected Thread makeShutdownThread() {
        return new ShutdownThread();
    }

    protected EventProtocolCoder getProtocolCoder() {
        return EventProtocolCoder.manager;
    }

    protected SystemPersistence getPersistenceManager() {
        return SystemPersistence.manager;
    }

    protected PolicyEngine getPolicyEngine() {
        return PolicyEngine.manager;
    }
}


