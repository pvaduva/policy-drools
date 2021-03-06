/*
 * ============LICENSE_START=======================================================
 * feature-distributed-locking
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.distributed.locking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.onap.policy.common.utils.properties.exception.PropertyException;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi;
import org.onap.policy.drools.features.PolicyEngineFeatureAPI;
import org.onap.policy.drools.persistence.SystemPersistence;
import org.onap.policy.drools.system.PolicyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLockingFeature implements PolicyEngineFeatureAPI, PolicyResourceLockFeatureApi {

    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockingFeature.class);

    /**
     * Properties Configuration Name.
     */
    public static final String CONFIGURATION_PROPERTIES_NAME = "feature-distributed-locking";

    /**
     * Properties for locking feature.
     */
    private DistributedLockingProperties lockProps;

    /**
     * Data source used to connect to the DB containing locks.
     */
    private BasicDataSource dataSource;

    /**
     * UUID. 
     */
    private static final UUID uuid = UUID.randomUUID();

    @Override
    public int getSequenceNumber() {
        return 1000;
    }

    @Override
    public OperResult beforeLock(String resourceId, String owner, int holdSec) {

        TargetLock lock = new TargetLock(resourceId, uuid, owner, dataSource);

        return (lock.lock(holdSec) ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
    }

    @Override
    public OperResult beforeRefresh(String resourceId, String owner, int holdSec) {

        TargetLock lock = new TargetLock(resourceId, uuid, owner, dataSource);

        return (lock.refresh(holdSec) ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);                
    }

    @Override
    public OperResult beforeUnlock(String resourceId, String owner) {
        TargetLock lock = new TargetLock(resourceId, uuid, owner, dataSource);

        return (lock.unlock() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
    }

    @Override
    public OperResult beforeIsLockedBy(String resourceId, String owner) {
        TargetLock lock = new TargetLock(resourceId, uuid, owner, dataSource);

        return (lock.isActive() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
    }

    @Override
    public OperResult beforeIsLocked(String resourceId) {
        TargetLock lock = new TargetLock(resourceId, uuid, "dummyOwner", dataSource);

        return (lock.isLocked() ? OperResult.OPER_ACCEPTED : OperResult.OPER_DENIED);
    }

    @Override
    public boolean afterStart(PolicyEngine engine) {

        try {
            this.lockProps = new DistributedLockingProperties(
                    SystemPersistence.manager.getProperties(DistributedLockingFeature.CONFIGURATION_PROPERTIES_NAME));
            this.dataSource = makeDataSource();
        } catch (PropertyException e) {
            logger.error("DistributedLockingFeature feature properies have not been loaded", e);
            throw new DistributedLockingFeatureException(e);
        } catch (InterruptedException e) {
            logger.error("DistributedLockingFeature failed to create data source", e);
            Thread.currentThread().interrupt();
            throw new DistributedLockingFeatureException(e);
        } catch (Exception e) {
            logger.error("DistributedLockingFeature failed to create data source", e);
            throw new DistributedLockingFeatureException(e);
        }

        cleanLockTable();

        return false;
    }

    /**
     * Make data source.
     * 
     * @return a new, pooled data source
     * @throws Exception exception
     */
    protected BasicDataSource makeDataSource() throws Exception {
        Properties props = new Properties();
        props.put("driverClassName", lockProps.getDbDriver());
        props.put("url", lockProps.getDbUrl());
        props.put("username", lockProps.getDbUser());
        props.put("password", lockProps.getDbPwd());
        props.put("testOnBorrow", "true");
        props.put("poolPreparedStatements", "true");

        // additional properties are listed in the GenericObjectPool API

        return BasicDataSourceFactory.createDataSource(props);
    }

    /**
     * This method kills the heartbeat thread and calls refreshLockTable which removes
     * any records from the db where the current host is the owner.
     */
    @Override
    public boolean beforeShutdown(PolicyEngine engine) {
        cleanLockTable();
        return false;
    }

    /**
     * This method removes all records owned by the current host from the db.
     */
    private void cleanLockTable() {

        try (Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(
                        "DELETE FROM pooling.locks WHERE host = ? OR expirationTime < now()")
                ) {

            statement.setString(1, uuid.toString());
            statement.executeUpdate();

        } catch (SQLException e) {
            logger.error("error in refreshLockTable()", e);
        }

    }
}
