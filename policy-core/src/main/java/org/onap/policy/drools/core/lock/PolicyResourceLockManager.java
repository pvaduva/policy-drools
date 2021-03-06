/*
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.core.lock;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureApi.OperResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager of resource locks. Checks for API implementers.
 */
public class PolicyResourceLockManager extends SimpleLockManager {

    private static Logger logger = LoggerFactory.getLogger(PolicyResourceLockManager.class);

    /**
     * Used by junit tests.
     */
    protected PolicyResourceLockManager() {
        super();
    }

    /**
     * Get instance.
     * 
     * @return the manager singleton
     */
    public static PolicyResourceLockManager getInstance() {
        return Singleton.instance;
    }

    @Override
    public boolean lock(String resourceId, String owner, int holdSec) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }


        return doBoolIntercept(impl -> impl.beforeLock(resourceId, owner, holdSec), () -> {

            // implementer didn't do the work - defer to the superclass
            boolean locked = super.lock(resourceId, owner, holdSec);

            doIntercept(false, impl -> impl.afterLock(resourceId, owner, locked));

            return locked;
        });
    }

    @Override
    public boolean refresh(String resourceId, String owner, int holdSec) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }


        return doBoolIntercept(impl -> impl.beforeRefresh(resourceId, owner, holdSec), () -> {

            // implementer didn't do the work - defer to the superclass
            boolean refreshed = super.refresh(resourceId, owner, holdSec);

            doIntercept(false, impl -> impl.afterRefresh(resourceId, owner, refreshed));

            return refreshed;
        });
    }

    @Override
    public boolean unlock(String resourceId, String owner) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }


        return doBoolIntercept(impl -> impl.beforeUnlock(resourceId, owner), () -> {

            // implementer didn't do the work - defer to the superclass
            boolean unlocked = super.unlock(resourceId, owner);

            doIntercept(false, impl -> impl.afterUnlock(resourceId, owner, unlocked));

            return unlocked;
        });
    }

    /**
     * Is locked.
     * 
     * @throws IllegalArgumentException if the resourceId is {@code null}
     */
    @Override
    public boolean isLocked(String resourceId) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }


        return doBoolIntercept(impl -> impl.beforeIsLocked(resourceId), () -> 

           // implementer didn't do the work - defer to the superclass
           super.isLocked(resourceId)
        );
    }

    /**
     *  Is locked by.
     *  
     * @throws IllegalArgumentException if the resourceId or owner is {@code null}
     */
    @Override
    public boolean isLockedBy(String resourceId, String owner) {
        if (resourceId == null) {
            throw makeNullArgException(MSG_NULL_RESOURCE_ID);
        }

        if (owner == null) {
            throw makeNullArgException(MSG_NULL_OWNER);
        }

        return doBoolIntercept(impl -> impl.beforeIsLockedBy(resourceId, owner), () -> 

            // implementer didn't do the work - defer to the superclass
            super.isLockedBy(resourceId, owner)
        );
    }

    /**
     * Applies a function to each implementer of the lock feature. Returns as soon as one
     * of them returns a result other than <b>OPER_UNHANDLED</b>. If they all return
     * <b>OPER_UNHANDLED</b>, then it returns the result of applying the default function.
     * 
     * @param interceptFunc intercept function
     * @param defaultFunc default function
     * @return {@code true} if success, {@code false} otherwise
     */
    private boolean doBoolIntercept(Function<PolicyResourceLockFeatureApi, OperResult> interceptFunc,
                    Supplier<Boolean> defaultFunc) {

        OperResult result = doIntercept(OperResult.OPER_UNHANDLED, interceptFunc);
        if (result != OperResult.OPER_UNHANDLED) {
            return (result == OperResult.OPER_ACCEPTED);
        }

        return defaultFunc.get();
    }

    /**
     * Applies a function to each implementer of the lock feature. Returns as soon as one
     * of them returns a non-null value.
     * 
     * @param continueValue if the implementer returns this value, then it continues to
     *        check addition implementers
     * @param func function to be applied to the implementers
     * @return first non-null value returned by an implementer, <i>continueValue</i> if
     *       they all returned <i>continueValue</i>
     */
    private <T> T doIntercept(T continueValue, Function<PolicyResourceLockFeatureApi, T> func) {

        for (PolicyResourceLockFeatureApi impl : getImplementers()) {
            try {
                T result = func.apply(impl);
                if (result != continueValue) {
                    return result;
                }

            } catch (RuntimeException e) {
                logger.warn("lock feature {} threw an exception", impl, e);
            }
        }

        return continueValue;
    }

    // these may be overridden by junit tests

    /**
     * Get implementers.
     * 
     * @return the list of feature implementers
     */
    protected List<PolicyResourceLockFeatureApi> getImplementers() {
        return PolicyResourceLockFeatureApi.impl.getList();
    }

    /**
     * Initialization-on-demand holder idiom.
     */
    private static class Singleton {

        private static final PolicyResourceLockManager instance = new PolicyResourceLockManager();
        
        /**
         * Not invoked.
         */
        private Singleton() {
            super();
        }
    }
}
