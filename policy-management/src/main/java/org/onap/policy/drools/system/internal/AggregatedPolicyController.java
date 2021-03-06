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

package org.onap.policy.drools.system.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.onap.policy.common.endpoints.event.comm.Topic;
import org.onap.policy.common.endpoints.event.comm.TopicEndpoint;
import org.onap.policy.common.endpoints.event.comm.TopicEndpointManager;
import org.onap.policy.common.endpoints.event.comm.TopicListener;
import org.onap.policy.common.endpoints.event.comm.TopicSink;
import org.onap.policy.common.endpoints.event.comm.TopicSource;
import org.onap.policy.common.gson.annotation.GsonJsonIgnore;
import org.onap.policy.drools.controller.DroolsController;
import org.onap.policy.drools.controller.DroolsControllerFactory;
import org.onap.policy.drools.features.PolicyControllerFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.properties.DroolsProperties;
import org.onap.policy.drools.protocol.configuration.DroolsConfiguration;
import org.onap.policy.drools.system.PolicyController;
import org.onap.policy.models.tosca.authorative.concepts.ToscaPolicyTypeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This implementation of the Policy Controller merely aggregates and tracks for management purposes
 * all underlying resources that this controller depends upon.
 */
public class AggregatedPolicyController implements PolicyController, TopicListener {

    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(AggregatedPolicyController.class);

    /**
     * identifier for this policy controller.
     */
    private final String name;

    /**
     * Abstracted Event Sources List regardless communication technology.
     */
    private final List<? extends TopicSource> sources;

    /**
     * Abstracted Event Sinks List regardless communication technology.
     */
    private final List<? extends TopicSink> sinks;

    /**
     * Mapping topics to sinks.
     */
    @JsonIgnore
    @GsonJsonIgnore
    private final HashMap<String, TopicSink> topic2Sinks = new HashMap<>();

    /**
     * Is this Policy Controller running (alive) ? reflects invocation of start()/stop() only.
     */
    private volatile boolean alive;

    /**
     * Is this Policy Controller locked ? reflects if i/o controller related operations and start
     * are permitted, more specifically: start(), deliver() and onTopicEvent(). It does not affect
     * the ability to stop the underlying drools infrastructure
     */
    private volatile boolean locked;

    /**
     * Policy Drools Controller.
     */
    private volatile DroolsController droolsController;

    /**
     * Properties used to initialize controller.
     */
    private final Properties properties;

    /**
     * Policy Types.
     */
    private List<ToscaPolicyTypeIdentifier> policyTypes;

    /**
     * Constructor version mainly used for bootstrapping at initialization time a policy engine
     * controller.
     *
     * @param name controller name
     * @param properties
     *
     * @throws IllegalArgumentException when invalid arguments are provided
     */
    public AggregatedPolicyController(String name, Properties properties) {

        this.name = name;

        /*
         * 1. Register read topics with network infrastructure (ueb, dmaap, rest) 2. Register write
         * topics with network infrastructure (ueb, dmaap, rest) 3. Register with drools
         * infrastructure
         */

        // Create/Reuse Readers/Writers for all event sources endpoints

        this.sources = getEndpointManager().addTopicSources(properties);
        this.sinks = getEndpointManager().addTopicSinks(properties);

        initDrools(properties);
        initSinks();

        /* persist new properties */
        getPersistenceManager().storeController(name, properties);
        this.properties = properties;

        this.policyTypes = getPolicyTypesFromProperties();
    }

    @Override
    public List<ToscaPolicyTypeIdentifier> getPolicyTypes() {
        if (!policyTypes.isEmpty()) {
            return policyTypes;
        }

        return droolsController
                .getBaseDomainNames()
                .stream()
                .map(d -> new ToscaPolicyTypeIdentifier(d, DroolsProperties.DEFAULT_CONTROLLER_POLICY_TYPE_VERSION))
                .collect(Collectors.toList());
    }

