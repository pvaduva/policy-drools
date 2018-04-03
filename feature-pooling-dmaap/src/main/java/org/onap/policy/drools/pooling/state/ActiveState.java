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

package org.onap.policy.drools.pooling.state;

import java.util.Arrays;
import java.util.TreeSet;
import org.onap.policy.drools.pooling.PoolingManager;
import org.onap.policy.drools.pooling.message.Heartbeat;
import org.onap.policy.drools.pooling.message.Offline;
import org.onap.policy.drools.pooling.message.Query;

/**
 * The active state. In this state, this host has one more more bucket
 * assignments and processes any events associated with one of its buckets.
 * Other events are forwarded to appropriate target hosts.
 */
public class ActiveState extends ProcessingState {

    /**
     * Set of hosts that have been assigned a bucket.
     */
    private final TreeSet<String> assigned = new TreeSet<>();

    /**
     * Host that comes after this host, or {@code null} if it has no successor.
     */
    private String succHost = null;

    /**
     * Host that comes before this host, or "" if it has no predecessor.
     */
    private String predHost = "";

    /**
     * {@code True} if we saw this host's heart beat since the last check,
     * {@code false} otherwise.
     */
    private boolean myHeartbeatSeen = false;

    /**
     * {@code True} if we saw the predecessor's heart beat since the last check,
     * {@code false} otherwise.
     */
    private boolean predHeartbeatSeen = false;

    /**
     * 
     * @param mgr
     */
    public ActiveState(PoolingManager mgr) {
        super(mgr, mgr.getAssignments().getLeader());

        assigned.addAll(Arrays.asList(mgr.getAssignments().getHostArray()));

        detmNeighbors();
    }

    /**
     * Determine this host's neighbors based on the order of the host UUIDs.
     * Updates {@link #succHost} and {@link #predHost}.
     */
    private void detmNeighbors() {
        if (assigned.size() < 2) {
            /*
             * this host is the only one with any assignments - it has no
             * neighbors
             */
            succHost = null;
            predHost = "";
            return;
        }

        if ((succHost = assigned.higher(getHost())) == null) {
            // wrapped around - successor is the first host in the set
            succHost = assigned.first();
        }

        if ((predHost = assigned.lower(getHost())) == null) {
            // wrapped around - predecessor is the last host in the set
            predHost = assigned.last();
        }
    }

    @Override
    public void start() {
        addTimers();
        genHeartbeat();
    }

    /**
     * Adds the timers.
     */
    private void addTimers() {

        /*
         * heart beat generator
         */
        long genMs = getProperties().getActiveHeartbeatMs();

        scheduleWithFixedDelay(genMs, genMs, xxx -> {
            genHeartbeat();
            return null;
        });

        /*
         * my heart beat checker
         */
        long interMs = getProperties().getInterHeartbeatMs();

        scheduleWithFixedDelay(interMs, interMs, xxx -> {
            if (myHeartbeatSeen) {
                myHeartbeatSeen = false;
                return null;
            }

            // missed my heart beat

            return internalTopicFailed();
        });

        /*
         * predecessor heart beat checker
         */
        if (!predHost.isEmpty()) {

            scheduleWithFixedDelay(interMs, interMs, xxx -> {
                if (predHeartbeatSeen) {
                    predHeartbeatSeen = false;
                    return null;
                }

                // missed the predecessor's heart beat
                publish(makeQuery());

                return goQuery();
            });
        }
    }

    /**
     * Generates a heart beat for this host and its successor.
     */
    private void genHeartbeat() {
        Heartbeat msg = makeHeartbeat(System.currentTimeMillis());
        publish(getHost(), msg);

        if (succHost != null) {
            publish(succHost, msg);
        }
    }

    @Override
    public State process(Heartbeat msg) {
        String src = msg.getSource();

        if (src == null) {
            return null;

        } else if (src.equals(getHost())) {
            myHeartbeatSeen = true;

        } else if (src.equals(predHost)) {
            predHeartbeatSeen = true;

        }

        return null;
    }

    @Override
    public State process(Offline msg) {
        String src = msg.getSource();

        if (src == null) {
            return null;

        } else if (!assigned.contains(src)) {
            /*
             * the offline host wasn't assigned any buckets, so just ignore the
             * message
             */
            return null;

        } else if (isLeader() || (predHost.equals(src) && predHost.equals(assigned.first()))) {
            /*
             * Case 1: We are the leader.
             * 
             * Case 2: Our predecessor was the leader and it has gone offline -
             * we should become the leader.
             * 
             * In either case, we are now the leader and we must re-balance the
             * buckets since one of the hosts has gone offline.
             */

            assigned.remove(src);

            return becomeLeader(assigned);

        } else {
            /*
             * Otherwise, we don't care right now - we'll wait for the leader to
             * tell us it's been removed.
             */
            return null;
        }
    }

    /**
     * Transitions to the query state.
     */
    @Override
    public State process(Query msg) {
        State next = super.process(msg);
        if (next != null) {
            return next;
        }

        return goQuery();
    }

    protected String getSuccHost() {
        return succHost;
    }

    protected String getPredHost() {
        return predHost;
    }

    protected boolean isMyHeartbeatSeen() {
        return myHeartbeatSeen;
    }

    protected boolean isPredHeartbeatSeen() {
        return predHeartbeatSeen;
    }

}
