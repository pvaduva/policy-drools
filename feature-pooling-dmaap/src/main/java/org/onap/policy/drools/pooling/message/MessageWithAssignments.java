/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.message;

import org.onap.policy.drools.pooling.PoolingFeatureException;

/**
 * A Message that includes bucket assignments.
 */
public class MessageWithAssignments extends Message {

    /**
     * Bucket assignments, as known by the source host.
     */
    private BucketAssignments assignments;

    /**
     * Constructor.
     */
    public MessageWithAssignments() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param source host on which the message originated
     * @param assignments assignements
     */
    public MessageWithAssignments(String source, BucketAssignments assignments) {
        super(source);

        this.assignments = assignments;

    }

    public BucketAssignments getAssignments() {
        return assignments;
    }

    public void setAssignments(BucketAssignments assignments) {
        this.assignments = assignments;
    }

    /**
     * If there are any assignments, it verifies there validity.
     */
    @Override
    public void checkValidity() throws PoolingFeatureException {

        super.checkValidity();

        if (assignments != null) {
            assignments.checkValidity();
        }
    }

}