    protected List<ToscaPolicyTypeIdentifier> getPolicyTypesFromProperties() {
        List<ToscaPolicyTypeIdentifier> policyTypeIds = new ArrayList<>();

        String ptiPropValue = properties.getProperty(DroolsProperties.PROPERTY_CONTROLLER_POLICY_TYPES);
        if (ptiPropValue == null) {
            return policyTypeIds;
        }

        List<String> ptiPropList = new ArrayList<>(Arrays.asList(ptiPropValue.split("\\s*,\\s*")));
        for (String pti : ptiPropList) {
            String[] ptv = pti.split(":");
            if (ptv.length == 1) {
                policyTypeIds.add(new ToscaPolicyTypeIdentifier(ptv[0],
                    DroolsProperties.DEFAULT_CONTROLLER_POLICY_TYPE_VERSION));
            } else if (ptv.length == 2) {
                policyTypeIds.add(new ToscaPolicyTypeIdentifier(ptv[0], ptv[1]));
            }
        }

        return policyTypeIds;
    }

    /**
     * initialize drools layer.
     *
     * @throws IllegalArgumentException if invalid parameters are passed in
     */
    private void initDrools(Properties properties) {
        try {
            // Register with drools infrastructure
            this.droolsController = getDroolsFactory().build(properties, sources, sinks);
        } catch (Exception | LinkageError e) {
            logger.error("{}: cannot init-drools because of {}", this, e.getMessage(), e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * initialize sinks.
     *
     * @throws IllegalArgumentException if invalid parameters are passed in
     */
    private void initSinks() {
        this.topic2Sinks.clear();
        for (TopicSink sink : sinks) {
            this.topic2Sinks.put(sink.getTopic(), sink);
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean updateDrools(DroolsConfiguration newDroolsConfiguration) {

        DroolsConfiguration oldDroolsConfiguration = new DroolsConfiguration(this.droolsController.getArtifactId(),
                this.droolsController.getGroupId(), this.droolsController.getVersion());

        if (oldDroolsConfiguration.getGroupId().equalsIgnoreCase(newDroolsConfiguration.getGroupId())
                && oldDroolsConfiguration.getArtifactId().equalsIgnoreCase(newDroolsConfiguration.getArtifactId())
                && oldDroolsConfiguration.getVersion().equalsIgnoreCase(newDroolsConfiguration.getVersion())) {
            logger.warn("{}: cannot update-drools: identical configuration {} vs {}", this, oldDroolsConfiguration,
                    newDroolsConfiguration);
            return true;
        }

        try {
            /* Drools Controller created, update initialization properties for restarts */

            this.properties.setProperty(DroolsProperties.RULES_GROUPID, newDroolsConfiguration.getGroupId());
            this.properties.setProperty(DroolsProperties.RULES_ARTIFACTID, newDroolsConfiguration.getArtifactId());
            this.properties.setProperty(DroolsProperties.RULES_VERSION, newDroolsConfiguration.getVersion());

            getPersistenceManager().storeController(name, this.properties);

            this.initDrools(this.properties);

            /* set drools controller to current locked status */

            if (this.isLocked()) {
                this.droolsController.lock();
            } else {
                this.droolsController.unlock();
            }

            /* set drools controller to current alive status */

            if (this.isAlive()) {
                this.droolsController.start();
            } else {
                this.droolsController.stop();
            }

        } catch (IllegalArgumentException e) {
            logger.error("{}: cannot update-drools because of {}", this, e.getMessage(), e);
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean start() {
        logger.info("{}: start", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeStart(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-start failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        if (this.isLocked()) {
            throw new IllegalStateException("Policy Controller " + name + " is locked");
        }

        synchronized (this) {
            if (this.alive) {
                return true;
            }

            this.alive = true;
        }

        final boolean success = this.droolsController.start();

        // register for events

        for (TopicSource source : sources) {
            source.register(this);
        }

        for (TopicSink sink : sinks) {
            try {
                sink.start();
            } catch (Exception e) {
                logger.error("{}: cannot start {} because of {}", this, sink, e.getMessage(), e);
            }
        }

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterStart(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-start failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean stop() {
        logger.info("{}: stop", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeStop(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-stop failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        /* stop regardless locked state */

        synchronized (this) {
            if (!this.alive) {
                return true;
            }

            this.alive = false;
        }

        // 1. Stop registration

        for (TopicSource source : sources) {
            source.unregister(this);
        }

        boolean success = this.droolsController.stop();

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterStop(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-stop failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void shutdown() {
        logger.info("{}: shutdown", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeShutdown(this)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-shutdown failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        this.stop();

        getDroolsFactory().shutdown(this.droolsController);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterShutdown(this)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-shutdown failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void halt() {
        logger.info("{}: halt", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeHalt(this)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-halt failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        this.stop();
        getDroolsFactory().destroy(this.droolsController);
        getPersistenceManager().deleteController(this.name);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterHalt(this)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-halt failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void onTopicEvent(Topic.CommInfrastructure commType, String topic, String event) {
        logger.debug("{}: raw event offered from {}:{}: {}", this, commType, topic, event);

        if (skipOffer()) {
            return;
        }

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeOffer(this, commType, topic, event)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-offer failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        boolean success = this.droolsController.offer(topic, event);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterOffer(this, commType, topic, event, success)) {
                    return;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-offer failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }
    }

    @Override
    public <T> boolean offer(T event) {
        logger.debug("{}: event offered: {}", this, event);

        if (skipOffer()) {
            return true;
        }

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeOffer(this, event)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-offer failure because of {}", this, feature.getClass().getName(),
                    e.getMessage(), e);
            }
        }

        boolean success = this.droolsController.offer(event);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterOffer(this, event, success)) {
                    return success;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-offer failure because of {}", this, feature.getClass().getName(),
                    e.getMessage(), e);
            }
        }

        return success;
    }

    private boolean skipOffer() {
        return isLocked() || !isAlive();
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean deliver(Topic.CommInfrastructure commType, String topic, Object event) {

        logger.debug("{}: deliver event to {}:{}: {}", this, commType, topic, event);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeDeliver(this, commType, topic, event)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-deliver failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Invalid Topic");
        }

        if (event == null) {
            throw new IllegalArgumentException("Invalid Event");
        }

        if (!this.isAlive()) {
            throw new IllegalStateException("Policy Engine is stopped");
        }

        if (this.isLocked()) {
            throw new IllegalStateException("Policy Engine is locked");
        }

        if (!this.topic2Sinks.containsKey(topic)) {
            logger.warn("{}: cannot deliver event because the sink {}:{} is not registered: {}", this, commType, topic,
                    event);
            throw new IllegalArgumentException("Unsupported topic " + topic + " for delivery");
        }

        boolean success = this.droolsController.deliver(this.topic2Sinks.get(topic), event);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterDeliver(this, commType, topic, event, success)) {
                    return success;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-deliver failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isAlive() {
        return this.alive;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean lock() {
        logger.info("{}: lock", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeLock(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-lock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        synchronized (this) {
            if (this.locked) {
                return true;
            }

            this.locked = true;
        }

        // it does not affect associated sources/sinks, they are
        // autonomous entities

        boolean success = this.droolsController.lock();

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterLock(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-lock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean unlock() {

        logger.info("{}: unlock", this);

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.beforeUnlock(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} before-unlock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        synchronized (this) {
            if (!this.locked) {
                return true;
            }

            this.locked = false;
        }

        boolean success = this.droolsController.unlock();

        for (PolicyControllerFeatureAPI feature : getProviders()) {
            try {
                if (feature.afterUnlock(this)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("{}: feature {} after-unlock failure because of {}", this, feature.getClass().getName(),
                        e.getMessage(), e);
            }
        }

        return success;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public boolean isLocked() {
        return this.locked;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<? extends TopicSource> getTopicSources() {
        return this.sources;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public List<? extends TopicSink> getTopicSinks() {
        return this.sinks;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public DroolsController getDrools() {
        return this.droolsController;
    }


    /**
     * {@inheritDoc}.
     */
    @Override
    @JsonIgnore
    @GsonJsonIgnore
    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public String toString() {
        return "AggregatedPolicyController [name=" + name + ", alive=" + alive
                + ", locked=" + locked + ", droolsController=" + droolsController + "]";
    }

    // the following methods may be overridden by junit tests

    protected SystemPersistence getPersistenceManager() {
        return SystemPersistence.manager;
    }

    protected TopicEndpoint getEndpointManager() {
        return TopicEndpointManager.getManager();
    }

    protected DroolsControllerFactory getDroolsFactory() {
        return DroolsController.factory;
    }

    protected List<PolicyControllerFeatureAPI> getProviders() {
        return PolicyControllerFeatureAPI.providers.getList();
    }
}

